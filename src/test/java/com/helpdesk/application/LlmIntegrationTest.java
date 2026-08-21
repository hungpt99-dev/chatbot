package com.helpdesk.application;

import com.helpdesk.domain.engine.LlmPort;
import com.helpdesk.domain.engine.LlmStepDecision;
import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.StepRequest;
import com.helpdesk.web.dto.BranchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Phase 1C: the LLM integration boundary. A {@link LlmPort} mock stands in for the
 * real provider so the contract (off-mode fallback, LLM-driven resolve/escalate,
 * and the branch-key guardrail) is verified without a live API key.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LlmIntegrationTest {

    @MockBean LlmPort llmPort;
    @Autowired SopService sopService;
    @Autowired ConversationService conversationService;

    private SopResponse buildSop(String id) {
        SopRequest req = new SopRequest(
                id, "Printer", "desc", "IT", "máy in không in được",
                List.of("máy in", "không in được"),
                List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "Máy in có bật không?", SopStepType.QUESTION, "2",
                                false, null, List.of(
                                        new BranchRequest("off", "tắt", "2"),
                                        new BranchRequest("on", "bật", "2"))),
                        new StepRequest("2", 2, "Kiểm tra kẹt giấy", SopStepType.ACTION, "3",
                                false, null, List.of()),
                        new StepRequest("3", 3, "In thử", SopStepType.CHECK, null,
                                true, SopTerminalKind.RESOLVE, List.of())));
        return sopService.create(req);
    }

    @Test
    void offModeDegradesToOfflineInterpreterAndStillResolves() {
        // No LLM wired: the mock returns isConfigured()=false by default -> offline path.
        when(llmPort.isConfigured()).thenReturn(false);

        buildSop("llm-off-printer");
        ConversationResponse conv = conversationService.create(new ConversationRequest("alice", "máy in không in được"));
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("máy in đang bật", null, null));
        assertEquals("2", conv.currentStepKey());
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("đã kiểm tra, không kẹt giấy", null, null));
        // step 2 -> terminal RESOLVE step 3 closes the conversation.
        assertEquals("RESOLVED", conv.status());
    }

    @Test
    void llmDrivesResolutionAtTerminalStep() {
        when(llmPort.isConfigured()).thenReturn(true);
        // LLM resolves only when the current step is 2 (its defaultNext is the terminal
        // RESOLVE step 3); otherwise CONTINUE.
        when(llmPort.decide(any(), anyString())).thenAnswer(inv -> {
            var snap = inv.getArgument(0, com.helpdesk.domain.engine.ConversationSnapshot.class);
            boolean atStep2 = "2".equals(snap.currentStep().stepKey());
            return atStep2
                    ? new LlmStepDecision("RESOLVE", null, "in được rồi", null, "Tuyệt vời, đã xong")
                    : new LlmStepDecision("CONTINUE", null, null, null, null);
        });

        buildSop("llm-resolve-printer");
        ConversationResponse conv = conversationService.create(new ConversationRequest("bob", "máy in không in được"));
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("bật rồi", null, null)); // step 2
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("in được chưa?", null, null)); // -> terminal 3 -> RESOLVED
        assertEquals("RESOLVED", conv.status());
    }

    @Test
    void llmInvalidBranchKeyIsRejectedGuardrail() {
        when(llmPort.isConfigured()).thenReturn(true);
        // LLM returns a branchKey that does NOT exist on step 1 -> must be ignored,
        // engine falls back to defaultNext ("2"), never jumps to an arbitrary step.
        when(llmPort.decide(any(), anyString()))
                .thenReturn(new LlmStepDecision("CONTINUE", "bogus-key", null, null, null));

        buildSop("llm-guard-printer");
        ConversationResponse conv = conversationService.create(new ConversationRequest("carol", "máy in không in được"));
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("trả lời", null, null));
        assertEquals("2", conv.currentStepKey()); // defaultNext, NOT a jump
    }

    @Test
    void llmDrivesEscalationWithReason() {
        when(llmPort.isConfigured()).thenReturn(true);
        when(llmPort.decide(any(), anyString())).thenAnswer(inv -> {
            var snap = inv.getArgument(0, com.helpdesk.domain.engine.ConversationSnapshot.class);
            boolean atStep2 = "2".equals(snap.currentStep().stepKey());
            return atStep2
                    ? new LlmStepDecision("ESCALATE", null, null, "vẫn kẹt giấy sau khi thử", null)
                    : new LlmStepDecision("CONTINUE", null, null, null, null);
        });

        buildSop("llm-esc-printer");
        ConversationResponse conv = conversationService.create(new ConversationRequest("dan", "máy in không in được"));
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("bật", null, null)); // step 2
        conv = conversationService.sendMessage(conv.id(), new MessageRequest("vẫn lỗi", null, null)); // ESCALATE at step 2
        assertEquals("ESCALATED", conv.status());
    }
}
