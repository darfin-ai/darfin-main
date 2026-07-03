-- =====================================================================
-- Darfin 전체 서비스 DB 스키마
-- HeidiSQL 사용 안내: 새 쿼리탭에 전체 붙여넣기 후 F9(전체 실행)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS darfin;
USE darfin;

SET default_storage_engine = InnoDB;
SET NAMES utf8mb4;


-- =====================================================================
-- 1. 공용
-- =====================================================================

-- 사용자 기본 정보, 계정 상태 관리
CREATE TABLE users (
id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
email               VARCHAR(100) NOT NULL,
password            VARCHAR(255) NULL,
name                VARCHAR(50)  NULL,
phone               VARCHAR(255) NOT NULL,
nickname            VARCHAR(50)  NOT NULL,
profile_image       MEDIUMTEXT   NULL,
provider            VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
provider_user_id    VARCHAR(100) NULL,
status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
subscription_level  VARCHAR(20)  NOT NULL DEFAULT 'FREE',
token_balance       INT          NOT NULL DEFAULT 0,
created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
UNIQUE KEY uq_provider_email (provider, email),
UNIQUE KEY uq_provider_user (provider, provider_user_id)
);

-- 주식 종목 기본 정보 (공시열람·기업분석 공용 기준 테이블)
CREATE TABLE stock (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    market_type     VARCHAR(20)  NULL,                  -- KOSPI, KOSDAQ 등
    company_name    VARCHAR(100) NOT NULL,
    dart_corp_code  VARCHAR(20)  NOT NULL,
    stock_code      VARCHAR(20)  NULL,
    UNIQUE KEY uq_stock_dart_corp_code (dart_corp_code),
    UNIQUE KEY uq_stock_code (stock_code)
);
CREATE INDEX idx_stock_company_name ON stock(company_name);

-- 이메일 인증 번호, 만료 시간 관리
CREATE TABLE email_verifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(100) NOT NULL,
    code        VARCHAR(6)   NOT NULL,
    purpose     VARCHAR(20)  NOT NULL,                      -- SIGNUP, PASSWORD_RESET
    is_verified TINYINT(1)   NOT NULL DEFAULT 0,
    expired_at  DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_email_verifications_lookup ON email_verifications(email, code);

-- JWT 인증을 위한 리프레시 토큰 관리
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(500) NOT NULL,
    ip_address  VARCHAR(45)  NULL,                          -- 보안 검증용
    user_agent  VARCHAR(255) NULL,                          -- 보안 검증용
    expired_at  DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_refresh_token (token),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


-- =====================================================================
-- 2. 결제
-- =====================================================================

