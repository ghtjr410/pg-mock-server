package com.pgmock.nice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pgmock.nice", "com.pgmock.common"})
public class MockNiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockNiceApplication.class, args);
    }
}
