package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.PortfolioResponse;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.fund.FundHistory;
import com.kosta.darfin.entity.fund.Funds;
import com.kosta.darfin.entity.fund.Holdings;
import com.kosta.darfin.entity.fund.StockInfo;
import com.kosta.darfin.entity.fund.Trades;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.fund.FundHistoryRepository;
import com.kosta.darfin.repository.fund.FundsRepository;
import com.kosta.darfin.repository.fund.HoldingsRepository;
import com.kosta.darfin.repository.fund.TradesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaperTradingService {

    private static final long DEFAULT_INITIAL_AMOUNT = 10_000_000L;

    private final UsersRepository usersRepository;
    private final FundsRepository fundsRepository;
    private final HoldingsRepository holdingsRepository;
    private final TradesRepository tradesRepository;
    private final FundHistoryRepository fundHistoryRepository;
    private final StockInfoService stockInfoService;

    /** 로그인 직후 포트폴리오 전체 로드 */
    public PortfolioResponse getPortfolio(String email) {
        Users user = findUser(email);
        return buildPortfolio(user);
    }

    /** 매수 — 잔고 차감 + 보유종목 갱신 + 거래 기록 */
    @Transactional
    public PortfolioResponse buy(String email, String code, int qty, long price) {
        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);

        long cost = (long) qty * price;
        if (cost > funds.getCashBalance()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잔고가 부족합니다.");
        }

        StockInfo stockInfo = stockInfoService.getOrFetch(code);

        Holdings holding = holdingsRepository
                .findByUser_IdAndStockInfo_StockCode(user.getId(), code)
                .orElse(null);

        if (holding == null) {
            holdingsRepository.save(Holdings.builder()
                    .user(user).stockInfo(stockInfo)
                    .quantity(qty).avgBuyPrice(price)
                    .updatedAt(LocalDateTime.now())
                    .build());
        } else {
            holding.buy(qty, price);
        }

        funds.updateCashBalance(funds.getCashBalance() - cost);

        tradesRepository.save(Trades.builder()
                .user(user).stockInfo(stockInfo)
                .type("BUY").status("DONE")
                .quantity(qty).price(price)
                .tradedAt(LocalDateTime.now())
                .build());

        log.info("모의투자 매수: user={} code={} qty={} price={}", email, code, qty, price);
        return buildPortfolio(user);
    }

    /** 매도 — 잔고 추가 + 보유종목 차감/삭제 + 거래 기록 */
    @Transactional
    public PortfolioResponse sell(String email, String code, int qty, long price) {
        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);

        Holdings holding = holdingsRepository
                .findByUser_IdAndStockInfo_StockCode(user.getId(), code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "보유하지 않은 종목입니다."));

        if (holding.getQuantity() < qty) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "보유 수량이 부족합니다.");
        }

        long pnl = (price - holding.getAvgBuyPrice()) * (long) qty;
        boolean fullyExited = holding.sell(qty);
        if (fullyExited) {
            holdingsRepository.delete(holding);
        }

        funds.updateCashBalance(funds.getCashBalance() + (long) qty * price);

        tradesRepository.save(Trades.builder()
                .user(user).stockInfo(holding.getStockInfo())
                .type("SELL").status("DONE")
                .quantity(qty).price(price).realizedPnl(pnl)
                .tradedAt(LocalDateTime.now())
                .build());

        log.info("모의투자 매도: user={} code={} qty={} price={} pnl={}", email, code, qty, price, pnl);
        return buildPortfolio(user);
    }

    /** 투자금 충전 (1일 3회 제한은 프론트에서 관리) */
    @Transactional
    public PortfolioResponse charge(String email, long amount) {
        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);

        funds.updateCashBalance(funds.getCashBalance() + amount);

        fundHistoryRepository.save(FundHistory.builder()
                .user(user).type("CHARGE").amount(amount)
                .createdAt(LocalDateTime.now())
                .build());

        return buildPortfolio(user);
    }

    /** 초기화 — 보유종목 전부 삭제 + 잔고를 초기투자금으로 복원 */
    @Transactional
    public PortfolioResponse reset(String email) {
        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);

        holdingsRepository.deleteByUser_Id(user.getId());
        funds.updateCashBalance(funds.getInitialAmount());

        fundHistoryRepository.save(FundHistory.builder()
                .user(user).type("RESET").amount(funds.getInitialAmount())
                .createdAt(LocalDateTime.now())
                .build());

        return buildPortfolio(user);
    }

    /** 초기 투자금 설정 (최초 또는 재설정) */
    @Transactional
    public PortfolioResponse initAmount(String email, long amount) {
        Users user = findUser(email);
        Funds funds = fundsRepository.findByUser_Id(user.getId()).orElse(null);

        if (funds == null) {
            funds = fundsRepository.save(Funds.builder()
                    .user(user).initialAmount(amount).cashBalance(amount)
                    .startDate(LocalDate.now()).updatedAt(LocalDateTime.now())
                    .build());
        } else {
            holdingsRepository.deleteByUser_Id(user.getId());
            funds.initAmount(amount);
        }

        fundHistoryRepository.save(FundHistory.builder()
                .user(user).type("INIT").amount(amount)
                .createdAt(LocalDateTime.now())
                .build());

        return buildPortfolio(user);
    }

    // ── helpers ──

    private PortfolioResponse buildPortfolio(Users user) {
        Funds funds = getOrCreateFunds(user);
        List<Holdings> holdings = holdingsRepository.findByUser_IdOrderByUpdatedAtDesc(user.getId());
        List<Trades> trades = tradesRepository.findByUser_IdOrderByTradedAtDesc(user.getId());
        return PortfolioResponse.from(funds, holdings, trades);
    }

    private Funds getOrCreateFunds(Users user) {
        return fundsRepository.findByUser_Id(user.getId())
                .orElseGet(() -> fundsRepository.save(Funds.builder()
                        .user(user)
                        .initialAmount(DEFAULT_INITIAL_AMOUNT)
                        .cashBalance(DEFAULT_INITIAL_AMOUNT)
                        .startDate(LocalDate.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private Users findUser(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
