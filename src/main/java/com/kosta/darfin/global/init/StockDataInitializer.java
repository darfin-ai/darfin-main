package com.kosta.darfin.global.init;

import com.kosta.darfin.repository.common.StockRepository;
import com.kosta.darfin.service.community.DartApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockDataInitializer implements ApplicationRunner {

    private final StockRepository stockRepository;
    private final DartApiService dartApiService;

    @Override
    public void run(ApplicationArguments args) {
        int normalized = dartApiService.normalizeKnownCompanyNames();
        if (normalized > 0) {
            log.info("[Stock] 기업 표시명 {}건 정규화 완료", normalized);
        }

        long count = stockRepository.count();
        if (count > 0) {
            log.info("[Stock] 기업 데이터 {}개 이미 존재 — 동기화 건너뜀", count);
            return;
        }

        log.info("[Stock] 기업 데이터 없음 — DART API에서 기업 목록 동기화 시작");
        try {
            int synced = dartApiService.syncCorpList();
            log.info("[Stock] 동기화 완료: {}개 기업 저장됨", synced);
        } catch (Exception e) {
            log.error("[Stock] DART API 동기화 실패 (application.properties의 dart.api.key 확인): {}", e.getMessage());
        }
    }
}
