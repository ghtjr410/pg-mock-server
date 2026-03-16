package com.pgmock.common.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos")
public class ChaosProperties {

    private ChaosMode mode = ChaosMode.NORMAL;
    private int slowMinMs = 3000;
    private int slowMaxMs = 10000;
    private int partialFailureRate = 50;

    public ChaosMode getMode() {
        return mode;
    }

    public void setMode(ChaosMode mode) {
        this.mode = mode;
    }

    public int getSlowMinMs() {
        return slowMinMs;
    }

    public void setSlowMinMs(int slowMinMs) {
        this.slowMinMs = slowMinMs;
    }

    public int getSlowMaxMs() {
        return slowMaxMs;
    }

    public void setSlowMaxMs(int slowMaxMs) {
        this.slowMaxMs = slowMaxMs;
    }

    public int getPartialFailureRate() {
        return partialFailureRate;
    }

    public void setPartialFailureRate(int partialFailureRate) {
        this.partialFailureRate = partialFailureRate;
    }
}
