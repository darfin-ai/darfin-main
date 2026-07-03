package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.AnalysisItemDto;
import com.kosta.darfin.dto.disclosure.DisclosureDetailResponse;
import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.disclosure.AiAnalysisItem;
import com.kosta.darfin.entity.disclosure.AiSummaryResult;
import com.kosta.darfin.entity.disclosure.Disclosure;
import com.kosta.darfin.entity.disclosure.DisclosureType;
import com.kosta.darfin.repository.disclosure.AiAnalysisItemRepository;
import com.kosta.darfin.repository.disclosure.AiSummaryResultRepository;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class DisclosureDetailService {

    private final DisclosureRepository disclosureRepository;
    private final AiSummaryResultRepository summaryRepository;
    private final AiAnalysisItemRepository analysisItemRepository;

    public DisclosureDetailService(DisclosureRepository disclosureRepository,
                                    AiSummaryResultRepository summaryRepository,
                                    AiAnalysisItemRepository analysisItemRepository) {
        this.disclosureRepository = disclosureRepository;
        this.summaryRepository = summaryRepository;
        this.analysisItemRepository = analysisItemRepository;
    }

    
    @Transactional(readOnly = true)
    public DisclosureDetailResponse getDetail(String rceptNo) {
        Disclosure disclosure = disclosureRepository.findById(rceptNo)
                .orElseThrow(() -> new IllegalArgumentException("disclosure 테이블에 없는 접수번호입니다: " + rceptNo));

        DisclosureType type = disclosure.getDisclosureType();
        Stock stock = disclosure.getStock();

        Optional<AiSummaryResult> summary = summaryRepository.findById(rceptNo);
        List<AiAnalysisItem> analysisItems = analysisItemRepository.findByDisclosure_RceptNoOrderByItemNoAsc(rceptNo);

        DisclosureDetailResponse response = new DisclosureDetailResponse();
        response.setRceptNo(disclosure.getRceptNo());
        response.setTitle(disclosure.getTitle());
        response.setFiledAt(disclosure.getFiledAt());
        response.setTypeCode(type.getTypeCode());
        response.setTypeLabel(type.getTypeName());
        response.setCompanyName(stock.getCompanyName());
        response.setCorpCode(stock.getDartCorpCode());
        response.setFilerName(disclosure.getFilerName());

        summary.ifPresent(s -> {
            response.setSummaryText(s.getSummaryText());
            response.setInvestorComment(s.getInvestorComment());
            response.setRiskLabel(s.getRiskLabel());
            response.setRiskTier(s.getRiskTier());
            response.setExtra(s.getExtra());
        });

        response.setAnalysisItems(toAnalysisItemDtos(analysisItems));

        return response;
    }

    private List<AnalysisItemDto> toAnalysisItemDtos(List<AiAnalysisItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(item -> {
                    AnalysisItemDto dto = new AnalysisItemDto(
                            item.getCategory(),
                            item.getTargetText(),
                            item.getMaterialImpact(),
                            item.getRiskLabel(),
                            item.getCharStart() == null ? -1 : item.getCharStart(),
                            item.getCharEnd() == null ? -1 : item.getCharEnd()
                    );
                    dto.setRiskTier(item.getRiskTier());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