-- 결제 수단 관리
CREATE TABLE payment_methods (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    billing_key     VARCHAR(255) NOT NULL,                  -- PG사 결제 토큰, 암호화 저장 권장
    card_company    VARCHAR(50)  NOT NULL,
    masked_card_num VARCHAR(20)  NOT NULL,
    is_default      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 구독 상태 관리
CREATE TABLE subscriptions (
    id                BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT     NOT NULL,
    plan_name         VARCHAR(20) NOT NULL,                  -- PRO, MAX 등
    status            VARCHAR(20) NOT NULL,                  -- ACTIVE, CANCELED
    next_payment_date DATE       NULL,                        -- 배치 실행 기준
    created_at        DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_subscriptions_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 결제 이력
CREATE TABLE payments (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    payment_method_id   BIGINT       NULL,
    amount              INT          NOT NULL,
    status              VARCHAR(20)  NOT NULL,                -- PAID, FAILED
    merchant_uid         VARCHAR(100) NOT NULL,
    pg_tid               VARCHAR(100) NULL,                    -- 환불 시 필수
    paid_at              DATETIME     NULL,
    UNIQUE KEY uq_payments_merchant_uid (merchant_uid),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
);


-- =====================================================================
-- 3. 공시 원문 (공시 통합검색 + 공시 상세 페이지)
--    파이프라인 흐름:
--    disclosure(공시원문) → [압축, 코드 내부 처리] → 요약/분석 호출
--      → 요약결과 1행: ai_summary_result
--      → 분석결과 N행: ai_analysis_item (해설+위험도+발췌좌표 한 행)
-- =====================================================================

-- 공시 대분류 (검색화면 상단 탭과 1:1 대응)
CREATE TABLE disclosure_group (
    group_code  VARCHAR(20) NOT NULL PRIMARY KEY,            -- PERIODIC, MAJOR_EVENT, ISSUANCE, EQUITY,
                                                              -- OTHER, AUDIT, FUND, ABS, EXCHANGE, FTC
    group_name  VARCHAR(50) NOT NULL                         -- 검색화면 탭 라벨
);

-- 공시유형 (95개+ 개별 문서종류)
CREATE TABLE disclosure_type (
    type_code         VARCHAR(40)  NOT NULL PRIMARY KEY,     -- 예: 'RIGHTS_OFFERING'
    group_code        VARCHAR(20)  NOT NULL,
    type_name         VARCHAR(100) NOT NULL,                 -- 검색결과·상세화면 표시명
    pblntf_ty         VARCHAR(2)   NULL,                     -- DART Open API 매핑용
    pblntf_detail_ty  VARCHAR(4)   NULL,                     -- DART Open API 매핑용
    parsing_strategy  VARCHAR(20)  NULL,                     -- narrative / tabular_itemized / tabular_timeseries
    urgency_tier      VARCHAR(10)  NULL,                      -- 분석 우선순위 참고용
    body_format       VARCHAR(20)  NULL,                      -- xml_inline / attachment_based
    risk_scale_code   VARCHAR(20)  NOT NULL DEFAULT 'STANDARD', -- risk_scale 조회 시 이 값으로 매칭(직접 FK 없음)
    FOREIGN KEY (group_code) REFERENCES disclosure_group(group_code)
);
CREATE INDEX idx_dtype_group ON disclosure_type(group_code);

-- 위험축 어휘 사전 (화면 배지 라벨/색상/순서 매핑용)
CREATE TABLE risk_scale (
    risk_scale_code VARCHAR(20) NOT NULL,                    -- STANDARD, GOVERNANCE, CONFIDENCE ...
    risk_label      VARCHAR(30) NOT NULL,                     -- 보고서 원본 그대로: Critical, Warning 등
    risk_tier       TINYINT     NOT NULL,                     -- 1(안전) ~ 5(최고위험)
    display_order   TINYINT     NOT NULL,                     -- 같은 scale 내 정렬 순서
    PRIMARY KEY (risk_scale_code, risk_label)
);

-- 공시 원문 (검색결과 리스트 + 원문 다운로드의 기준 행)
CREATE TABLE disclosure (
    rcept_no        VARCHAR(14)  NOT NULL PRIMARY KEY,        -- DART 접수번호
    dart_corp_code  VARCHAR(20)  NOT NULL,
    type_code       VARCHAR(40)  NOT NULL,
    reporter_name   VARCHAR(100) NULL,                        -- 회사 자신이 보고자면 NULL
    title           VARCHAR(300) NOT NULL,                    -- 검색결과 "공시제목"
    filer_name      VARCHAR(100) NOT NULL,                    -- 검색결과 "제출인"
    filed_at        DATE         NOT NULL,                     -- 검색결과 "공시일자"
    rcept_dt        DATETIME     NULL,
    raw_zip_path    VARCHAR(300) NULL,                         -- 원문 다운로드 경로
    raw_text_path   VARCHAR(300) NULL,                         -- 압축 단계 입력 소스
    char_count      INT          NULL,                          -- TEXT_TOO_SHORT 검증 근거
    fiscal_year     VARCHAR(10)  NULL,
    missing_targets JSON         NULL,                          -- 분석에서 못 찾은 항목 코드. 예: ["RO5","TOA4"]
    dropped_count   SMALLINT     NOT NULL DEFAULT 0,            -- targetKey 검증 실패로 버려진 항목 수
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dart_corp_code) REFERENCES stock(dart_corp_code),
    FOREIGN KEY (type_code) REFERENCES disclosure_type(type_code)
);
CREATE INDEX idx_disc_corp_date ON disclosure(dart_corp_code, filed_at DESC);
CREATE INDEX idx_disc_type_date ON disclosure(type_code, filed_at DESC);
CREATE INDEX idx_disc_title ON disclosure(title);

-- 요약 파이프라인 결과 (상세화면 "AI 요약" 탭, 1 disclosure : 1 row)
CREATE TABLE ai_summary_result (
    rcept_no         VARCHAR(14)  NOT NULL PRIMARY KEY,
    summary_text     VARCHAR(200) NOT NULL,                   -- 1문장 핵심요약
    investor_comment TEXT         NOT NULL,                    -- 투자자 해설(3문장 이내)
    risk_label       VARCHAR(30)  NOT NULL,                     -- overallRisk 원본
    risk_tier        TINYINT      NOT NULL,                      -- risk_scale 조회결과 비정규화 저장
    extra            JSON         NULL,                          -- 보고서별 가변 보조필드
    compressed_context_chars INT  NULL,
    model_name       VARCHAR(40)  NOT NULL DEFAULT 'gemini-2.5-flash-lite',
    tokens_in        INT          NULL,
    tokens_out       INT          NULL,
    cost_usd         DECIMAL(10,6) NULL,
    latency_ms       INT          NULL,
    cache_hit        BOOLEAN      NOT NULL DEFAULT FALSE,
    error_code       VARCHAR(40)  NULL,
    error_message    VARCHAR(300) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rcept_no) REFERENCES disclosure(rcept_no)
);

