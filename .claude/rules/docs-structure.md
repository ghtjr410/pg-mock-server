## docs/ 디렉토리 구조 규칙

```
docs/{pg}/spec/  ← 공식 PG API 스펙 원본 (Playwright로 추출)
docs/{pg}/mock/  ← Mock 구현 규격, 공식과의 차이점
```

### 규칙

- **spec/** — 공식 API 스펙만 보관. Mock 관련 내용 금지.
- **mock/** — 공식 스펙과의 차이점만 기술.
- 파일 네이밍: `confirm.md`, `cancel.md`, `get-payment.md`, `error-codes.md` 등 API 단위로 작성.
