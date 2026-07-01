<div id="top"></div>

<!-- PROJECT LOGO -->
<br />
<div align="center">
    <img src="./docs/images/logo.svg" alt="Logo" width="80" height="80">

  <h3 align="center">clist</h3>

  <p align="center">
    Markdown 기반 AI CLI 학습 서비스 — Spring Boot 서버
  </p>

</div>
<br />

Markdown 문서를 기반으로 개인화된 학습 경험을 제공하는 AI 학습 보조 서비스, **clist**의 메인 백엔드 서버입니다.
사용자는 CLI를 통해 학습하고 싶은 내용을 Markdown 문서로 등록하고, 이를 바탕으로 AI가 생성한 퀴즈를 풀거나
AI와 피드백 세션을 진행할 수 있으며, 학습 결과는 이력으로 자동 정리됩니다.

이 저장소는 clist 서비스를 구성하는 세 컴포넌트(CLI / Spring Boot 서버 / Node AI 서버) 중 **핵심 API 서버**를 담당합니다.

<br>

## 화면

### CLI 메인

`clist --help`로 회원가입, 로그인, MD 문서 관리, 퀴즈, 피드백, 학습 이력 조회 등 전체 커맨드를 확인할 수 있습니다.

### 학습 이력 / 퀴즈 채점

퀴즈 정답 여부를 기록하고, AI가 채점 결과와 함께 근거가 되는 요약(예: "layout.tsx는 여러 페이지에 공통 적용되는 UI와 상태를 유지하는 '공통 레이아웃 컴포넌트'입니다")을 함께 제공합니다.

<br>

## 기술 스택

| 구분            | 기술                                            |
| --------------- | ----------------------------------------------- |
| 언어/프레임워크 | Java, Spring Boot                               |
| 인증/보안       | Spring Security, JWT                            |
| AI              | OpenAI API (직접 호출 + Node AI 서버 경유)      |
| 배포            | Docker, Render                                  |
| 연동 서비스     | clist-cli(JS 클라이언트), Node AI 서버(Express) |

<br>

## 시스템 구성

clist는 하나의 서비스가 세 부분으로 분리되어 있습니다.

```
clist-cli (JS)  ──HTTP──▶  clist 서버 (Spring Boot, 본 저장소)  ──▶  OpenAI API (직접 호출)
                                     │
                                     └────HTTP────▶  Node AI 서버 (Express) ──▶ OpenAI API
```

- **clist-cli**: 사용자가 실제로 사용하는 커맨드라인 도구. 인증 토큰을 저장하고, 서버 API를 호출합니다.
- **clist 서버 (본 저장소)**: 인증, 학습 자료(MD), 퀴즈, 피드백, 학습 이력 등 모든 도메인 로직과 데이터 저장을 담당하는 메인 API 서버입니다.
- **Node AI 서버**: OpenAI 호출 로직만 별도로 분리한 서버. clist 서버는 `AIClientRouter`를 통해 OpenAI를 직접 호출할지, Node AI 서버를 경유할지 선택할 수 있어, AI 처리 로직에 문제가 생겨도 메인 서버와 독립적으로 수정·배포할 수 있게 했습니다.

<br>

## 핵심 기능

### 1. 인증 (`auth`)

회원가입/로그인 API를 제공하고, `TokenProvider` + `TokenAuthorizationFilter`로 JWT 기반 인증을 처리합니다. CLI는 로그인 후 발급받은 토큰을 로컬에 저장해 이후 요청에 사용합니다.

### 2. Markdown 학습 자료 관리 (`md`)

프레임워크, 라이브러리, 개념 정리 내용을 Markdown 문서(`MdDocument`)로 등록·조회합니다. 이후 퀴즈 생성과 피드백 세션의 기반 자료로 사용됩니다.

### 3. AI 기반 퀴즈 (`quiz`)

등록된 Markdown 문서를 바탕으로 `QuizAiService`가 퀴즈(`QuizQuestion`)를 생성하고, 세션 단위(`QuizSession`)로 관리합니다. 사용자가 CLI로 답변을 제출하면 정답 여부와 채점 근거를 함께 돌려줍니다.

### 4. AI 피드백 세션 (`feedback`)

사용자가 질문을 입력하면 `FeedbackAiService`가 응답하는 대화형 피드백 세션(`FeedbackSession` / `FeedbackMessage`)을 제공합니다. 단순 Q&A가 아니라, 아래 Tool Calling 구조를 통해 사용자의 실제 학습 맥락(문서·퀴즈·이력)을 참고해 답변합니다.

### 5. 학습 이력 (`history`)

퀴즈 결과 등을 바탕으로 `HistoryAiService`가 학습 이력(`LearningHistory`)을 정리해, CLI의 `clist list` 명령으로 지금까지의 학습 흐름을 확인할 수 있게 합니다.

<br>

## AI Tool Calling 구조

`global/ai` 패키지는 단순 프롬프트 호출을 넘어, AI가 필요할 때 서버 데이터를 조회하도록 Tool Calling 패턴으로 설계되어 있습니다.

- `ToolRegistry` / `ToolHandler`: 사용 가능한 도구를 등록하고 실행하는 공통 인터페이스
- `GetMdContentHandler`: 등록된 Markdown 문서 내용을 조회
- `GetQuizHistoryHandler`: 이전 퀴즈 기록을 조회
- `GetFeedbackMessagesHandler`: 이전 피드백 대화 내역을 조회
- `GetLearningHistoryHandler`: 학습 이력을 조회

AI(`AIService`)가 답변을 생성하는 과정에서 이 핸들러들을 호출해 필요한 컨텍스트를 가져오므로, 매번 전체 문서를 프롬프트에 밀어 넣지 않고도 사용자의 학습 맥락에 맞는 퀴즈/피드백/이력 요약을 만들 수 있습니다.
<br>
