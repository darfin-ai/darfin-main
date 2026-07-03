package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.DartCollectRequestDto;
import com.kosta.darfin.dto.disclosure.DartCollectResponseDto;
import com.kosta.darfin.dto.disclosure.DisclosureSearchCondition;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResponse;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResult;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class DisclosureSearchService {

    private static final Logger log = LoggerFactory.getLogger(DisclosureSearchService.class);
    private static final DateTimeFormatter COMPACT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DisclosureRepository disclosureRepository;
    private final DartCollectService dartCollectService;

    public DisclosureSearchService(DisclosureRepository disclosureRepository,
                                    DartCollectService dartCollectService) {
        this.disclosureRepository = disclosureRepository;
        this.dartCollectService = dartCollectService;
    }

    public Page<DisclosureSearchResult> search(DisclosureSearchCondition condition, Pageable pageable) {
        return disclosureRepository.search(condition, pageable);
    }

    
    public DisclosureSearchResponse searchWithAutoCollect(DisclosureSearchCondition condition, Pageable pageable) {
        boolean canCollect = condition.getCompanyName() != null && !condition.getCompanyName().isBlank()
                && condition.getDateFrom() != null && condition.getDateTo() != null;

        DartCollectResponseDto collectResult = null;

        
        if (canCollect) {
            try {
                boolean alreadyCollected = disclosureRepository.existsCollected(
                        condition.getCompanyName(), condition.getDateFrom(), condition.getDateTo());

                if (!alreadyCollected) {
                    DartCollectRequestDto req = new DartCollectRequestDto();
                    req.setCompanyName(condition.getCompanyName());
                    req.setBgnDe(condition.getDateFrom().format(COMPACT_DATE_FMT));
                    req.setEndDe(condition.getDateTo().format(COMPACT_DATE_FMT));

                    collectResult = dartCollectService.collect(req);
                    if (!collectResult.isSuccess()) {
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
