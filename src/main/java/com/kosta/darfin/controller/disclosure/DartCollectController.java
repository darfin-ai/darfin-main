package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.DartCollectRequestDto;
import com.kosta.darfin.dto.disclosure.DartCollectResponseDto;
import com.kosta.darfin.service.disclosure.DartCollectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class DartCollectController {

    private final DartCollectService collectService;

    public DartCollectController(DartCollectService collectService) {
        this.collectService = collectService;
    }

    @PostMapping("/api/collect")
    public ResponseEntity<DartCollectResponseDto> collect(@RequestBody DartCollectRequestDto req) {
        if (req.getCompanyName() == null || req.getCompanyName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(DartCollectResponseDto.error("companyName은 필수입니다."));
        }
        if (req.getBgnDe() == null || req.getEndDe() == null) {
            return ResponseEntity.badRequest()
                    .body(DartCollectResponseDto.error("bgnDe, endDe는 필수입니다. (YYYYMMDD 형식)"));
        }

        DartCollectResponseDto result = collectService.collect(req);

        if (!result.isSuccess()) {
            return ResponseEntity.internalServerError().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
