package com.helpdesk.infrastructure.seed;

import com.helpdesk.application.SopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the 8 bundled seed SOPs load on startup when seeding is enabled. Run in its
 * own context (seed enabled via property) so it does not depend on other tests' state.
 */
@SpringBootTest(properties = "helpdesk.seed.enabled=true")
@ActiveProfiles("test")
class SopSeedLoaderTest {

    @Autowired SopService sopService;

    @Test
    void eightSeedSopsLoad() {
        List<?> all = sopService.list("grand-hotel-saigon", null);
        assertTrue(all.size() >= 8, "expected at least 8 seeded SOPs, got " + all.size());
    }

    @Test
    void printerSopRetrievableInVietnamese() {
        var res = sopService.retrieve("grand-hotel-saigon", "Máy in không in được");
        assertFalse(res.isEmpty());
        assertEquals("printer-cannot-print", res.candidates().get(0).getCode());
    }
}
