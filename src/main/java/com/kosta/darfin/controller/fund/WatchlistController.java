package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.WatchlistResponse;
import com.kosta.darfin.service.fund.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/funds/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public List<WatchlistResponse> getWatchlist(@AuthenticationPrincipal UserDetails userDetails) {
        return watchlistService.getWatchlist(userDetails.getUsername());
    }

    @PutMapping("/{stockCode}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String stockCode) {
        watchlistService.addToWatchlist(userDetails.getUsername(), stockCode);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{stockCode}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String stockCode) {
        watchlistService.removeFromWatchlist(userDetails.getUsername(), stockCode);
        return ResponseEntity.noContent().build();
    }
}
