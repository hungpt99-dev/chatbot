package com.helpdesk.infrastructure.seed;

import com.helpdesk.application.SopService;
import com.helpdesk.web.dto.SopRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the 8 Phase 1A SOPs from JSON on startup (idempotent: existing SOP ids are
 * skipped). Gated by {@code helpdesk.seed.enabled} so tests can disable it.
 */
@Component
public class SopSeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SopSeedLoader.class);

    private final SopService sopService;
    private final boolean enabled;

    public SopSeedLoader(SopService sopService, org.springframework.core.env.Environment env) {
        this.sopService = sopService;
        this.enabled = "true".equalsIgnoreCase(env.getProperty("helpdesk.seed.enabled", "true"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        List<String> files = List.of(
                "printer-cannot-print",
                "computer-no-internet",
                "wifi-cannot-connect",
                "cannot-login",
                "password-reset",
                "email-cannot-send",
                "vpn-cannot-connect",
                "monitor-not-working"
        );
        int seeded = 0;
        for (String f : files) {
            try {
                SopRequest req = read(f);
                if (req == null) {
                    continue;
                }
                sopService.create(req); // create() throws DuplicateSopException if exists; catch below
                seeded++;
            } catch (com.helpdesk.web.DuplicateSopException dup) {
                // already seeded — fine, idempotent
            } catch (Exception e) {
                log.warn("Failed to seed SOP {}: {}", f, e.getMessage());
            }
        }
        log.info("SOP seed complete: {} new SOP(s) loaded.", seeded);
    }

    private SopRequest read(String name) {
        String path = "sop/" + name + ".json";
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) {
            log.warn("Seed file missing: {}", path);
            return null;
        }
        try (InputStream in = res.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(in, SopRequest.class);
        } catch (Exception e) {
            log.warn("Could not parse seed {}: {}", path, e.getMessage());
            return null;
        }
    }
}
