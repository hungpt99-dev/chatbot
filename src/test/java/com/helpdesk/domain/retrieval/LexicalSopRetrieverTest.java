package com.helpdesk.domain.retrieval;

import com.helpdesk.application.SopService;
import com.helpdesk.domain.model.Sop;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.StepRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that retrieval returns the correct SOP for representative problem text,
 * and that it does not return anything for totally unrelated input.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LexicalSopRetrieverTest {

    private static final String HOTEL = "test-hotel";

    @Autowired SopService sopService;

    private SopRequest stepDef(String id, String title, String problem, List<String> symptoms) {
        return new SopRequest(
                id, title, "desc", "IT",
                problem, symptoms, List.of(), "e", "f", "esc",
                List.of(new StepRequest("1", 1, "x", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "done", SopStepType.ESCALATE, null, true, SopTerminalKind.ESCALATE, List.of()))
        );
    }

    @Test
    void retrievesPrinterSopForVietnameseQuery() {
        sopService.create(HOTEL, stepDef("ret-1", "Printer cannot print", "máy in không in được", List.of("không in được", "máy in", "paper jam")));
        sopService.create(HOTEL, stepDef("ret-2", "WiFi cannot connect", "không kết nối wifi", List.of("wifi", "sai mật khẩu")));

        var res = sopService.retrieve(HOTEL, "Máy in không in được");
        assertFalse(res.isEmpty());
        assertEquals("ret-1", res.candidates().get(0).getCode());
    }

    @Test
    void retrievesWifiSopForEnglishQuery() {
        sopService.create(HOTEL, stepDef("ret-3", "Printer cannot print", "printer issue", List.of("printer", "paper jam")));
        sopService.create(HOTEL, stepDef("ret-4", "WiFi cannot connect", "wifi issue", List.of("wifi", "cannot connect")));

        var res = sopService.retrieve(HOTEL, "I cannot connect to wifi");
        assertFalse(res.isEmpty());
        assertEquals("ret-4", res.candidates().get(0).getCode());
    }

    @Test
    void noMatchForUnrelatedQuery() {
        sopService.create(HOTEL, stepDef("ret-5", "Printer cannot print", "printer", List.of("printer")));
        var res = sopService.retrieve(HOTEL, "cho tôi công thức nấu phở");
        assertTrue(res.isEmpty());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        var res = sopService.retrieve(HOTEL, "");
        assertTrue(res.isEmpty());
    }
}
