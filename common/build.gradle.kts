// common 모듈은 독립 실행 앱이 아니므로 bootJar 비활성화
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
