package com.helpdesk.infrastructure.seed;

import com.helpdesk.application.SopService;
import com.helpdesk.domain.model.Hotel;
import com.helpdesk.domain.repository.HotelRepository;
import com.helpdesk.web.dto.SopRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds hotels and their SOPs on startup (idempotent). Multi-tenant (ADR-0008):
 * each hotel owns its own SOP instances, so the shared corporate SOP set is
 * replicated per hotel. A hotel may also have hotel-specific SOP files
 * (keyed by hotel id) to reflect properties that differ.
 */
@Component
@Slf4j
public class SopSeedLoader {

    private final SopService sopService;
    private final HotelRepository hotelRepository;
    private final boolean enabled;

    public SopSeedLoader(SopService sopService, HotelRepository hotelRepository,
                         org.springframework.core.env.Environment env) {
        this.sopService = sopService;
        this.hotelRepository = hotelRepository;
        this.enabled = "true".equalsIgnoreCase(env.getProperty("helpdesk.seed.enabled", "true"));
    }

    /** Shared corporate SOP set, replicated for every hotel. */
    private static final List<String> SHARED_SOPS = List.of(
            "printer-cannot-print",
            "computer-no-internet",
            "wifi-cannot-connect",
            "cannot-login",
            "password-reset",
            "email-cannot-send",
            "vpn-cannot-connect",
            "monitor-not-working",
            "keycard-encoder-failure",
            "pos-terminal-down",
            "booking-sync-failure"
    );

    /** Hotels in the chain. id is also the SOP id prefix (hotelId:code). */
    private static final List<Hotel> HOTELS = List.of(
            new Hotel("grand-hotel-saigon", "Grand Hotel Saigon", "Ho Chi Minh City, VN"),
            new Hotel("seaside-resort-danang", "Seaside Resort Danang", "Danang, VN")
    );

    /** Extra SOP files specific to one hotel (demonstrates per-property divergence). */
    private static final Map<String, List<String>> HOTEL_SPECIFIC = Map.of(
            "seaside-resort-danang", List.of("spa-booking-system-down")
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        int seededSops = 0;
        for (Hotel hotel : HOTELS) {
            if (!hotelRepository.existsById(hotel.getId())) {
                hotelRepository.save(hotel);
            }
            List<String> files = new ArrayList<>(SHARED_SOPS);
            files.addAll(HOTEL_SPECIFIC.getOrDefault(hotel.getId(), List.of()));
            for (String f : files) {
                try {
                    SopRequest req = read(f);
                    if (req == null) continue;
                    sopService.create(hotel.getId(), req); // throws DuplicateSopException if exists
                    seededSops++;
                } catch (com.helpdesk.web.exception.DuplicateSopException dup) {
                    // already seeded for this hotel — idempotent
                } catch (Exception e) {
                    log.warn("Failed to seed SOP {} for hotel {}: {}", f, hotel.getId(), e.getMessage());
                }
            }
        }
        log.info("SOP seed complete: {} new SOP instance(s) loaded across {} hotels.", seededSops, HOTELS.size());
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
