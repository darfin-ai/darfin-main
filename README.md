# darfin-main

DART(전자공시) 데이터와 국내 주식 시세를 기반으로 기업 분석, 모의투자, 커뮤니티, 구독/결제 기능을 제공하는 Spring Boot 백엔드 서비스입니다.

## 기술 스택

- **Language / Runtime**: Java 11
- **Framework**: Spring Boot 2.7.18 (Web, Data JPA, Security, Validation, WebSocket, Mail)
- **Database**: MariaDB (JPA/Hibernate + QueryDSL 5.0)
- **Auth**: JWT (`jjwt` 0.11.5), Kakao/Google OAuth2
- **실시간**: STOMP over WebSocket (한국투자증권 KIS API 연동)
- **결제**: 토스페이먼츠(Toss Payments) API 연동
- **기타**: OpenPDF(PDF 생성), Apache HttpClient(DART ZIP 다운로드)
- **Build**: Gradle (wrapper 포함)

## 프로젝트 구조

```
src/main/java/com/kosta/darfin/
├── DarfinMainApplication.java   # 스프링 부트 엔트리 포인트 (@EnableScheduling)
├── controller/                  # REST / WebSocket 컨트롤러
│   ├── analysis/                 # 기업 분석, 관심 기업(즐겨찾기)
│   ├── auth/                     # 로그인/회원가입, 인증
│   ├── community/                # 질문/답글 게시판
│   ├── disclosure/               # 공시 수집/검색/상세/요약
│   ├── fund/                     # 시세, 모의투자, 포트폴리오, 관심종목
│   └── payment/                  # 결제수단, 구독, 토큰, 토스 웹훅
├── dto/                         # 계층별 요청/응답 DTO
├── entity/                      # JPA 엔티티 (analysis/common/community/disclosure/fund/payment)
├── repository/                  # Spring Data JPA + QueryDSL 리포지토리
├── service/                     # 도메인별 비즈니스 로직
│   ├── analysis/                 # DART 재무데이터 수집·가공, 기업 분석 리스크 판정
│   ├── auth/                     # 인증, 메일 발송
│   ├── community/                # 커뮤니티, DART 연동
│   ├── disclosure/               # 공시 수집/검색/요약, LLM 파이프라인 연동
│   ├── fund/                     # KIS 시세·주문 API 연동, 모의투자, 포트폴리오 분석(PDF)
│   ├── oauth/                    # Kakao/Google OAuth
│   └── payment/                  # 구독/결제/토큰 과금, 토스페이먼츠 연동
├── global/
│   ├── config/                   # Security, WebSocket(STOMP), QueryDSL, App 설정
│   ├── exception/                 # 전역 예외 처리
│   ├── init/                      # 초기 데이터 적재
│   └── jwt/                       # JWT 발급/검증 필터
├── security/                    # UserDetailsService 구현
└── websocket/                   # 실시간 랭킹 브로드캐스트, 구독 트래커

src/main/resources/
└── application.properties       # 로컬 환경설정 (gitignore 처리, 저장소에는 커밋되지 않음)

src/test/java/com/kosta/darfin/  # 서비스/웹소켓 단위 테스트 (JUnit 5)
```

## 주요 기능

- **기업 분석**: DART Open API로 재무제표·공시를 수집해 지표를 산출하고, 위험도(Risk) 상태를 판정
- **공시 조회**: 공시 검색/상세/오늘의 공시, AI 요약 및 리스크 하이라이트
- **모의투자**: KIS(한국투자증권) API 연동 시세 조회, 페이퍼 트레이딩 주문/체결, 실시간 랭킹(WebSocket)
- **포트폴리오 분석**: 보유 종목 분석 및 PDF 리포트 생성
- **커뮤니티**: 종목 관련 질문/답글 게시판
- **인증**: 이메일 로그인/회원가입, Kakao/Google 소셜 로그인, JWT 기반 인증/인가
- **결제/구독**: 플랜 구독, 결제수단 관리, 토큰 과금(콘텐츠 열람권), 토스페이먼츠 웹훅 처리

## API 개요

| Prefix | 설명 |
|---|---|
| `/api/v1/auth` | 로그인, 회원가입, 아이디/비밀번호 찾기, 토큰 재발급 |
| `/api/v1/users/me` | 내 프로필 관리 |
| `/api/v1/companies`, `/api/v1/companies/starred` | 기업 목록/상세, 관심 기업 |
| `/api/analysis`, `/api/analysis/portfolio` | 공시 분석, 포트폴리오 분석 |
| `/api/summary` | 공시 요약 |
| `/api/community`, `/api/community/stocks` | 커뮤니티 게시판, 종목 검색 |
| `/funds/market`, `/funds/market-overview` | 시세, 시장 개요 |
| `/funds/stocks`, `/funds/ranks` | 종목 정보, 랭킹 |
| `/funds/watchlist` | 관심종목 (인증 필요) |
| `/funds/paper`, `/funds/paper-trading` | 모의투자 (인증 필요) |
| `/api/v1/subscriptions`, `/api/v1/billing/*`, `/api/v1/tokens` | 구독/결제/토큰 |
| `/api/v1/webhooks/toss` | 토스페이먼츠 웹훅 |
| `/ws/stomp` | STOMP WebSocket 엔드포인트 (실시간 시세/랭킹) |

## 시작하기

### 요구사항

- JDK 11
- MariaDB (로컬 실행 시)

### 환경설정

`src/main/resources/application.properties`는 gitignore 처리되어 저장소에 포함되지 않습니다. 아래 항목을 직접 채운 파일을 로컬에 생성해야 합니다.

- `spring.datasource.*` (MariaDB 접속 정보)
- `jwt.secret`, `jwt.access-token-expiration`, `jwt.refresh-token-expiration`
- `spring.mail.*` (SMTP 인증 메일 발송)
- `oauth2.kakao.*`, `oauth2.google.*` (소셜 로그인)
- `dart.api.key`, `dart.api.base-url` (DART Open API)
- `kis.real.*`, `kis.mock.*` (한국투자증권 API)
- `toss.payments.*` (토스페이먼츠 API)

### 빌드 & 실행

```bash
./gradlew build
./gradlew bootRun
```

기본 포트는 `8080`이며 컨텍스트 경로는 `/`입니다.

### 테스트

```bash
./gradlew test
```
