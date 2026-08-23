package com.helpdesk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.web.dto.HotelAdminRequest;
import com.helpdesk.web.dto.SopRequest;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Hotel admin CRUD (Phase 1G). Verifies create/get/update/
 * delete plus the delete guard (422 when a hotel still owns SOPs/conversations).
 * Uses the in-memory H2 test profile (application-test.yml) — no external DB/LLM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HotelApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private HotelAdminRequest req(String id, String name, String location, String region) {
        return new HotelAdminRequest(id, name, location, region);
    }

    @Test
    void createAndGetHotel() throws Exception {
        HotelAdminRequest body = req("h-create-1", "Created Hotel", "HCMC", "South");
        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("h-create-1"))
                .andExpect(jsonPath("$.name").value("Created Hotel"))
                .andExpect(jsonPath("$.location").value("HCMC"))
                .andExpect(jsonPath("$.region").value("South"));

        mvc.perform(get("/api/hotels/h-create-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Created Hotel"))
                .andExpect(jsonPath("$.region").value("South"));
    }

    @Test
    void getMissingReturns404() throws Exception {
        mvc.perform(get("/api/hotels/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateHotel() throws Exception {
        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("h-update-1", "Before", "Loc A", "R1"))))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/hotels/h-update-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("h-update-1", "After", "Loc B", "R2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.location").value("Loc B"))
                .andExpect(jsonPath("$.region").value("R2"));

        mvc.perform(get("/api/hotels/h-update-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"));
    }

    @Test
    void updateMissingReturns404() throws Exception {
        mvc.perform(put("/api/hotels/missing-hotel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("missing-hotel", "X", "Y", "Z"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHotel() throws Exception {
        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("h-delete-me", "To Delete", "Loc", "R"))))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/hotels/h-delete-me"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/hotels/h-delete-me"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        mvc.perform(delete("/api/hotels/missing-hotel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGuardRejectsHotelWithSops() throws Exception {
        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("h-guard-1", "Guarded", "Loc", "R"))))
                .andExpect(status().isCreated());

        // Attach a SOP to the hotel so it is considered "in use".
        SopRequest sop = sampleSop("guard-sop-1");
        mvc.perform(post("/api/sops").param("hotelId", "h-guard-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(sop)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/hotels/h-guard-1"))
                .andExpect(status().isUnprocessableEntity());

        // Hotel still exists after the rejected delete.
        mvc.perform(get("/api/hotels/h-guard-1"))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateCreateRejected() throws Exception {
        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("h-dup-1", "Dup", "Loc", "R"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("h-dup-1", "Dup", "Loc", "R"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidCreateRejected() throws Exception {
        // missing name -> bean validation failure
        HotelAdminRequest bad = new HotelAdminRequest("h-bad", "", "Loc", "R");
        mvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    private SopRequest sampleSop(String id) {
        return new SopRequest(
                id, "Guard SOP " + id, "desc", "IT / Test",
                "problem text",
                List.of("symptom-a"),
                List.of("prereq-1"),
                "expected", "failure", "escalation",
                List.of(
                        new StepRequest("1", 1, "Step one instruction", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "Step two", SopStepType.ESCALATE, null, true, SopTerminalKind.ESCALATE, List.of())
                )
        );
    }
}
