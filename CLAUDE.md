# pg-mock-server

PG사 Mock 서버 — Resilience4j 등 장애 대응 테스트용.

## 빌드 & 테스트

```bash
./gradlew build                  # 전체 빌드 + 테스트
./gradlew :mock-toss:test        # 모듈별 테스트
./gradlew :common:test
```

## 프로젝트 구조

### 모듈

| 모듈 | 포트 | 설명 |
|------|------|------|
| `common` | - | 카오스 시뮬레이션 엔진 (라이브러리) |
| `mock-toss` | 8090 | 토스페이먼츠 결제 API Mock |
| `mock-nice` | 8091 | 나이스페이먼츠 결제 API Mock |

### 문서

```
docs/{pg}/spec/  ← 공식 PG API 스펙
docs/{pg}/mock/  ← Mock 구현 규격·차이점
```
