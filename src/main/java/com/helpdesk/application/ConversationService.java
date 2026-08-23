package com.helpdesk.application;

import com.helpdesk.domain.engine.ConversationSnapshot;
import com.helpdesk.domain.engine.EngineResult;
import com.helpdesk.domain.engine.LlmPort;
import com.helpdesk.domain.engine.LlmStepDecision;
import com.helpdesk.domain.engine.OfflineInterpreter;
import com.helpdesk.domain.engine.ResponseComposer;
import com.helpdesk.domain.engine.SopExecutionEngine;
import com.helpdesk.domain.engine.StepOutcome;
import com.helpdesk.domain.engine.StepResult;
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
import com.helpdesk.web.dto.CaseDetail;
import com.helpdesk.web.dto.CaseSummary;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageDto;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.StepResultDto;
import com.helpdesk.web.exception.CaseNotFoundException;
import com.helpdesk.web.exception.ConversationClosedException;
import com.helpdesk.web.exception.ConversationNotFoundException;
import com.helpdesk.web.exception.NoSopFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Conversation use cases: create a conversation (triggers SOP retrieval scoped to
 * the hotel + starts execution), advance it with a user message + structured AI
 * step outcome, and read conversation/case state. Hotel-scoped (multi-tenant).
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
        String hotelId = req.hotelId();
        SopResponse retrieved = bestRetrieval(hotelId, req.problem());
        if (retrieved == null) {
            throw new NoSopFoundException(req.problem());
        }
        Sop sop = sopService.load(hotelId, retrieved.id());

        Conversation conv = new Conversation();
        conv.setHotelId(hotelId);
        conv.setSopId(sop.getCode());
        conv.setStatus(ConversationStatus.IN_PROGRESS);
        conv.setEmployee(req.employee());
        conv.setProblemSummary(req.problem());
        conv.setCurrentStepKey(sop.getSteps().get(0).getStepKey());
        conv.setLastUserMessage(req.problem());
        Conversation saved = conversationRepository.save(conv);

        appendMessage(saved, MessageRole.USER, MessageKind.PROBLEM, req.problem(), sop.getCode(), saved.getCurrentStepKey(), null, null);
        SopResponse.StepDto first = SopResponse.from(sop).steps().get(0);
        String assistantText = composer.compose(new EngineResult(first, first.stepKey(), ConversationStatus.IN_PROGRESS.name(), List.of(), false), SopResponse.from(sop));
        appendMessage(saved, MessageRole.ASSISTANT, MessageKind.QUESTION, assistantText, sop.getCode(), first.stepKey(), "TROUBLESHOOT", null);
        auditRepository.save(new AuditEvent(hotelId, saved.getId(), sop.getCode(), first.stepKey(), "STEP_SHOWN", assistantText));

        saved.setLastAssistantMessage(assistantText);
        saved.setLastIntent("TROUBLESHOOT");
        conversationRepository.save(saved);

        return ConversationResponse.from(saved, SopResponse.from(sop), messagesOf(saved.getId()));
    }

    @Transactional
    public ConversationResponse sendMessage(Long conversationId, MessageRequest req) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        if (conv.getStatus() == ConversationStatus.RESOLVED
                || conv.getStatus() == ConversationStatus.ESCALATED) {
            throw new ConversationClosedException(conversationId);
        }

        String hotelId = conv.getHotelId();
        Sop sop = sopService.load(hotelId, conv.getSopId());
        SopResponse sopDto = SopResponse.from(sop);
        SopResponse.StepDto current = currentStep(sopDto, conv.getCurrentStepKey());

        appendMessage(conv, MessageRole.USER, MessageKind.ANSWER, req.message(), sop.getCode(), conv.getCurrentStepKey(), null, null);
        conv.setLastUserMessage(req.message());

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
                String branchKey = decision.hasBranch() && branchExists(current, decision.branchKey())
                        ? decision.branchKey() : null;
                StepResult intent = StepResult.valueOf(
                        decision.intent() == null ? "CONTINUE" : decision.intent().toUpperCase());
                outcome = new StepOutcome(branchKey, intent, decision.stepResult(),
                        req.message(), decision.escalationReason());
                auditRepository.save(new AuditEvent(hotelId, conv.getId(), sop.getCode(), current.stepKey(),
                        "LLM_DECISION", "intent=" + intent + "; branch=" + branchKey
                        + (decision.response() != null ? "; modelReply=" + decision.response() : "")));
            } else {
                outcome = interpreter.interpret(req.message(), current);
            }
        } else {
            outcome = interpreter.interpret(req.message(), current);
        }

        EngineResult result = engine.advance(sopDto, current, outcome);

        String assistantText = composer.compose(result, sopDto);
        appendMessage(conv, MessageRole.ASSISTANT, kindFor(result), assistantText,
                sop.getCode(), result.currentStepKey(), "TROUBLESHOOT", outcome.stepResult().name());
        auditRepository.save(new AuditEvent(hotelId, conv.getId(), sop.getCode(), conv.getCurrentStepKey(),
                "STEP_RESULT", "stepResult=" + outcome.stepResult().name()
                        + (outcome.hasBranch() ? "; branch=" + outcome.branchKey() : "")));

        conv.setCurrentStepKey(result.currentStepKey());
        conv.setStatus(ConversationStatus.valueOf(result.status()));
        conv.setLastAssistantMessage(assistantText);
        conv.setLastIntent("TROUBLESHOOT");
        conv.setLastStepResult(outcome.stepResult().name());
        if (result.conversationOver() && "RESOLVED".equals(result.status())) {
            conv.setResolvedAt(Instant.now());
        }
        if (result.conversationOver() && "ESCALATED".equals(result.status())) {
            conv.setEscalatedAt(Instant.now());
        }
        conversationRepository.save(conv);

        upsertCase(conv, sop, result, outcome);

        return ConversationResponse.from(conv, sopDto, messagesOf(conv.getId()));
    }

    public ConversationResponse get(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        Sop sop = sopService.load(conv.getHotelId(), conv.getSopId());
        return ConversationResponse.from(conv, SopResponse.from(sop), messagesOf(conv.getId()));
    }

    public List<CaseSummary> listCases(String hotelId, String status) {
        List<SupportCase> cases;
        if (hotelId == null || hotelId.isBlank()) {
            cases = (status == null || status.isBlank())
                    ? caseRepository.findAllByOrderByStartedAtDesc()
                    : caseRepository.findByStatusOrderByStartedAtDesc(ConversationStatus.valueOf(status.toUpperCase()));
        } else {
            cases = (status == null || status.isBlank())
                    ? caseRepository.findByHotelIdOrderByStartedAtDesc(hotelId)
                    : caseRepository.findByHotelIdAndStatusOrderByStartedAtDesc(hotelId, ConversationStatus.valueOf(status.toUpperCase()));
        }
        return cases.stream().map(CaseSummary::from).toList();
    }

    public CaseDetail getCase(String reference) {
        SupportCase c = caseRepository.findByReference(reference);
        if (c == null) throw new CaseNotFoundException(reference);
        return CaseDetail.from(c);
    }

    // ---- internals ----

    private SopResponse bestRetrieval(String hotelId, String problem) {
        var res = sopService.retrieve(hotelId, problem);
        if (res.candidates().isEmpty()) return null;
        return SopResponse.from(sopService.loadById(res.candidates().get(0).getId()));
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

    private List<MessageDto> messagesOf(Long conversationId) {
        return messageRepository.findByConversationIdOrderBySeqAsc(conversationId).stream()
                .map(m -> new MessageDto(
                        m.getSeq(), m.getRole(), m.getKind(),
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
        sc.setHotelId(conv.getHotelId());
        sc.setEmployee(conv.getEmployee());
        sc.setProblem(conv.getProblemSummary());
        sc.setSopId(sop.getCode());
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
