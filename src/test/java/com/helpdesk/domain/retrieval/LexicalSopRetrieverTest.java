package com.helpdesk.domain.retrieval;

import com.helpdesk.application.SopService;
import com.helpdesk.domain.model.Sop;
import com.helpdesk.web.dto.SopRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that retrieval returns the correct SOP for representative problem text,
 * and that it does not return anything for totally unrelated input.
 */
@SpringBootTest
class LexicalSopRetrieverTest {

    @Autowired SopService sopService;

    private SopRequest stepDef(String id, String title, String problem, List<String> symptoms) {
        return new com.helpdesk.web.dto.SopRequest(
                id, title, "desc", "IT",
                problem, symptoms, List.of(), "e", "f", "esc",
                List.of(new com.helpdesk.web.dto.StepRequest("1", 1, "x", com.helpdesk.web.dto.SopStepType.QUESTION, "2", false, null, List.of()),
                        new com.helpdesk.web.dto.StepRequest("2", 2, "done", com.helpdesk.web.dto.SopStepType.ESCALATE, null, true, com.helpdesk.web.dto.SopTerminalKind.ESCALATE, List.of()))
        );
    }

    @Test
    void retrievesPrinterSopForVietnameseQuery() {
        sopService.create(stepDef("ret-1", "Printer cannot print", "máy in không in được", List.of("không in được", "máy in", "paper jam")));
        sopService.create(stepDef("ret-2", "WiFi cannot connect", "không kết nối wifi", List.of("wifi", "sai mật khẩu")));

        var res = sopService.retrieve("Máy in không in được");
        assertFalse(res.isEmpty());
        assertEquals("ret-1", res.candidates().get(0).getId());
    }

    @Test
    void retrievesWifiSopForEnglishQuery() {
        sopService.create(stepDef("ret-3", "Printer cannot print", "printer issue", List.of("printer", "paper jam")));
        sopService.create(stepDef("ret-4", "WiFi cannot connect", "wifi issue", List.of("wifi", "cannot connect")));

        var res = sopService.retrieve("I cannot connect to wifi");
        assertFalse(res.isEmpty());
        assertEquals("ret-4", res.candidates().get(0).getId());
    }

    @Test
    void noMatchForUnrelatedQuery() {
        sopService.create(stepDef("ret-5", "Printer cannot print", "printer", List.of("printer")));
        var res = sopService.retrieve("cho tôi công thức nấu phở");
        assertTrue(res.isEmpty());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        var res = sopService.retrieve("");
        assertTrue(res.isEmpty());
    }
}