-- 분석 파이프라인 결과 (상세화면 "심층 분석" 탭 + 원문 하이라이트, 1 disclosure : N rows)
CREATE TABLE ai_analysis_item (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rcept_no         VARCHAR(14)  NOT NULL,
    item_no          TINYINT      NOT NULL,                    -- 같은 보고서 내 항목 순번(0~6)
    category         VARCHAR(40)  NOT NULL,                     -- analysisCategory
    target_text      VARCHAR(500) NOT NULL,                      -- 원문 100% 일치 발췌(하이라이트 대상)
    char_start       INT          NULL,
    char_end         INT          NULL,
    material_impact  TEXT         NOT NULL,                       -- 항목 해설(1~3문장)
    risk_label       VARCHAR(30)  NOT NULL,
    risk_tier        TINYINT      NOT NULL,
    extra            JSON         NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rcept_no) REFERENCES disclosure(rcept_no)
);
CREATE INDEX idx_item_rcept ON ai_analysis_item(rcept_no, item_no);
CREATE INDEX idx_item_category ON ai_analysis_item(category);
CREATE INDEX idx_item_rcept_pos ON ai_analysis_item(rcept_no, char_start, char_end);


-- =====================================================================
-- 4. 커뮤니티
-- =====================================================================

-- 게시글
CREATE TABLE community_posts (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_id     BIGINT       NOT NULL,
    stock_id      BIGINT       NULL,                           -- stock.id 참조(종목 토론방용)
    title         VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    views         INT          NOT NULL DEFAULT 0,
    is_resolved   TINYINT(1)   NOT NULL DEFAULT 0,
    reward_tokens INT          NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (author_id) REFERENCES users(id),
    FOREIGN KEY (stock_id) REFERENCES stock(id)
);

-- 댓글/대댓글
CREATE TABLE community_comments (
    id          BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_id   BIGINT     NOT NULL,
    post_id     BIGINT     NOT NULL,
    parent_id   BIGINT     NULL,                               -- 대댓글용 자기참조
    content     TEXT       NOT NULL,
    is_adopted  TINYINT(1) NOT NULL DEFAULT 0,
    created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES community_comments(id)
);


-- =====================================================================
-- 5. 용어사전
-- 용어 마스터 데이터와 매칭 로직은 darfin-disclosure(Python) 서비스로 이전했다.
-- app/data/dictionary_terms.json 파일로 관리하며, DB 테이블은 더 이상 쓰지 않는다.
-- =====================================================================


-- =====================================================================
-- 6. 모의투자
-- =====================================================================

-- 모의투자 전용 종목 시세 마스터
-- 공용 stock 테이블과는 완전히 별개의 독립 테이블 (FK로 연결하지 않음)
-- KIS(한국투자증권) API 기준 종목코드를 자체적으로 관리
CREATE TABLE stock_info (
    stock_code      VARCHAR(20)  NOT NULL PRIMARY KEY,         -- KIS 종목코드 (stock.stock_code와 무관)
    stock_name      VARCHAR(100) NOT NULL,
    market          VARCHAR(20)  NULL,                          -- KOSPI, KOSDAQ
    sector          VARCHAR(50)  NULL,                           -- NULL이면 리스크 계산 시 "기타"로 처리
    market_cap      BIGINT       NULL,
    per             DOUBLE       NULL,
    pbr             DOUBLE       NULL,
    dividend_yield  DOUBLE       NULL,
    updated_at      DATETIME     NULL                            -- KIS API 갱신 시각
);
-- 아래 모의투자 하위 테이블(funds 제외)은 모두 이 stock_info.stock_code를 참조함
-- (공용 stock 테이블이 아니라 이 테이블을 기준으로 함)

