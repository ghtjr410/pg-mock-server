package com.example.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
@Testcontainers
public abstract class ExampleTestBase {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().getParent();

    @Container
    protected static GenericContainer<?> mockToss = new GenericContainer<>(
            createImage())
            .withExposedPorts(8090)
            .waitingFor(Wait.forHttp("/chaos/mode").forStatusCode(200));

    private static ImageFromDockerfile createImage() {
        // mock-toss bootJar 경로 찾기 (*-SNAPSHOT.jar, plain 제외)
        Path libsDir = PROJECT_ROOT.resolve("mock-toss/build/libs");
        Path bootJar;
        try {
            bootJar = Files.list(libsDir)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> !p.toString().contains("-plain"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "mock-toss bootJar not found. Run ./gradlew :mock-toss:bootJar first."));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + libsDir, e);
        }

        return new ImageFromDockerfile("mock-toss-test", false)
                .withDockerfileFromBuilder(builder -> builder
                        .from("eclipse-temurin:21-jre-alpine")
                        .workDir("/app")
                        .copy("app.jar", "app.jar")
                        .expose(8090)
                        .entryPoint("java", "-jar", "app.jar")
                        .build())
                .withFileFromPath("app.jar", bootJar);
    }

    @Autowired
    protected PaymentClient paymentClient;

    @BeforeEach
    void printTestHeader(TestInfo testInfo) {
        String name = testInfo.getDisplayName().replace("_", " ");
        System.out.printf("%n═══ %s ═══%n", name);
    }

    @BeforeEach
    void resetMockServer() {
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 5000);
        paymentClient.resetTest();
    }
}
