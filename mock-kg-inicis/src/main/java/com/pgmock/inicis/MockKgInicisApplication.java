package com.pgmock.inicis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pgmock.inicis", "com.pgmock.common"})
public class MockKgInicisApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockKgInicisApplication.class, args);
    }
}
