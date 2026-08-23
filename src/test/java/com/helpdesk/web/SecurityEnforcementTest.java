package com.helpdesk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the BRD §7 security contract when {@code helpdesk.security.enabled=true}:
 *
 * <ul>
 *   <li>{@code /api/health} is public (no auth).</li>
 *   <li>Unauthenticated {@code /api/**} calls are redirected to the SSO login.</li>
 *   <li>EMPLOYEE may chat (POST /api/conversations) but not perform IT_ADMIN
 *       actions (SOP / hotel management).</li>
 *   <li>IT_ADMIN may perform admin actions.</li>
 * </ul>
 *
 * <p>This complements the default (disabled) suite: with security off, the
 * {@code @PreAuthorize} annotations are inert and every request is permitted,
 * so the existing unauthenticated tests keep passing.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "security"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SecurityEnforcementTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedApiIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/api/hotels")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanChatButNotManageSops() throws Exception {
        // Employee is authorized to chat: the request must not be rejected for
        // authorization reasons (401/403). A 422 here just means no matching SOP
        // was found in this profile's seed data — auth has already passed.
        mvc.perform(post("/api/conversations")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatRequest()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertFalse(
                            status == 401 || status == 403,
                            "EMPLOYEE chat must be authorized, got " + status);
                });

        mvc.perform(post("/api/sops").param("hotelId", "h")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sopRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "IT_ADMIN")
    void itAdminCanManageSops() throws Exception {
        mvc.perform(post("/api/sops").param("hotelId", "h")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sopRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotDeleteHotel() throws Exception {
        mvc.perform(delete("/api/hotels/h")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    private String chatRequest() {
        return "{\"hotelId\":\"grand-hotel-saigon\",\"employee\":\"amy\",\"problem\":\"printer cannot print\"}";
    }

    private String sopRequest() {
        return """
                {"id":"sec-sop","title":"Security SOP","description":"d","category":"IT",
                 "problemDescription":"p","symptoms":["s"],"prerequisites":[],
                 "expectedResult":"e","failureCondition":"f","escalationCondition":"esc",
                 "steps":[{"stepKey":"1","stepOrder":1,"instruction":"do","type":"QUESTION",
                           "defaultNext":"2","terminal":false,"branches":[]},
                          {"stepKey":"2","stepOrder":2,"instruction":"done","type":"CHECK",
                           "defaultNext":null,"terminal":true,"terminalKind":"RESOLVE",
                           "branches":[]}]}""";
    }
}
