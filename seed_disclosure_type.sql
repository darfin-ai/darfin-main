-- Seed disclosure_group / disclosure_type so DartCollectService can save filings.
-- type_code values must match app/dart_collector.py PBLNTF_TY_TO_TYPE_CODE + RIGHTS_OFFERING carve-out.

INSERT INTO disclosure_group (group_code, group_name) VALUES
    ('PERIODIC', '정기공시'),
    ('MAJOR_EVENT', '주요사항보고'),
    ('ISSUANCE', '발행공시'),
    ('EQUITY', '지분공시'),
    ('OTHER', '기타공시'),
    ('AUDIT', '외부감사관련'),
    ('FUND', '펀드공시'),
    ('ABS', '자산유동화'),
    ('EXCHANGE', '거래소공시'),
    ('FTC', '공정위공시')
ON DUPLICATE KEY UPDATE group_name = VALUES(group_name);

INSERT INTO disclosure_type (type_code, group_code, type_name, risk_scale_code) VALUES
    ('BIZ_REPORT', 'PERIODIC', '사업/반기/분기보고서', 'STANDARD'),
    ('RIGHTS_OFFERING', 'MAJOR_EVENT', '유상증자결정', 'STANDARD'),
    ('MAJOR_EVENT', 'MAJOR_EVENT', '주요사항보고서', 'STANDARD'),
    ('ISSUANCE', 'ISSUANCE', '증권신고서/투자설명서', 'STANDARD'),
    ('EQUITY', 'EQUITY', '지분공시', 'STANDARD'),
    ('OTHER', 'OTHER', '기타공시', 'STANDARD'),
    ('AUDIT', 'AUDIT', '외부감사관련', 'STANDARD'),
    ('FUND', 'FUND', '펀드공시', 'STANDARD'),
    ('ABS', 'ABS', '자산유동화', 'STANDARD'),
    ('EXCHANGE', 'EXCHANGE', '거래소공시', 'STANDARD'),
    ('FTC', 'FTC', '공정위공시', 'STANDARD')
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name), group_code = VALUES(group_code);
