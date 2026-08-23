package com.helpdesk.application;

import com.helpdesk.domain.engine.StepResult;
import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.repository.AuditEventRepository;
import com.helpdesk.domain.repository.ConversationMessageRepository;
import com.helpdesk.domain.repository.ConversationRepository;
import com.helpdesk.domain.repository.SupportCaseRepository;
import com.helpdesk.web.dto.CaseSummary;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.StepRequest;
import com.helpdesk.web.dto.StepResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end conversation + case + audit flow (Phase 1B). Provider-free: the
 * offline interpreter derives step outcomes; the deterministic engine routes.
 * Verifies the whole chain from problem → retrieval → execution → resolution /
 * escalation → case + audit persistence.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConversationFlowTest {

    private static final String HOTEL = "test-hotel";

    @Autowired SopService sopService;
    @Autowired ConversationService conversationService;
    @Autowired ConversationRepository conversationRepository;
    @Autowired ConversationMessageRepository messageRepository;
    @Autowired SupportCaseRepository caseRepository;
    @Autowired AuditEventRepository auditRepository;

    private com.helpdesk.web.dto.SopResponse buildSop(String id) {
        SopRequest req = new SopRequest(
                id, "Printer", "desc", "IT", "máy in không in được",
                List.of("máy in", "không in được", "kẹt giấy"),
                List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "Máy in có bật không?", SopStepType.QUESTION, "2",
                                false, null, List.of(
                                        new com.helpdesk.web.dto.BranchRequest("off", "tắt", "2"),
                                        new com.helpdesk.web.dto.BranchRequest("on", "bật", "2"))),
                        new StepRequest("2", 2, "Kiểm tra kẹt giấy", SopStepType.ACTION, "3",
                                false, null, List.of()),
                        new StepRequest("3", 3, "In thử", SopStepType.CHECK, null,
                                true, SopTerminalKind.RESOLVE, List.of())));
        return sopService.create(HOTEL, req);
    }

    @Test
    void happyPathResolvesAndCreatesResolvedCase() {
        buildSop("flow-printer");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "alice", "máy in không in được"));

        assertNotNull(conv.id());
        assertEquals("flow-printer", conv.sopId());
        assertEquals(ConversationStatus.IN_PROGRESS, conv.status());
        assertFalse(conv.messages().isEmpty());

        // Step 1: user says "bật" -> branch 'on' -> step 2
        ConversationResponse after1 = conversationService.sendMessage(conv.id(),
                new MessageRequest("máy in đang bật", "on", null));
        assertEquals("2", after1.currentStepKey());

        // Step 2: action done -> continue to step 3 (terminal RESOLVE) -> RESOLVED.
        ConversationResponse after2 = conversationService.sendMessage(after1.id(),
                new MessageRequest("đã kiểm tra xong", null,
                new StepResultDto(StepResult.CONTINUE, "flow-printer", "2", null, null)));
        assertEquals(ConversationStatus.RESOLVED, after2.status());
        assertNotNull(after2.resolvedAt());

        // Case created + resolved.
        List<CaseSummary> cases = conversationService.listCases(HOTEL, "RESOLVED");
        assertTrue(cases.stream().anyMatch(c -> c.conversationId().equals(conv.id())));

        // Audit trail present: STEP_SHOWN + STEP_RESULT events.
        assertTrue(auditRepository.findByConversationIdOrderByCreatedAtAsc(conv.id()).size() >= 2);
    }

    @Test
    void escalationPathCreatesEscalatedCaseWithFailedStep() {
        buildSop("flow-printer2");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "bob", "máy in không in được"));

        // User reports it still doesn't work -> escalate.
        ConversationResponse after = conversationService.sendMessage(conv.id(),
                new MessageRequest("vẫn không được, bỏ cuộc", null,
                        new StepResultDto(StepResult.ESCALATE, "flow-printer2", "1", null,
                                "đã thử bật và kiểm tra kẹt giấy nhưng không được")));
        assertEquals(ConversationStatus.ESCALATED, after.status());
        assertNotNull(after.escalatedAt());

        List<CaseSummary> cases = conversationService.listCases(HOTEL, "ESCALATED");
        CaseSummary c = cases.stream().filter(x -> x.conversationId().equals(conv.id())).findFirst().orElseThrow();
        assertEquals("1", c.failedStepKey());
        assertEquals("flow-printer2", c.sopId());
    }

    @Test
    void closedConversationRejectsFurtherMessages() {
        buildSop("flow-printer3");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "carol", "máy in không in được"));
        conversationService.sendMessage(conv.id(),
                new MessageRequest("vẫn không được, bỏ cuộc", null,
                        new StepResultDto(StepResult.ESCALATE, "flow-printer3", "1", null,
                                "đã thử nhưng không được")));

        assertThrows(com.helpdesk.web.ConversationClosedException.class, () ->
                conversationService.sendMessage(conv.id(),
                        new MessageRequest("thêm câu hỏi", null, null)));
    }

    @Test
    void messageAndConversationPersistAcrossLoads() {
        buildSop("flow-printer4");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "dave", "máy in không in được"));
        conversationService.sendMessage(conv.id(),
                new MessageRequest("bật rồi", null, null));

        // Reload from repository directly (new "session").
        var reloaded = conversationRepository.findById(conv.id()).orElseThrow();
        assertEquals(ConversationStatus.IN_PROGRESS, reloaded.getStatus());
        assertEquals("2", reloaded.getCurrentStepKey());
        assertTrue(messageRepository.findByConversationIdOrderBySeqAsc(conv.id()).size() >= 3);
    }

    @Test
    void noMatchingSopRejected() {
        assertThrows(com.helpdesk.web.NoSopFoundException.class, () ->
                conversationService.create(new ConversationRequest(HOTEL, "eve", "tàu hỏa trễ giờ")));
    }
}
