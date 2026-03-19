## 테스트

- **JUnit 5** + **MockMvc**, `@SpringBootTest`
- 모듈별 테스트 실행:
  ```bash
  ./gradlew :mock-toss:test
  ./gradlew :mock-nice:test
  ./gradlew :common:test
  ```
