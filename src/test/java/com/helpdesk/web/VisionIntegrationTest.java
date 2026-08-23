package com.helpdesk.web;

import com.helpdesk.application.SopService;
import com.helpdesk.domain.model.MessageAttachment;
import com.helpdesk.domain.port.VisionPort;
import com.helpdesk.domain.repository.MessageAttachmentRepository;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.StepRequest;
import com.helpdesk.web.dto.BranchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Screenshot / vision flow at the API boundary. VisionPort is mocked so the test
 * verifies (a) the attachment bytes are persisted tenant-scoped to the
 * conversation and (b) the vision description is folded into the message the
 * engine/LLM sees. Unconfigured vision degrades gracefully (attachment still
 * stored, request still succeeds).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VisionIntegrationTest {

    private static final String HOTEL = "test-hotel";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SopService sopService;
    @Autowired MessageAttachmentRepository attachmentRepository;
    @MockBean VisionPort visionPort;

    private void seed() {
        SopRequest req = new SopRequest(
                "api-printer", "Printer", "d", "IT", "máy in không in được",
                List.of("máy in", "không in được"), List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "Bật nguồn?", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "In thử", SopStepType.CHECK, null, true, SopTerminalKind.RESOLVE, List.of())));
        sopService.create(HOTEL, req);
    }

    private Long createConversation() throws Exception {
        String createJson = """
                {"hotelId":"test-hotel","employee":"amy","problem":"máy in không in được"}""";
        String body = mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void attachmentPersistedAndVisionDescriptionFedToEngine() throws Exception {
        seed();
        Long id = createConversation();

        when(visionPort.isConfigured()).thenReturn(true);
        when(visionPort.analyze(any(byte[].class), any(), any()))
                .thenReturn("PRINTER_ERROR_LED_BLINKING");

        MockMultipartFile file = new MockMultipartFile("image", "shot.png", "image/png", new byte[]{1, 2, 3, 4});
        String resp = mockMvc.perform(multipart("/api/conversations/" + id + "/messages")
                        .file(file)
                        .param("message", "here is my printer"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(resp.contains("PRINTER_ERROR_LED_BLINKING"), "vision description should reach the conversation");

        List<MessageAttachment> atts = attachmentRepository.findByConversationIdOrderByCreatedAtAsc(id);
        assertEquals(1, atts.size());
        assertEquals("image/png", atts.get(0).getContentType());
        assertEquals(HOTEL, atts.get(0).getHotelId());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, atts.get(0).getData());
    }

    @Test
    void unconfiguredVisionStillStoresAttachmentAndSucceeds() throws Exception {
        seed();
        Long id = createConversation();

        when(visionPort.isConfigured()).thenReturn(false);
        when(visionPort.analyze(any(byte[].class), any(), any())).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("image", "shot.jpg", "image/jpeg", new byte[]{9, 8, 7});
        mockMvc.perform(multipart("/api/conversations/" + id + "/messages")
                        .file(file)
                        .param("message", "printer broken"))
                .andExpect(status().isOk());

        List<MessageAttachment> atts = attachmentRepository.findByConversationIdOrderByCreatedAtAsc(id);
        assertEquals(1, atts.size());
        assertEquals("image/jpeg", atts.get(0).getContentType());
        assertArrayEquals(new byte[]{9, 8, 7}, atts.get(0).getData());
        verify(visionPort, never()).analyze(any(), any(), any());
    }

    @Test
    void messageWithoutAttachmentStillWorks() throws Exception {
        seed();
        Long id = createConversation();

        mockMvc.perform(post("/api/conversations/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"bật rồi\"}"))
                .andExpect(status().isOk());

        assertTrue(attachmentRepository.findByConversationIdOrderByCreatedAtAsc(id).isEmpty());
    }
}
