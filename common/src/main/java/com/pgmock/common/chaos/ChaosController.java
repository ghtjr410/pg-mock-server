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
        return Map.of(
                "mode", properties.getMode(),
                "slowMinMs", properties.getSlowMinMs(),
                "slowMaxMs", properties.getSlowMaxMs(),
                "partialFailureRate", properties.getPartialFailureRate()
        );
    }

    @PutMapping("/mode")
    public Map<String, Object> setMode(@RequestParam ChaosMode mode) {
        properties.setMode(mode);
        return Map.of("mode", properties.getMode());
    }
}
