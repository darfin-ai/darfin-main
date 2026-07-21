package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.DartCollectRequestDto;
import com.kosta.darfin.dto.disclosure.DartCollectResponseDto;
import com.kosta.darfin.dto.disclosure.DisclosureSearchCondition;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResponse;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResult;
import com.kosta.darfin.entity.disclosure.DisclosureCollectionLog;
import com.kosta.darfin.repository.disclosure.DisclosureCollectionLogRepository;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
public class DisclosureSearchService {

    private static final Logger log = LoggerFactory.getLogger(DisclosureSearchService.class);
    private static final DateTimeFormatter COMPACT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DisclosureRepository disclosureRepository;
    private final DisclosureCollectionLogRepository collectionLogRepository;
    private final DartCollectService dartCollectService;

    public DisclosureSearchService(DisclosureRepository disclosureRepository,
                                    DisclosureCollectionLogRepository collectionLogRepository,
                                    DartCollectService dartCollectService) {
        this.disclosureRepository = disclosureRepository;
        this.collectionLogRepository = collectionLogRepository;
        this.dartCollectService = dartCollectService;
    }

    public Page<DisclosureSearchResult> search(DisclosureSearchCondition condition, Pageable pageable) {
        return disclosureRepository.search(condition, pageable);
    }

    /**
     * 사용자가 원하는 기업 + 원하는 기간의 공시를 전부 불러오는 게 목적이므로, "이미 수집됐는지"는
     * disclosure 테이블에 행이 있는지가 아니라 이 기업+기간을 실제로 DART에서 수집한 이력
     * (DisclosureCollectionLog)으로만 판단한다. disclosure 행 존재 여부로 판단하면 "오늘 올라온
     * 공시" 피드처럼 검색과 무관하게 들어온 행 하나 때문에 훨씬 넓은 검색 범위 전체가 이미
     * 수집된 것으로 오판되어, 실제로는 못 받아온 과거 공시가 조용히 누락될 수 있었다.
     */
    @Transactional
    public DisclosureSearchResponse searchWithAutoCollect(DisclosureSearchCondition condition, Pageable pageable) {
        boolean canCollect = condition.getCompanyName() != null && !condition.getCompanyName().isBlank()
                && condition.getDateFrom() != null && condition.getDateTo() != null;

        DartCollectResponseDto collectResult = null;

        if (canCollect) {
            try {
                boolean alreadyCollected = collectionLogRepository
                        .existsByCompanyNameAndBgnDeLessThanEqualAndEndDeGreaterThanEqual(
                                condition.getCompanyName(), condition.getDateFrom(), condition.getDateTo());

                if (!alreadyCollected) {
                    DartCollectRequestDto req = new DartCollectRequestDto();
                    req.setCompanyName(condition.getCompanyName());
                    req.setBgnDe(condition.getDateFrom().format(COMPACT_DATE_FMT));
                    req.setEndDe(condition.getDateTo().format(COMPACT_DATE_FMT));

                    collectResult = dartCollectService.collect(req);
                    if (collectResult.isSuccess()) {
                        collectionLogRepository.save(DisclosureCollectionLog.builder()
                                .companyName(condition.getCompanyName())
                                .bgnDe(condition.getDateFrom())
                                .endDe(condition.getDateTo())
                                .build());
                    } else {
                        log.warn("[자동수집 실패] companyName={}, message={}", condition.getCompanyName(), collectResult.getErrorMessage());
                        collectResult = null;
                    }
                }
            } catch (Exception e) {
                log.error("[자동수집 중 예외] companyName={}", condition.getCompanyName(), e);
                collectResult = null;
            }
        }

        Page<DisclosureSearchResult> results = disclosureRepository.search(condition, pageable);
        return DisclosureSearchResponse.of(collectResult, results);
    }
}
