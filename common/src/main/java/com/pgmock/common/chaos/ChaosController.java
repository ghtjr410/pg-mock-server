package com.pgmock.common.chaos;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosProperties properties;

    public ChaosController(ChaosProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/mode")
    public Map<String, Object> getMode() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("mode", properties.getMode());
        map.put("slowMinMs", properties.getSlowMinMs());
        map.put("slowMaxMs", properties.getSlowMaxMs());
        map.put("partialFailureRate", properties.getPartialFailureRate());
        map.put("affectReadApis", properties.isAffectReadApis());
        return map;
    }

    @PutMapping("/mode")
    public Map<String, Object> setMode(
            @RequestParam ChaosMode mode,
            @RequestParam(required = false) Integer slowMinMs,
            @RequestParam(required = false) Integer slowMaxMs,
            @RequestParam(required = false) Integer partialFailureRate,
            @RequestParam(required = false) Boolean affectReadApis) {
        properties.setMode(mode);
        if (slowMinMs != null) properties.setSlowMinMs(slowMinMs);
        if (slowMaxMs != null) properties.setSlowMaxMs(slowMaxMs);
        if (partialFailureRate != null) properties.setPartialFailureRate(partialFailureRate);
        if (affectReadApis != null) properties.setAffectReadApis(affectReadApis);
        return getMode();
    }
}
