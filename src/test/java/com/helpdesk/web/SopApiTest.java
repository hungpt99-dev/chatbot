package com.helpdesk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopSummary;
import com.helpdesk.web.dto.StepRequest;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the SOP CRUD API (Phase 1A). Uses an in-memory H2 profile
 * (application-test.yml) — no external DB or LLM required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SopApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private SopRequest sample(String id) {
        return new SopRequest(
                id, "Test SOP " + id, "desc", "IT / Test",
                "problem text",
                java.util.List.of("symptom-a", "symptom-b"),
                java.util.List.of("prereq-1"),
                "expected", "failure", "escalation",
                java.util.List.of(
                        new StepRequest("1", 1, "Step one instruction", SopStepType.QUESTION, "2", false, null, java.util.List.of()),
                        new StepRequest("2", 2, "Step two", SopStepType.ESCALATE, null, true, SopTerminalKind.ESCALATE, java.util.List.of())
                )
        );
    }

    @Test
    void createAndGetSop() throws Exception {
        SopRequest req = sample("test-create-1");
        MvcResult res = mvc.perform(post("/api/sops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-create-1"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andReturn();

        // GET returns the same
        mvc.perform(get("/api/sops/test-create-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test SOP test-create-1"))
                .andExpect(jsonPath("$.steps[0].branches.length()").value(0));
    }

    @Test
    void duplicateCreateRejected() throws Exception {
        SopRequest req = sample("test-dup-1");
        mvc.perform(post("/api/sops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/sops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void getMissingReturns404() throws Exception {
        mvc.perform(get("/api/sops/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSop() throws Exception {
        SopRequest req = sample("test-update-1");
        mvc.perform(post("/api/sops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        SopRequest updated = sample("test-update-1");
        // mutate title via reflection-free copy
        SopRequest changed = new SopRequest(
                "test-update-1", "Renamed SOP", "desc2", "IT / Test",
                "problem", java.util.List.of("x"), java.util.List.of(),
                "e", "f", "esc", req.steps());
        mvc.perform(put("/api/sops/test-update-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed SOP"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void listAndCategoryFilter() throws Exception {
        mvc.perform(post("/api/sops").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(sample("test-list-1"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/sops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'test-list-1')]").exists());

        mvc.perform(get("/api/sops").param("category", "IT / Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'test-list-1')]").exists());
    }

    @Test
    void invalidRequestRejected() throws Exception {
        // missing title
        SopRequest bad = new SopRequest("bad-1", "", "d", "c", "p",
                java.util.List.of(), java.util.List.of(), "e", "f", "esc",
                java.util.List.of(new StepRequest("1", 1, "x", SopStepType.QUESTION, "2", false, null, java.util.List.of())));
        mvc.perform(post("/api/sops").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }
}
