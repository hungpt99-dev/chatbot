package com.helpdesk.web;

import com.helpdesk.application.KpiService;
import com.helpdesk.web.dto.KpiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin KPI telemetry endpoint. Thin: delegates all aggregation to {@link KpiService}.
 */
@RestController
@RequestMapping("/api/admin")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/kpis")
    public KpiResponse kpis(@RequestParam(required = false) String hotelId) {
        return kpiService.compute(hotelId);
    }
}
