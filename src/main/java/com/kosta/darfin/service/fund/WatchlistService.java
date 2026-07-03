package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.WatchlistResponse;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.fund.StockInfo;
import com.kosta.darfin.entity.fund.Watchlist;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.fund.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    private static final int MAX_WATCHLIST_SIZE = 30;

    private final WatchlistRepository watchlistRepository;
    private final UsersRepository usersRepository;
    private final StockInfoService stockInfoService;

    public List<String> getWatchlistCodes(String email) {
        Users user = findUser(email);
        return watchlistRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> w.getStockInfo().getStockCode())
                .collect(Collectors.toList());
    }

    public List<WatchlistResponse> getWatchlist(String email) {
        Users user = findUser(email);
        return watchlistRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> {
                    String code = w.getStockInfo().getStockCode();
                    String name = stockInfoService.getCachedNameOrFallback(code, w.getStockInfo().getStockName());
                    return new WatchlistResponse(code, name);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void addToWatchlist(String email, String stockCode) {
        Users user = findUser(email);

        if (watchlistRepository.findByUser_IdAndStockInfo_StockCode(user.getId(), stockCode).isPresent()) {
            return; // 이미 등록됨 — 멱등 처리
        }
        if (watchlistRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).size() >= MAX_WATCHLIST_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "관심종목은 최대 " + MAX_WATCHLIST_SIZE + "개까지 등록할 수 있습니다.");
        }

        StockInfo stockInfo = stockInfoService.getOrCreateFromStockMaster(stockCode);
        watchlistRepository.save(Watchlist.builder()
                .user(user)
                .stockInfo(stockInfo)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void removeFromWatchlist(String email, String stockCode) {
        Users user = findUser(email);
        watchlistRepository.deleteByUser_IdAndStockInfo_StockCode(user.getId(), stockCode);
    }

    private Users findUser(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
