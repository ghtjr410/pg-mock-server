package com.pgmock.toss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pgmock.toss", "com.pgmock.common"})
public class MockTossApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockTossApplication.class, args);
    }
}
