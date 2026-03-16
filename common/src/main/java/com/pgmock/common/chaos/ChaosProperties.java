package com.pgmock.common.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos")
public class ChaosProperties {

    private ChaosMode mode = ChaosMode.NORMAL;
    private int slowMinMs = 3000;
    private int slowMaxMs = 10000;
    private int partialFailureRate = 50;
    // GET 요청(조회 API)에도 카오스를 적용할지 여부
    // false면 GET 요청은 카오스 모드와 무관하게 항상 정상 응답
    private boolean affectReadApis = false;

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

    public boolean isAffectReadApis() {
        return affectReadApis;
    }

    public void setAffectReadApis(boolean affectReadApis) {
        this.affectReadApis = affectReadApis;
    }
}
