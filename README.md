# 소설 생성기

Claude API를 활용해 장르, 인물, 배경, 사건을 입력하면 **전체 개요**와 **소설 도입부**를 자동으로 생성해주는 웹 애플리케이션입니다.

---

## 주요 기능

- **장르 선택** — 판타지, 로맨스, 무협, SF, 스릴러, 회귀물 등 14가지 장르 지원
- **실시간 스트리밍** — Claude API 응답을 SSE로 스트리밍하여 생성되는 텍스트를 즉시 확인
- **탭 분리 출력** — 전체 개요 / 소설 앞부분을 탭으로 구분하여 표시
- **분량 조절** — 슬라이더로 도입부 분량을 500자 ~ 3000자 사이에서 설정
- **예상 비용 표시** — 입력 내용과 분량 기반으로 API 호출 비용을 실시간 추정
- **마크다운 렌더링** — 생성된 텍스트를 marked.js로 렌더링하여 가독성 있게 표시

---

## 기술 스택

| 분류 | 사용 기술 |
|------|-----------|
| Backend | Java 17, Spring Boot 3.4.4, Gradle |
| Frontend | Vanilla JS, HTML/CSS |
| AI | Anthropic Claude API (claude-opus-4-6) |
| 스트리밍 | Spring SseEmitter + Fetch API ReadableStream |

---

## 시작하기

### 사전 준비

- Java 17 이상
- [Anthropic API 키](https://console.anthropic.com/)

### 설정

`src/main/resources/application.properties.example`을 복사하여 `application.properties`를 생성하고 API 키를 입력합니다.

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

```properties
anthropic.api-key=YOUR_API_KEY_HERE
anthropic.model=claude-opus-4-6
anthropic.max-tokens=16000
```

### 실행

```bash
./gradlew bootRun
```

서버 실행 후 브라우저에서 `http://localhost:8080` 접속

---

## 사용 방법

1. **장르** 선택 (필수)
2. **주요 배경** 입력 — 시대, 장소 등 (선택)
3. **등장인물** 입력 — 이름, 나이, 성격, 관계 등 (필수)
4. **핵심 사건 / 플롯 포인트** 입력 (필수)
5. **추가 조건** 입력 — 시점, 분위기, 특별 요청 등 (선택)
6. **앞부분 분량** 슬라이더 조절
7. **소설 생성하기** 버튼 클릭

---

## 주의사항

- `application.properties`에는 API 키가 포함되어 있으므로 **절대 Git에 커밋하지 마세요.** (`.gitignore`에 등록되어 있습니다.)
- API 사용 비용은 Anthropic 요금제에 따라 부과됩니다.
