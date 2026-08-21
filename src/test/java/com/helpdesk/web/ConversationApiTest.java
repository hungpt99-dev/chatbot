package com.helpdesk.web;

import com.helpdesk.application.SopService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API-level contract for the conversation + case endpoints (Phase 1B).
 * Provider-free: the offline interpreter drives outcomes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConversationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SopService sopService;

    private void seed() {
        SopRequest req = new SopRequest(
                "api-printer", "Printer", "d", "IT", "máy in không in được",
                List.of("máy in", "không in được"), List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "Bật nguồn?", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "In thử", SopStepType.CHECK, null, true, SopTerminalKind.RESOLVE, List.of())));
        sopService.create(req);
    }

    @Test
    void createConversationThenResolveThenCaseAppears() throws Exception {
        seed();
        String createJson = """
                {"employee":"amy","problem":"máy in không in được"}""";
        String body = mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sopId").value("api-printer"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(body).get("id").asLong();

        // Resolve on step 1 -> RESOLVED.
        String msg = """
                {"message":"xong rồi","stepResult":{"intent":"TROUBLESHOOT","sopId":"api-printer","currentStep":"1","result":"RESOLVE","resolved":"ok"}}""";
        mockMvc.perform(post("/api/conversations/" + id + "/messages").contentType(MediaType.APPLICATION_JSON).content(msg))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/conversations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/cases").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.conversationId == " + id + ")].status").exists());
    }

    @Test
    void noMatchingSopReturns422() throws Exception {
        String createJson = """
                {"employee":"x","problem":"tàu hỏa trễ giờ"}""";
        mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void messageToClosedConversationReturns409() throws Exception {
        seed();
        String createJson = """
                {"employee":"y","problem":"máy in không in được"}""";
        String body = mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        String msg = """
                {"message":"xong","stepResult":{"intent":"TROUBLESHOOT","sopId":"api-printer","currentStep":"1","result":"RESOLVE"}}""";
        mockMvc.perform(post("/api/conversations/" + id + "/messages").contentType(MediaType.APPLICATION_JSON).content(msg))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/conversations/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getMissingConversationReturns404() throws Exception {
        mockMvc.perform(get("/api/conversations/999999"))
                .andExpect(status().isNotFound());
    }
}
