package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.entity.disclosure.DisclosureType;
import com.kosta.darfin.entity.disclosure.RiskScale;
import com.kosta.darfin.repository.disclosure.DisclosureTypeRepository;
import com.kosta.darfin.repository.disclosure.RiskScaleRepository;
import org.springframework.stereotype.Service;


@Service
public class RiskNormalizer {

    private final DisclosureTypeRepository disclosureTypeRepo;
    private final RiskScaleRepository riskScaleRepo;

    public RiskNormalizer(DisclosureTypeRepository disclosureTypeRepo, RiskScaleRepository riskScaleRepo) {
        this.disclosureTypeRepo = disclosureTypeRepo;
        this.riskScaleRepo = riskScaleRepo;
    }

    
    public NormalizedRisk normalize(String typeCode, String riskLabel) {
        DisclosureType disclosureType = disclosureTypeRepo.findById(typeCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "disclosure_type에 등록되지 않은 type_code입니다: '" + typeCode + "'. "
                                + "먼저 disclosure_type 테이블에 이 유형을 등록하세요."));

        String riskScaleCode = disclosureType.getRiskScaleCode();

        RiskScale row = riskScaleRepo.findByCodeAndLabel(riskScaleCode, riskLabel)
                .orElseThrow(() -> new RiskLabelNotFoundException(riskScaleCode, riskLabel));

        return new NormalizedRisk(riskLabel, row.getRiskTier());
    }


    public String getRiskScaleCode(String typeCode) {
        DisclosureType disclosureType = disclosureTypeRepo.findById(typeCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "disclosure_type에 등록되지 않은 type_code입니다: '" + typeCode + "'."));
        return disclosureType.getRiskScaleCode();
    }

    public Byte getRiskTier(String riskScaleCode, String riskLabel) {
        return riskScaleRepo.findByCodeAndLabel(riskScaleCode, riskLabel)
                .map(RiskScale::getRiskTier)
                .orElseThrow(() -> new RiskLabelNotFoundException(riskScaleCode, riskLabel));
    }

    public static class NormalizedRisk {
        public final String riskLabel;
        public final Byte riskTier;

        public NormalizedRisk(String riskLabel, Byte riskTier) {
            this.riskLabel = riskLabel;
            this.riskTier = riskTier;
        }
    }

    
    public static class RiskLabelNotFoundException extends RuntimeException {
        public RiskLabelNotFoundException(String riskScaleCode, String riskLabel) {
            super("risk_scale에 등록되지 않은 라벨입니다: riskScaleCode='" + riskScaleCode
                    + "', riskLabel='" + riskLabel + "'. risk_scale 테이블 시드 데이터를 먼저 확인하세요.");
        }
    }
}
