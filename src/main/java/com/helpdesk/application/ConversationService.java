package com.helpdesk.application;

import com.helpdesk.domain.engine.ConversationSnapshot;
import com.helpdesk.domain.engine.EngineResult;
import com.helpdesk.domain.engine.LlmPort;
import com.helpdesk.domain.engine.LlmStepDecision;
import com.helpdesk.domain.engine.OfflineInterpreter;
import com.helpdesk.domain.engine.ResponseComposer;
import com.helpdesk.domain.engine.SopExecutionEngine;
import com.helpdesk.domain.engine.StepOutcome;
import com.helpdesk.domain.model.AuditEvent;
import com.helpdesk.domain.model.Conversation;
import com.helpdesk.domain.model.ConversationMessage;
import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.model.MessageKind;
import com.helpdesk.domain.model.MessageRole;
import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SupportCase;
import com.helpdesk.domain.repository.AuditEventRepository;
import com.helpdesk.domain.repository.ConversationMessageRepository;
import com.helpdesk.domain.repository.ConversationRepository;
import com.helpdesk.domain.repository.SupportCaseRepository;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.StepResultDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Conversation use cases: create a conversation (triggers SOP retrieval +
 * starts execution), advance it with a user message + structured AI step
 * outcome, and read conversation/case state.
 *
 * <p>The service is deliberately provider-agnostic: the structured {@link StepOutcome}
 * is supplied by the caller (in Phase 1C the LLM produces it; offline, the
 * {@link OfflineInterpreter} does). The service NEVER lets the AI choose an
 * arbitrary step — routing is delegated to {@link SopExecutionEngine}, which is
 * the deterministic, auditable source of truth.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final SupportCaseRepository caseRepository;
    private final AuditEventRepository auditRepository;
    private final SopService sopService;
    private final SopExecutionEngine engine;
    private final LlmPort llmPort;
    private final OfflineInterpreter interpreter;
    private final ResponseComposer composer;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               SupportCaseRepository caseRepository,
                               AuditEventRepository auditRepository,
                               SopService sopService,
                               SopExecutionEngine engine,
                               LlmPort llmPort,
                               OfflineInterpreter interpreter,
                               ResponseComposer composer) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.caseRepository = caseRepository;
        this.auditRepository = auditRepository;
        this.sopService = sopService;
        this.engine = engine;
        this.llmPort = llmPort;
        this.interpreter = interpreter;
        this.composer = composer;
    }

    @Transactional
    public ConversationResponse create(ConversationRequest req) {
        SopResponse retrieved = bestRetrieval(req.problem());
        if (retrieved == null) {
            throw new com.helpdesk.web.NoSopFoundException(req.problem());
        }
        Sop sop = sopService.load(retrieved.id());

        Conversation conv = new Conversation();
        conv.setSopId(sop.getId());
        conv.setStatus(ConversationStatus.IN_PROGRESS);
        conv.setEmployee(req.employee());
        conv.setProblemSummary(req.problem());
        conv.setCurrentStepKey(sop.getSteps().get(0).getStepKey());
        conv.setLastUserMessage(req.problem());
        Conversation saved = conversationRepository.save(conv);

        // Initial employee message + assistant's first step.
        appendMessage(saved, MessageRole.USER, MessageKind.PROBLEM, req.problem(), retrieved.id(), saved.getCurrentStepKey(), null, null);
        SopResponse.StepDto first = SopResponse.from(sop).steps().get(0);
        String assistantText = composer.compose(new EngineResult(first, first.stepKey(), ConversationStatus.IN_PROGRESS.name(), List.of(), false), SopResponse.from(sop));
        appendMessage(saved, MessageRole.ASSISTANT, MessageKind.QUESTION, assistantText, retrieved.id(), first.stepKey(), "TROUBLESHOOT", null);
        auditRepository.save(new AuditEvent(saved.getId(), sop.getId(), first.stepKey(), "STEP_SHOWN", assistantText));

        saved.setLastAssistantMessage(assistantText);
        saved.setLastIntent("TROUBLESHOOT");
        conversationRepository.save(saved);

        return ConversationResponse.from(saved, SopResponse.from(sop), messagesOf(saved.getId()));
    }

    @Transactional
    public ConversationResponse sendMessage(Long conversationId, MessageRequest req) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new com.helpdesk.web.ConversationNotFoundException(conversationId));
        if (conv.getStatus() == ConversationStatus.RESOLVED
                || conv.getStatus() == ConversationStatus.ESCALATED) {
            throw new com.helpdesk.web.ConversationClosedException(conversationId);
        }

        Sop sop = sopService.load(conv.getSopId());
        SopResponse sopDto = SopResponse.from(sop);
        SopResponse.StepDto current = currentStep(sopDto, conv.getCurrentStepKey());

        // Record the employee's reply.
        appendMessage(conv, MessageRole.USER, MessageKind.ANSWER, req.message(), sop.getId(), conv.getCurrentStepKey(), null, null);
        conv.setLastUserMessage(req.message());

        // Decide the outcome. Priority: explicit structured result from the caller
        // (tests/curl) > LLM when configured > deterministic offline interpreter.
        StepOutcome outcome;
        if (req.stepResult() != null) {
            outcome = new StepOutcome(
                    req.branchKey(),
                    req.stepResult().result(),
                    req.stepResult().resolved(),
                    req.message(),
                    req.stepResult().escalationReason());
        } else if (llmPort.isConfigured()) {
            LlmStepDecision decision = llmPort.decide(snapshotFor(conv, sopDto, current), req.message());
            if (decision != null) {
                // Guardrail: only trust a branchKey that is enumerated on the current step.
                String branchKey = decision.hasBranch() && branchExists(current, decision.branchKey())
                        ? decision.branchKey() : null;
                String intent = decision.intent() == null ? "CONTINUE" : decision.intent().toUpperCase();
                outcome = new StepOutcome(branchKey, intent, decision.stepResult(),
                        req.message(), decision.escalationReason());
                auditRepository.save(new AuditEvent(conv.getId(), sop.getId(), current.stepKey(),
                        "LLM_DECISION", "intent=" + intent + "; branch=" + branchKey
                        + (decision.response() != null ? "; modelReply=" + decision.response() : "")));
            } else {
                outcome = interpreter.interpret(req.message(), current);
            }
        } else {
            outcome = interpreter.interpret(req.message(), current);
        }

        EngineResult result = engine.advance(sopDto, current, outcome);

        // Persist the assistant's response + record step result.
        String assistantText = composer.compose(result, sopDto);
        appendMessage(conv, MessageRole.ASSISTANT, kindFor(result), assistantText,
                sop.getId(), result.currentStepKey(), "TROUBLESHOOT", outcome.stepResult());
        auditRepository.save(new AuditEvent(conv.getId(), sop.getId(), conv.getCurrentStepKey(),
                "STEP_RESULT", "stepResult=" + outcome.stepResult()
                        + (outcome.hasBranch() ? "; branch=" + outcome.branchKey() : "")));

        // Update conversation state.
        conv.setCurrentStepKey(result.currentStepKey());
        conv.setStatus(ConversationStatus.valueOf(result.status()));
        conv.setLastAssistantMessage(assistantText);
        conv.setLastIntent("TROUBLESHOOT");
        conv.setLastStepResult(outcome.stepResult());
        if (result.conversationOver() && "RESOLVED".equals(result.status())) {
            conv.setResolvedAt(Instant.now());
        }
        if (result.conversationOver() && "ESCALATED".equals(result.status())) {
            conv.setEscalatedAt(Instant.now());
        }
        conversationRepository.save(conv);

        // Derive / update the support case.
        upsertCase(conv, sop, result, outcome);

        return ConversationResponse.from(conv, sopDto, messagesOf(conv.getId()));
    }

    public ConversationResponse get(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new com.helpdesk.web.ConversationNotFoundException(conversationId));
        Sop sop = sopService.load(conv.getSopId());
        return ConversationResponse.from(conv, SopResponse.from(sop), messagesOf(conv.getId()));
    }

    public List<com.helpdesk.web.dto.CaseSummary> listCases(String status) {
        List<SupportCase> cases;
        if (status == null || status.isBlank()) {
            cases = caseRepository.findAllByOrderByStartedAtDesc();
        } else {
            cases = caseRepository.findByStatusOrderByStartedAtDesc(ConversationStatus.valueOf(status.toUpperCase()));
        }
        return cases.stream().map(com.helpdesk.web.dto.CaseSummary::from).toList();
    }

    public com.helpdesk.web.dto.CaseDetail getCase(String reference) {
        SupportCase c = caseRepository.findByReference(reference);
        if (c == null) throw new com.helpdesk.web.CaseNotFoundException(reference);
        return com.helpdesk.web.dto.CaseDetail.from(c);
    }

    // ---- internals ----

    private SopResponse bestRetrieval(String problem) {
        var res = sopService.retrieve(problem);
        if (res.candidates().isEmpty()) return null;
        return SopResponse.from(sopService.load(res.candidates().get(0).getId()));
    }

    private SopResponse.StepDto currentStep(SopResponse sop, String key) {
        if (key == null) return null;
        return sop.steps().stream().filter(s -> s.stepKey().equals(key)).findFirst().orElse(null);
    }

    private boolean branchExists(SopResponse.StepDto step, String branchKey) {
        if (step == null || step.branches() == null || branchKey == null) return false;
        return step.branches().stream().anyMatch(b -> branchKey.equals(b.branchKey()));
    }

    private ConversationSnapshot snapshotFor(Conversation conv, SopResponse sopDto, SopResponse.StepDto current) {
        List<String> recent = messageRepository.findByConversationIdOrderBySeqAsc(conv.getId()).stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .toList();
        return new ConversationSnapshot(
                String.valueOf(conv.getId()), sopDto.id(), sopDto.title(), current, recent);
    }

    private MessageKind kindFor(EngineResult result) {
        if (result.conversationOver()) {
            return "RESOLVED".equals(result.status()) ? MessageKind.RESOLUTION : MessageKind.ESCALATION;
        }
        return MessageKind.QUESTION;
    }

    private void appendMessage(Conversation conv, MessageRole role, MessageKind kind,
                                String content, String sopId, String stepKey,
                                String intent, String stepResult) {
        int seq = messageRepository.maxSeq(conv.getId()) + 1;
        ConversationMessage m = new ConversationMessage(conv, seq, role, kind, content);
        m.setSopId(sopId);
        m.setStepKey(stepKey);
        m.setIntent(intent);
        m.setStepResult(stepResult);
        messageRepository.save(m);
    }

    private List<com.helpdesk.web.dto.MessageDto> messagesOf(Long conversationId) {
        return messageRepository.findByConversationIdOrderBySeqAsc(conversationId).stream()
                .map(m -> new com.helpdesk.web.dto.MessageDto(
                        m.getSeq(), m.getRole().name(), m.getKind().name(),
                        m.getContent(), m.getStepKey(), m.getCreatedAt()))
                .toList();
    }

    private void upsertCase(Conversation conv, Sop sop, EngineResult result, StepOutcome outcome) {
        SupportCase sc = caseRepository.findByConversationId(conv.getId());
        boolean isNew = sc == null;
        if (isNew) {
            sc = new SupportCase();
            sc.setConversationId(conv.getId());
            sc.setReference("CASE-" + String.format("%06d", conv.getId()));
            sc.setStartedAt(conv.getStartedAt());
        }
        sc.setEmployee(conv.getEmployee());
        sc.setProblem(conv.getProblemSummary());
        sc.setSopId(sop.getId());
        sc.setSopTitle(sop.getTitle());
        sc.setStatus(ConversationStatus.valueOf(result.status()));
        if ("ESCALATED".equals(result.status())) {
            sc.setFailedStepKey(result.currentStepKey());
            sc.setEscalationReason(outcome.escalationReason());
            sc.setEscalatedAt(conv.getEscalatedAt());
        }
        if ("RESOLVED".equals(result.status())) {
            sc.setResolvedAt(conv.getResolvedAt());
        }
        caseRepository.save(sc);
    }
}
