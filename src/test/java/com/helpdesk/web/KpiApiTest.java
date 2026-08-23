package com.helpdesk.web;

import com.helpdesk.application.SopService;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.BranchRequest;
import com.helpdesk.web.dto.StepRequest;
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
 * KPI telemetry endpoint contract: after seeding conversations with mixed
 * outcomes, {@code GET /api/admin/kpis} returns a sane aggregate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KpiApiTest {

    private static final String HOTEL = "kpi-hotel";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SopService sopService;

    private void seed() {
        SopRequest req = new SopRequest(
                "kpi-printer", "Printer", "d", "IT", "máy in không in được",
                List.of("máy in", "không in được"), List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "Bật nguồn?", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "In thử", SopStepType.CHECK, null, true, SopTerminalKind.RESOLVE, List.of())));
        sopService.create(HOTEL, req);
    }

    private Long startConversation() throws Exception {
        String createJson = """
                {"hotelId":"kpi-hotel","employee":"amy","problem":"máy in không in được"}""";
        String body = mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void kpisReflectSeededConversations() throws Exception {
        seed();

        Long resolved = startConversation();
        mockMvc.perform(post("/api/conversations/" + resolved + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"bật rồi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        Long escalated = startConversation();
        mockMvc.perform(post("/api/conversations/" + escalated + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"still not working\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        mockMvc.perform(get("/api/admin/kpis").param("hotelId", HOTEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConversations").value(2))
                .andExpect(jsonPath("$.escalated").value(1))
                .andExpect(jsonPath("$.resolutionRate").value(0.5))
                .andExpect(jsonPath("$.avgLatencyMs").exists());
    }

    @Test
    void kpisEmptyHotelReturnsZeros() throws Exception {
        mockMvc.perform(get("/api/admin/kpis").param("hotelId", "no-such-hotel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConversations").value(0))
                .andExpect(jsonPath("$.escalated").value(0))
                .andExpect(jsonPath("$.resolutionRate").value(0.0))
                .andExpect(jsonPath("$.avgLatencyMs").value(0.0));
    }
}
