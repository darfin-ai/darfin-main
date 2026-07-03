package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.dto.disclosure.DisclosureSearchCondition;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResult;
import com.kosta.darfin.entity.disclosure.QAiSummaryResult;
import com.kosta.darfin.entity.disclosure.QDisclosure;
import com.kosta.darfin.entity.disclosure.QDisclosureType;
import com.kosta.darfin.entity.common.QStock;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;


@Repository
public class DisclosureSearchRepositoryImpl implements DisclosureSearchRepository {

    private final JPAQueryFactory queryFactory;

    public DisclosureSearchRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<DisclosureSearchResult> search(DisclosureSearchCondition cond, Pageable pageable) {
        QDisclosure d = QDisclosure.disclosure;
        QStock s = QStock.stock;
        QDisclosureType t = QDisclosureType.disclosureType;
        QAiSummaryResult r = QAiSummaryResult.aiSummaryResult;

        List<DisclosureSearchResult> content = queryFactory
                .select(Projections.constructor(
                        DisclosureSearchResult.class,
                        d.rceptNo, d.title, d.filedAt,
                        t.typeCode, t.typeName,
                        s.companyName, d.filerName,
                        r.riskLabel, r.riskTier
                ))
                .from(d)
                .join(d.stock, s)
                .join(d.disclosureType, t)
                .leftJoin(r).on(d.rceptNo.eq(r.rceptNo))
                .where(
                        companyNameOrStockCodeMatches(s, cond.getCompanyName()),
                        typeCodeIn(t, cond),
                        filedAtBetween(d, cond)
                )
                .orderBy(resolveOrder(cond, d, t, r))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(d.count())
                .from(d)
                .join(d.stock, s)
                .join(d.disclosureType, t)
                .where(
                        companyNameOrStockCodeMatches(s, cond.getCompanyName()),
                        typeCodeIn(t, cond),
                        filedAtBetween(d, cond)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public boolean existsCollected(String companyNameOrCode, LocalDate dateFrom, LocalDate dateTo) {
        QDisclosure d = QDisclosure.disclosure;
        QStock s = QStock.stock;

        BooleanExpression dateCondition = (dateFrom == null || dateTo == null)
                ? null
                : d.filedAt.between(dateFrom, dateTo);

        Integer one = queryFactory
                .selectOne()
                .from(d)
                .join(d.stock, s)
                .where(
                        companyNameOrStockCodeMatches(s, companyNameOrCode),
                        dateCondition
                )
                .fetchFirst();

        return one != null;
    }

    /** "기업명 또는 종목코드" 입력 한 칸으로 두 컬럼(stock.companyName, stock.stockCode) 모두 매칭 */
    private BooleanExpression companyNameOrStockCodeMatches(QStock s, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null; // null이면 where()에서 자동으로 무시됨(QueryDSL 동적쿼리의 핵심)
        }
        return s.companyName.contains(keyword).or(s.stockCode.eq(keyword));
    }

    /**
     * 전체보기(빈 리스트)면 조건을 걸지 않는다.
     * 선택된 값이 있으면 disclosure_type.type_code 또는 disclosure_group.group_code 둘 중
     * 하나라도 일치하면 매칭한다 — 화면이 개별 공시유형(type_code)이 아니라
     * 대분류 칩(group_code, 예: "PERIODIC")으로 필터링을 보내는 경우까지 함께 지원한다.
     */
    private BooleanExpression typeCodeIn(QDisclosureType t, DisclosureSearchCondition cond) {
        if (cond.isAllTypesSelected()) {
            return null;
        }
        return t.typeCode.in(cond.getTypeCodes()).or(t.disclosureGroup.groupCode.in(cond.getTypeCodes()));
    }

    private BooleanExpression filedAtBetween(QDisclosure d, DisclosureSearchCondition cond) {
        if (cond.getDateFrom() == null || cond.getDateTo() == null) {
            return null;
        }
        return d.filedAt.between(cond.getDateFrom(), cond.getDateTo());
    }

    /**
     * 정렬 — sortKey가 "risk"일 때는 ai_summary_result.risk_tier 기준으로 정렬해야 한다.
     * risk_tier는 항상 1(안전)~5(위험)로 정규화되어 있으므로, "위험도 높은순"은
     * risk_tier 내림차순과 정확히 일치한다(risk_label 텍스트로 정렬하면 역설축에서 틀어짐).
     */
    private OrderSpecifier<?> resolveOrder(DisclosureSearchCondition cond, QDisclosure d, QDisclosureType t, QAiSummaryResult r) {
        boolean asc = "asc".equalsIgnoreCase(cond.getSortDirection());
        String key = cond.getSortKey();

        if (key == null) {
            return d.filedAt.desc(); // 기본값: 최신순
        }

        switch (key) {
            case "date":
                return asc ? d.filedAt.asc() : d.filedAt.desc();
            case "type":
                return asc ? t.typeCode.asc() : t.typeCode.desc();
            case "title":
                return asc ? d.title.asc() : d.title.desc();
            case "risk":
                return asc ? r.riskTier.asc() : r.riskTier.desc();
            default:
                return d.filedAt.desc();
        }
    }
}
