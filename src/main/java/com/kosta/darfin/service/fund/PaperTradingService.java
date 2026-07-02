package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.BalanceResponse;
import com.kosta.darfin.dto.fund.HoldingResponse;
import com.kosta.darfin.dto.fund.OrderRequest;
import com.kosta.darfin.dto.fund.OrderResponse;
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
import com.kosta.darfin.repository.fund.StockInfoRepository;
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

    private final UsersRepository     usersRepository;
    private final FundsRepository     fundsRepository;
    private final HoldingsRepository  holdingsRepository;
    private final TradesRepository    tradesRepository;
    private final FundHistoryRepository fundHistoryRepository;
    private final StockInfoRepository stockInfoRepository;
    private final StockInfoService    stockInfoService;
    private final KisApiClient        kisApiClient;

    // =========================================================================
    // 포트폴리오 (기존 API — /funds/paper)
    // =========================================================================

    public PortfolioResponse getPortfolio(String email) {
        Users user = findUser(email);
        return buildPortfolio(user);
    }

    @Transactional
    public PortfolioResponse buyPortfolio(String email, String code, int qty, long price) {
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

        log.info("모의투자 매수(포트폴리오): user={} code={} qty={} price={}", email, code, qty, price);
        return buildPortfolio(user);
    }

    @Transactional
    public PortfolioResponse sellPortfolio(String email, String code, int qty, long price) {
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

        log.info("모의투자 매도(포트폴리오): user={} code={} qty={} price={} pnl={}", email, code, qty, price, pnl);
        return buildPortfolio(user);
    }

    private static final int DAILY_CHARGE_LIMIT = 3;

    @Transactional
    public PortfolioResponse charge(String email, long amount) {
        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long chargedToday = fundHistoryRepository
                .countByUser_IdAndTypeAndCreatedAtAfter(user.getId(), "CHARGE", todayStart);
        if (chargedToday >= DAILY_CHARGE_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자금 충전은 1일 " + DAILY_CHARGE_LIMIT + "회까지 가능합니다.");
        }

        funds.updateCashBalance(funds.getCashBalance() + amount);

        fundHistoryRepository.save(FundHistory.builder()
                .user(user).type("CHARGE").amount(amount)
                .createdAt(LocalDateTime.now())
                .build());

        return buildPortfolio(user);
    }

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

    // =========================================================================
    // 주문 API (/funds/paper-trading)
    // =========================================================================

    public BalanceResponse getBalance(String email) {
        Funds funds = getOrCreateFunds(findUser(email));
        return BalanceResponse.builder()
                .availableBalance(funds.getCashBalance())
                .build();
    }

    public HoldingResponse getHolding(String email, String stockCode) {
        Holdings holding = holdingsRepository
                .findByUserEmailAndStockInfoStockCode(email, stockCode)
                .orElse(null);

        long currentPrice = fetchCurrentPrice(stockCode);
        String stockName = holding != null
                ? holding.getStockInfo().getStockName()
                : stockInfoRepository.findById(stockCode)
                        .map(StockInfo::getStockName)
                        .orElse(stockCode);

        if (holding == null || holding.getQuantity() <= 0) {
            return HoldingResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .quantity(0)
                    .avgBuyPrice(0L)
                    .currentPrice(currentPrice)
                    .valuationPnl(0L)
                    .valuationPnlRate(0.0)
                    .build();
        }

        long pnl = (currentPrice - holding.getAvgBuyPrice()) * holding.getQuantity();
        double pnlRate = holding.getAvgBuyPrice() > 0
                ? (double) (currentPrice - holding.getAvgBuyPrice()) / holding.getAvgBuyPrice() * 100
                : 0.0;

        return HoldingResponse.builder()
                .stockCode(stockCode)
                .stockName(holding.getStockInfo().getStockName())
                .quantity(holding.getQuantity())
                .avgBuyPrice(holding.getAvgBuyPrice())
                .currentPrice(currentPrice)
                .valuationPnl(pnl)
                .valuationPnlRate(Math.round(pnlRate * 100.0) / 100.0)
                .build();
    }

    @Transactional
    public OrderResponse buy(String email, OrderRequest req) {
        validateOrderType(req.getOrderType());

        long price = resolvePrice(req);
        long total = price * req.getQuantity();

        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);
        StockInfo stock = getStockInfo(req.getStockCode());

        if (funds.getCashBalance() < total) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("주문 가능 금액이 부족합니다. (필요: %,d원 / 보유: %,d원)", total, funds.getCashBalance()));
        }

        funds.deductBalance(total);

        Holdings holding = holdingsRepository
                .findByUserEmailAndStockInfoStockCode(email, req.getStockCode())
                .orElse(null);

        if (holding == null) {
            holding = Holdings.builder()
                    .user(user)
                    .stockInfo(stock)
                    .quantity(req.getQuantity())
                    .avgBuyPrice(price)
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            holding.applyBuy(req.getQuantity(), price);
        }
        holdingsRepository.save(holding);

        Trades trade = Trades.builder()
                .user(user)
                .stockInfo(stock)
                .type("BUY")
                .status("COMPLETE")
                .quantity(req.getQuantity())
                .price(price)
                .tradedAt(LocalDateTime.now())
                .build();
        tradesRepository.save(trade);

        log.info("매수 체결: {} {} {}주 @{}", email, req.getStockCode(), req.getQuantity(), price);

        return OrderResponse.builder()
                .tradeId(trade.getTradeId())
                .stockCode(req.getStockCode())
                .stockName(stock.getStockName())
                .side("BUY")
                .orderType(req.getOrderType())
                .price(price)
                .quantity(req.getQuantity())
                .totalAmount(total)
                .tradedAt(trade.getTradedAt())
                .build();
    }

    @Transactional
    public OrderResponse sell(String email, OrderRequest req) {
        validateOrderType(req.getOrderType());

        Users user = findUser(email);
        Funds funds = getOrCreateFunds(user);

        Holdings holding = holdingsRepository
                .findByUserEmailAndStockInfoStockCode(email, req.getStockCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "보유하지 않은 종목입니다."));

        if (holding.getQuantity() < req.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("보유 수량이 부족합니다. (요청: %d주 / 보유: %d주)", req.getQuantity(), holding.getQuantity()));
        }

        long price = resolvePrice(req);
        long total = price * req.getQuantity();
        long realizedPnl = (price - holding.getAvgBuyPrice()) * req.getQuantity();

        funds.addBalance(total);

        holding.applySell(req.getQuantity());
        if (holding.getQuantity() <= 0) {
            holdingsRepository.delete(holding);
        } else {
            holdingsRepository.save(holding);
        }

        StockInfo stock = holding.getStockInfo();
        Trades trade = Trades.builder()
                .user(user)
                .stockInfo(stock)
                .type("SELL")
                .status("COMPLETE")
                .quantity(req.getQuantity())
                .price(price)
                .realizedPnl(realizedPnl)
                .tradedAt(LocalDateTime.now())
                .build();
        tradesRepository.save(trade);

        log.info("매도 체결: {} {} {}주 @{} (실현손익: {})", email, req.getStockCode(), req.getQuantity(), price, realizedPnl);

        return OrderResponse.builder()
                .tradeId(trade.getTradeId())
                .stockCode(req.getStockCode())
                .stockName(stock.getStockName())
                .side("SELL")
                .orderType(req.getOrderType())
                .price(price)
                .quantity(req.getQuantity())
                .totalAmount(total)
                .realizedPnl(realizedPnl)
                .tradedAt(trade.getTradedAt())
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private PortfolioResponse buildPortfolio(Users user) {
        Funds funds = getOrCreateFunds(user);
        List<Holdings> holdings = holdingsRepository.findByUser_IdOrderByUpdatedAtDesc(user.getId());
        List<Trades> trades = tradesRepository.findByUser_IdOrderByTradedAtDesc(user.getId());
        List<FundHistory> fundHistory = fundHistoryRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        return PortfolioResponse.from(funds, holdings, trades, fundHistory);
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

    private long resolvePrice(OrderRequest req) {
        if ("MARKET".equalsIgnoreCase(req.getOrderType())) {
            return fetchCurrentPrice(req.getStockCode());
        }
        if (req.getPrice() == null || req.getPrice() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지정가 주문은 가격을 입력해야 합니다.");
        }
        return req.getPrice();
    }

    private long fetchCurrentPrice(String stockCode) {
        try {
            return kisApiClient.fetchStockBasicInfo(stockCode).getCurrentPrice();
        } catch (Exception e) {
            log.warn("현재가 조회 실패 code={}: {}", stockCode, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "현재가를 조회할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private void validateOrderType(String orderType) {
        if (!"LIMIT".equalsIgnoreCase(orderType) && !"MARKET".equalsIgnoreCase(orderType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주문 유형은 LIMIT 또는 MARKET이어야 합니다.");
        }
    }

    private Users findUser(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private StockInfo getStockInfo(String stockCode) {
        return stockInfoRepository.findById(stockCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목 정보를 찾을 수 없습니다: " + stockCode));
    }
}
