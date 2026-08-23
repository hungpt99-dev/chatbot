package com.helpdesk.domain.retrieval;

import com.helpdesk.application.SopService;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.StepRequest;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies retrieval thresholding: the best-matching SOP is always returned, and
 * near-irrelevant SOPs that share only a few common words are pruned.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RetrievalThresholdTest {

    private static final String HOTEL = "test-hotel";

    @Autowired SopService sopService;

    private SopRequest sop(String id, String title, String problem, List<String> symptoms) {
        return new SopRequest(
                id, title, "desc", "IT", problem, symptoms, List.of(), "e", "f", "esc",
                List.of(
                        new StepRequest("1", 1, "x", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "done", SopStepType.ESCALATE, null, true, SopTerminalKind.ESCALATE, List.of()))
        );
    }

    @Test
    void bestCandidateAlwaysReturnedAndNoisePruned() {
        // Strongly printer-related SOP plus a weakly-related one (shares generic words).
        sopService.create(HOTEL, sop("th-1", "Printer cannot print", "máy in không in được giấy",
                List.of("máy in", "không in được", "paper jam", "kẹt giấy")));
        sopService.create(HOTEL, sop("th-2", "Monitor not working", "màn hình không lên",
                List.of("màn hình", "không sáng")));

        var res = sopService.retrieve(HOTEL, "Máy in không in được");
        assertFalse(res.isEmpty());
        assertEquals("th-1", res.candidates().get(0).getCode());
        // The weakly-related monitor SOP should be pruned (score far below 50% of best).
        assertTrue(res.candidates().stream().noneMatch(s -> s.getCode().equals("th-2")),
                "weakly-related SOP should be filtered out");
    }

    @Test
    void genuineTieBothReturned() {
        sopService.create(HOTEL, sop("th-3", "Password reset A", "quên mật khẩu tài khoản",
                List.of("quên mật khẩu", "đổi mật khẩu")));
        sopService.create(HOTEL, sop("th-4", "Password reset B", "quên mật khẩu đăng nhập",
                List.of("quên mật khẩu", "đăng nhập")));

        var res = sopService.retrieve(HOTEL, "quên mật khẩu");
        assertTrue(res.candidates().size() >= 2);
    }
}
