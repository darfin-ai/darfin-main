-- disclosure_type 10개 대분류 시드.
--
-- 문제: DartCollectService.collect()는 disclosure_type에 등록되지 않은 type_code를
-- FK 위반 방지를 위해 조용히 건너뛴다(DartCollectService.java 127-131행). dart_collector.py의
-- PBLNTF_TY_TO_TYPE_CODE는 DART pblntf_ty(A~J) 10개를 아래 10개 type_code로 매핑하는데,
-- 이 중 OTHER(기타공시)만 disclosure_type에 등록되어 있어서 나머지 9개 대분류 공시가
-- 전부 스킵되고 "기타공시"만 검색 결과에 남는 증상이 발생했다.
--
-- BIZ_REPORT(정기공시)는 registry.py의 LLM 파이프라인과도 연결되는 type_code라 이미
-- 등록되어 있을 가능성이 높지만, 안전하게 ON DUPLICATE KEY UPDATE로 같이 넣는다.
-- 나머지 8개는 아직 세부 보고서별 파이프라인이 없는 "대분류 통짜" 코드이므로
-- group_code와 동일한 값을 type_code로 사용한다(추후 세분화 시 마이그레이션 필요).

INSERT INTO disclosure_type (type_code, group_code, type_name, pblntf_ty, risk_scale_code)
VALUES
    ('BIZ_REPORT',   'PERIODIC',    '정기공시',     'A', 'STANDARD'),
    ('MAJOR_EVENT',  'MAJOR_EVENT', '주요사항보고', 'B', 'STANDARD'),
    ('ISSUANCE',     'ISSUANCE',    '발행공시',     'C', 'STANDARD'),
    ('EQUITY',       'EQUITY',      '지분공시',     'D', 'STANDARD'),
    ('OTHER',        'OTHER',       '기타공시',     'E', 'STANDARD'),
    ('AUDIT',        'AUDIT',       '외부감사관련', 'F', 'STANDARD'),
    ('FUND',         'FUND',        '펀드공시',     'G', 'STANDARD'),
    ('ABS',          'ABS',         '자산유동화',   'H', 'STANDARD'),
    ('EXCHANGE',     'EXCHANGE',    '거래소공시',   'I', 'STANDARD'),
    ('FTC',          'FTC',         '공정위공시',   'J', 'STANDARD')
ON DUPLICATE KEY UPDATE
    group_code = VALUES(group_code),
    type_name = VALUES(type_name),
    pblntf_ty = VALUES(pblntf_ty);