-- 모의 투자 자금
CREATE TABLE funds (
    fund_id        BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT   NOT NULL,
    initial_amount BIGINT   NULL,                               -- 시드머니
    cash_balance   BIGINT   NULL,
    start_date     DATE     NULL,                                -- 매매빈도 계산 분모, NULL이면 계산 오류 위험
    updated_at     DATETIME NULL,                                 -- 자동갱신(@PrePersist/@PreUpdate)
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 실시간 가격
CREATE TABLE stock_price_realtime (
    price_id         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stock_code        VARCHAR(20) NOT NULL,
    current_price      BIGINT   NULL,
    prev_close_price   BIGINT   NULL,
    change_rate        DECIMAL(10,4) NULL,
    volume              BIGINT   NULL,
    high_52w            BIGINT   NULL,                            -- 추격매수 감지 로직에 사용
    low_52w             BIGINT   NULL,
    fetched_at          DATETIME NULL,                              -- 30초 TTL 캐시 판단 기준
    FOREIGN KEY (stock_code) REFERENCES stock_info(stock_code)
);

-- 하루 단위 시세 (차트용)
CREATE TABLE stock_price_daily (
    daily_id    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stock_code  VARCHAR(20) NOT NULL,
    trade_date  DATE   NOT NULL,
    open_price  BIGINT NULL,
    high_price  BIGINT NULL,
    low_price   BIGINT NULL,
    close_price BIGINT NULL,
    volume      BIGINT NULL,
    created_at  DATETIME NULL,
    UNIQUE KEY uq_stock_daily (stock_code, trade_date),
    FOREIGN KEY (stock_code) REFERENCES stock_info(stock_code)
);

-- 지금 들고 있는 주식
CREATE TABLE holdings (
    holding_id    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    stock_code    VARCHAR(20) NOT NULL,
    quantity      INT    NULL,
    avg_buy_price BIGINT NULL,
    updated_at    DATETIME NULL,
    UNIQUE KEY uq_holdings_user_stock (user_id, stock_code),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (stock_code) REFERENCES stock_info(stock_code)
);

-- 사고 판 기록
CREATE TABLE trades (
    trade_id     BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    stock_code   VARCHAR(20) NOT NULL,
    type         VARCHAR(10) NULL,                              -- BUY, SELL
    status       VARCHAR(10) NULL,                               -- PENDING, COMPLETE, FAILED (기본값 COMPLETE는 엔티티에서 처리)
    kis_order_no VARCHAR(50) NULL,                                -- KIS API 주문번호(대조용)
    quantity     INT    NULL,
    price        BIGINT NULL,
    realized_pnl BIGINT NULL,                                     -- 실현손익(매도 시만 의미있는 값)
    hold_days    INT    NULL,                                     -- 보유기간(BUY는 NULL, SELL만 값 있음)
    traded_at    DATETIME NULL,
    deleted_at   DATETIME NULL,                                    -- soft delete(자금 초기화 시에도 보존)
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (stock_code) REFERENCES stock_info(stock_code)
);

-- 충전/초기화 기록
CREATE TABLE fund_history (
    history_id       BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    related_trade_id BIGINT NULL,                                 -- 매수/매도로 인한 변동이면 연결
    type             VARCHAR(10) NOT NULL,                         -- CHARGE(충전), RESET(초기화)
    amount           BIGINT NULL,
    created_at       DATETIME NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (related_trade_id) REFERENCES trades(trade_id)
);

-- 관심 종목
CREATE TABLE watchlist (
    watch_id   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    created_at DATETIME NULL,
    UNIQUE KEY uq_watchlist_user_stock (user_id, stock_code),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (stock_code) REFERENCES stock_info(stock_code)
);

-- AI가 만든 분석 리포트
CREATE TABLE ai_reports (
    report_id      BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT   NOT NULL,
    health_score   JSON     NULL,                                -- 분산도/리스크/수익률/매매습관 점수
    tendency_label VARCHAR(50) NULL,                              -- 투자성향 레이블(8유형 중 1개)
    report_content TEXT     NULL,                                  -- Gemini 생성 리포트 원문
    share_token    VARCHAR(64) NULL,                               -- 공유링크용 UUID(엔티티에서 자동생성)
    created_at     DATETIME NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 매매 습관 통계 (유저당 정확히 1개)
CREATE TABLE user_trading_stats (
    stat_id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    monthly_trade_freq DOUBLE NULL,
    avg_hold_days      DOUBLE NULL,
    stop_loss_rate     DOUBLE NULL,
    take_profit_rate   DOUBLE NULL,
    chase_buy_count    INT    NULL,                               -- 추격매수 감지 건수
    updated_at         DATETIME NULL,
    UNIQUE KEY uq_user_trading_stats_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);


-- =====================================================================
-- 7. 기업분석 (DART 원문 파이프라인)
-- =====================================================================

CREATE TABLE companies (
    corp_code   VARCHAR(8)   NOT NULL PRIMARY KEY,  -- DART 기업코드
    sector      VARCHAR(100) NULL,                  -- KRX 섹터 (stock에 없음)
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (corp_code) REFERENCES stock(dart_corp_code)
);

-- DART 정기공시 접수정보 및 파이프라인 처리 상태
CREATE TABLE filings (
    rcept_no        CHAR(14)     NOT NULL PRIMARY KEY,         -- DART 공시 고유키
    corp_code       CHAR(8)      NOT NULL,
    corp_name       VARCHAR(100) NOT NULL,                      -- 공시 기준 기업명
    bsns_year       CHAR(4)      NOT NULL,                       -- 사업연도
    reprt_code      CHAR(5)      NOT NULL,                       -- 11011=사업, 11012=반기, 11013=1분기, 11014=3분기
    filed_date      CHAR(8)      NOT NULL,                       -- 접수일(YYYYMMDD)
    xml_path        VARCHAR(300) NULL,
    pipeline_status ENUM('RAW','PARSED','STORED','SUMMARIZED') NOT NULL DEFAULT 'RAW',
    created_at      DATETIME     NOT NULL,
    FOREIGN KEY (corp_code) REFERENCES companies(corp_code)
);

-- 공시 원문을 섹션별로 분할한 텍스트 청크
CREATE TABLE text_chunks (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rcept_no    CHAR(14)     NOT NULL,
    corp_code   CHAR(8)      NOT NULL,
    section_nm  VARCHAR(200) NOT NULL,                          -- 예: 사업의 개요
    content     TEXT         NOT NULL,
    chunk_index INT          NOT NULL,                           -- 섹션 내 청크 순번
    created_at  DATETIME     NOT NULL,
    FOREIGN KEY (rcept_no) REFERENCES filings(rcept_no) ON DELETE CASCADE
);

-- 공시에서 추출한 재무 계정과목별 수치 데이터
CREATE TABLE metrics (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rcept_no    CHAR(14)     NOT NULL,
    corp_code   CHAR(8)      NOT NULL,
    bsns_year   CHAR(4)      NOT NULL,
    account_nm  VARCHAR(200) NOT NULL,                          -- 예: 매출액
    amount      BIGINT       NULL,                               -- 단위: 원
    created_at  DATETIME     NOT NULL,
    FOREIGN KEY (rcept_no) REFERENCES filings(rcept_no) ON DELETE CASCADE
);

-- LLM이 생성한 공시 섹션별 요약문
CREATE TABLE llm_summaries (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rcept_no     CHAR(14)     NOT NULL,
    corp_code    CHAR(8)      NOT NULL,
    summary_type VARCHAR(50)  NOT NULL,                          -- 예: OVERVIEW, RISK
    content      TEXT         NOT NULL,
    model_used   VARCHAR(100) NOT NULL,                          -- 예: claude-sonnet-4-6
    created_at   DATETIME     NOT NULL,
    FOREIGN KEY (rcept_no) REFERENCES filings(rcept_no) ON DELETE CASCADE
);
