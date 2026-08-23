package com.helpdesk.application;

import com.helpdesk.domain.engine.EngineResult;
import com.helpdesk.domain.engine.LlmPort;
import com.helpdesk.domain.engine.OfflineInterpreter;
import com.helpdesk.domain.engine.ResponseComposer;
import com.helpdesk.domain.engine.SopExecutionEngine;
import com.helpdesk.domain.engine.StepOutcome;
import com.helpdesk.domain.engine.StepResult;
import com.helpdesk.domain.model.Conversation;
import com.helpdesk.domain.model.ConversationMessage;
import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopStep;
import com.helpdesk.domain.model.StepType;
import com.helpdesk.domain.model.SupportCase;
import com.helpdesk.domain.port.TicketPort;
import com.helpdesk.domain.repository.AuditEventRepository;
import com.helpdesk.domain.repository.ConversationMessageRepository;
import com.helpdesk.domain.repository.ConversationRepository;
import com.helpdesk.domain.repository.SupportCaseRepository;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.dto.SopResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the real-ticket-escalation wiring: on ESCALATE the {@link TicketPort}
 * is invoked and the returned external reference is persisted on the
 * {@link SupportCase}. Uses mocked collaborators so the escalation can be driven
 * deterministically without an LLM or a live Helpdesk.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTicketTest {

    @Mock ConversationRepository conversationRepository;
    @Mock ConversationMessageRepository messageRepository;
    @Mock SupportCaseRepository caseRepository;
    @Mock AuditEventRepository auditRepository;
    @Mock SopService sopService;
    @Mock SopExecutionEngine engine;
    @Mock LlmPort llmPort;
    @Mock OfflineInterpreter interpreter;
    @Mock ResponseComposer composer;
    @Mock TicketPort ticketPort;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversationRepository, messageRepository, caseRepository,
                auditRepository, sopService, engine, llmPort, interpreter, composer, ticketPort);
    }

    @Test
    void escalatedCaseRaisesExternalTicketAndStoresRef() {
        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setHotelId("hotel-1");
        conv.setSopId("printer");
        conv.setCurrentStepKey("1");
        conv.setStatus(ConversationStatus.IN_PROGRESS);
        conv.setEmployee("jdoe");
        conv.setProblemSummary("Printer won't print");
        conv.setStartedAt(Instant.now());

        Sop sop = buildSop();
        SopResponse.StepDto current = SopResponse.from(sop).steps().get(0);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(messageRepository.maxSeq(1L)).thenReturn(0);
        when(messageRepository.findByConversationIdOrderBySeqAsc(anyLong())).thenReturn(List.of());
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(conversationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sopService.load("hotel-1", "printer")).thenReturn(sop);
        when(llmPort.isConfigured()).thenReturn(false);
        when(interpreter.interpret(anyString(), any())).thenReturn(StepOutcome.of(null, StepResult.ESCALATE));
        when(engine.advance(any(), any(), any()))
                .thenReturn(new EngineResult(current, "2", "ESCALATED", List.of(), true));
        when(composer.compose(any(), any())).thenReturn("Escalating to Helpdesk.");
        when(caseRepository.findByConversationId(1L)).thenReturn(null);
        when(ticketPort.raiseTicket(any())).thenReturn("EXT-999");
        when(caseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.sendMessage(1L, new MessageRequest("give up", null, null));

        verify(ticketPort, times(1)).raiseTicket(any());
        ArgumentCaptor<SupportCase> cap = ArgumentCaptor.forClass(SupportCase.class);
        verify(caseRepository).save(cap.capture());
        assertEquals("EXT-999", cap.getValue().getExternalTicketRef());
    }

    @Test
    void escalatedCaseWithUnconfiguredTicketPortPersistsWithoutRef() {
        Conversation conv = new Conversation();
        conv.setId(2L);
        conv.setHotelId("hotel-1");
        conv.setSopId("printer");
        conv.setCurrentStepKey("1");
        conv.setStatus(ConversationStatus.IN_PROGRESS);
        conv.setEmployee("jdoe");
        conv.setProblemSummary("Printer won't print");
        conv.setStartedAt(Instant.now());

        Sop sop = buildSop();
        SopResponse.StepDto current = SopResponse.from(sop).steps().get(0);

        when(conversationRepository.findById(2L)).thenReturn(Optional.of(conv));
        when(messageRepository.maxSeq(2L)).thenReturn(0);
        when(messageRepository.findByConversationIdOrderBySeqAsc(anyLong())).thenReturn(List.of());
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(conversationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sopService.load("hotel-1", "printer")).thenReturn(sop);
        when(llmPort.isConfigured()).thenReturn(false);
        when(interpreter.interpret(anyString(), any())).thenReturn(StepOutcome.of(null, StepResult.ESCALATE));
        when(engine.advance(any(), any(), any()))
                .thenReturn(new EngineResult(current, "2", "ESCALATED", List.of(), true));
        when(composer.compose(any(), any())).thenReturn("Escalating to Helpdesk.");
        when(caseRepository.findByConversationId(2L)).thenReturn(null);
        when(ticketPort.raiseTicket(any())).thenReturn(null);
        when(caseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.sendMessage(2L, new MessageRequest("give up", null, null));

        verify(ticketPort, times(1)).raiseTicket(any());
        ArgumentCaptor<SupportCase> cap = ArgumentCaptor.forClass(SupportCase.class);
        verify(caseRepository).save(cap.capture());
        assertEquals(null, cap.getValue().getExternalTicketRef());
    }

    private Sop buildSop() {
        SopStep step = new SopStep();
        step.setStepKey("1");
        step.setStepOrder(1);
        step.setInstruction("Is it on?");
        step.setType(StepType.QUESTION);
        step.setDefaultNext("2");
        step.setTerminal(false);
        step.setBranches(List.of());

        Sop sop = new Sop("printer", "Printer");
        sop.setHotelId("hotel-1");
        sop.setCode("printer");
        sop.setDescription("");
        sop.setCategory("");
        sop.setProblemDescription("");
        sop.setSymptoms(List.of());
        sop.setPrerequisites(List.of());
        sop.setExpectedResult("");
        sop.setFailureCondition("");
        sop.setEscalationCondition("");
        sop.setVersion(1);
        sop.setCreatedAt(Instant.now());
        sop.setUpdatedAt(Instant.now());
        sop.setSteps(List.of(step));
        return sop;
    }
}
