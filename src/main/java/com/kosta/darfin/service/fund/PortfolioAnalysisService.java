package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.fund.AiReports;
import com.kosta.darfin.entity.fund.UserTradingStats;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.fund.AiReportsRepository;
import com.kosta.darfin.repository.fund.UserTradingStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {

    @Value("${llm.service.base-url:http://127.0.0.1:8001}")
    private String llmServiceBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UsersRepository usersRepository;
    private final AiReportsRepository aiReportsRepository;
    private final UserTradingStatsRepository userTradingStatsRepository;

    public Map<String, Object> analyzeAndSave(String email, Map<String, Object> requestBody) {
        Users user = resolveUser(email, requestBody);
        Map<String, Object> pythonRequest = new LinkedHashMap<>(requestBody);
        pythonRequest.remove("userId");
        pythonRequest.remove("user_id");

        Map<String, Object> pythonResponse = requestPythonPortfolioAnalysis(pythonRequest);
        Map<String, Object> report = asMap(pythonResponse.get("report"));
        Map<String, Object> metrics = asMap(pythonResponse.get("metrics"));
        String analysis = asString(pythonResponse.get("analysis"));
        String prompt = asString(pythonResponse.get("prompt"));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("metrics", metrics);
        content.put("report", report);
        content.put("prompt", prompt);
        content.put("analysis", analysis);

        AiReports saved = aiReportsRepository.save(AiReports.builder()
                .user(user)
                .healthScore(extractHealthScore(report))
                .tendencyLabel(asString(report.get("label")))
                .reportContent(toJson(content))
                .shareToken(UUID.randomUUID().toString().replace("-", ""))
                .build());

        upsertUserTradingStats(user, metrics);

        Map<String, Object> response = new LinkedHashMap<>(pythonResponse);
        response.put("report_id", saved.getReportId());
        response.put("reportId", saved.getReportId());
        response.put("db_error", null);
        response.put("dbError", null);
        return response;
    }

    private void upsertUserTradingStats(Users user, Map<String, Object> metrics) {
        Map<String, Object> behavior = asMap(metrics.get("behavior"));

        UserTradingStats stats = userTradingStatsRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    UserTradingStats created = new UserTradingStats();
                    created.setUser(user);
                    return created;
                });

        stats.setMonthlyTradeFreq(toDouble(behavior.get("tradesPerMonth")));
        stats.setAvgHoldDays(toDouble(behavior.get("avgHoldDays")));
        stats.setStopLossRate(toDouble(behavior.get("stopLossRatio")));
        stats.setTakeProfitRate(toDouble(behavior.get("takeProfitRatio")));
        stats.setChaseBuyCount(toInt(behavior.get("chaseBuyCount")));
        stats.setUpdatedAt(LocalDateTime.now());

        userTradingStatsRepository.save(stats);
    }

    public List<Map<String, Object>> listReports(String email, Integer limit, Long requestedUserId) {
        Map<String, Object> userHint = new LinkedHashMap<>();
        if (requestedUserId != null) {
            userHint.put("userId", requestedUserId);
        }
        Users user = resolveUser(email, userHint);
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 50));

        return aiReportsRepository.findByUser_IdOrderByCreatedAtDescReportIdDesc(user.getId()).stream()
                .limit(safeLimit)
                .map(this::toClientReport)
                .collect(Collectors.toList());
    }

    private Map<String, Object> requestPythonPortfolioAnalysis(Map<String, Object> body) {
        String url = llmServiceBaseUrl + "/analysis/portfolio";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            return response == null ? new LinkedHashMap<>() : response;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Python 포트폴리오 분석 서버 호출 실패: " + e.getMessage(), e);
        }
    }

    private Users resolveUser(String email, Map<String, Object> body) {
        if (email != null && !email.isBlank()) {
            return usersRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));
        }

        Long userId = toLong(body.get("userId"));
        if (userId == null) userId = toLong(body.get("user_id"));
        if (userId != null) {
            return usersRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
    }

    private Map<String, Object> toClientReport(AiReports row) {
        Map<String, Object> content = parseJsonObject(row.getReportContent());
        Map<String, Object> report = new LinkedHashMap<>(asMap(content.get("report")));
        report.put("remoteReportId", row.getReportId());
        report.put("geminiAnalysis", sanitize(content.get("analysis")));
        report.put("dbError", null);
        if (row.getCreatedAt() != null) {
            report.put("ts", row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return report;
    }

    private Map<String, Object> parseJsonObject(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String extractHealthScore(Map<String, Object> report) {
        Object health = report.get("health");
        if (!(health instanceof Map<?, ?>)) return null;
        Object total = ((Map<?, ?>) health).get("total");
        return total == null ? null : String.valueOf(total);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 리포트 저장 JSON 생성 실패", e);
        }
    }

    private Object sanitize(Object value) {
        if (value instanceof String) return ((String) value).replace("undefined", "미분류");
        if (value instanceof List<?>) return ((List<?>) value).stream().map(this::sanitize).collect(Collectors.toList());
        if (value instanceof Map<?, ?>) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), sanitize(item)));
            return result;
        }
        return value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
