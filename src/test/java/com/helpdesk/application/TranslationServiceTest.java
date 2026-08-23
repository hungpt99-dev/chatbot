package com.helpdesk.application;

import com.helpdesk.domain.port.TranslationPort;
import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.repository.ConversationRepository;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.BranchRequest;
import com.helpdesk.web.dto.StepRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Multilingual (BRD §4) behavior at the application layer: the assistant message
 * is localized through {@link TranslationPort} only when a language is requested
 * and the port is configured (mocked here). When unconfigured / no language the
 * original (authored) text is returned untouched.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TranslationServiceTest {

    private static final String HOTEL = "test-hotel";

    @Autowired SopService sopService;
    @Autowired ConversationService conversationService;
    @Autowired ConversationRepository conversationRepository;

    @MockBean TranslationPort translationPort;

    private SopResponse buildSop(String id) {
        SopRequest req = new SopRequest(
                id, "Printer", "desc", "IT", "máy in không in được",
                List.of("máy in"), List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "Máy in có bật không?", SopStepType.QUESTION, "2",
                                false, null, List.of(
                                        new BranchRequest("off", "tắt", "2"),
                                        new BranchRequest("on", "bật", "2"))),
                        new StepRequest("2", 2, "Kiểm tra kẹt giấy", SopStepType.ACTION, "3",
                                false, null, List.of()),
                        new StepRequest("3", 3, "In thử", SopStepType.CHECK, null,
                                true, SopTerminalKind.RESOLVE, List.of())));
        return sopService.create(HOTEL, req);
    }

    @Test
    void translatesAssistantMessageWhenConfiguredAndLangRequested() {
        when(translationPort.isConfigured()).thenReturn(true);
        when(translationPort.translate(anyString(), eq("en"))).thenReturn("Is the printer turned on?");

        buildSop("tr-printer");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "alice", "máy in không in được", "en"));

        assertEquals("Is the printer turned on?",
                conv.messages().get(1).content());
        verify(translationPort).translate(anyString(), eq("en"));
    }

    @Test
    void doesNotTranslateWhenNoLangRequested() {
        when(translationPort.isConfigured()).thenReturn(true);

        buildSop("tr-printer2");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "bob", "máy in không in được"));

        String assistant = conv.messages().get(1).content();
        assertNotEquals("Is the printer turned on?", assistant);
        verify(translationPort, never()).translate(anyString(), anyString());
    }

    @Test
    void passesThroughWhenTranslationUnconfiguredEvenWithLang() {
        when(translationPort.isConfigured()).thenReturn(false);

        buildSop("tr-printer3");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "carol", "máy in không in được", "en"));

        String assistant = conv.messages().get(1).content();
        assertTrue(assistant.contains("Máy in có bật không?"),
                "assistant should keep authored text when translation unconfigured");
        verify(translationPort, never()).translate(anyString(), anyString());
    }

    @Test
    void translatesFollowUpMessageWhenLangRequested() {
        when(translationPort.isConfigured()).thenReturn(true);
        when(translationPort.translate(anyString(), eq("en")))
                .thenReturn("Step text (translated)");

        buildSop("tr-printer4");
        ConversationResponse conv = conversationService.create(
                new ConversationRequest(HOTEL, "dave", "máy in không in được", "en"));
        conversationService.sendMessage(conv.id(),
                new MessageRequest("máy in đang bật", "on", null, "en"));

        ConversationResponse reloaded = conversationService.get(conv.id());
        assertEquals(ConversationStatus.IN_PROGRESS, reloaded.status());
        verify(translationPort, atLeast(2)).translate(anyString(), eq("en"));
    }
}
