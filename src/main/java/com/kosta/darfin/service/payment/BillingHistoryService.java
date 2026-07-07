package com.kosta.darfin.service.payment;

import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.payment.Payments;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.payment.PaymentsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingHistoryService {

    private final PaymentsRepository paymentsRepository;
    private final UsersRepository usersRepository;
    private final TossPaymentsClient tossPaymentsClient;

    @Transactional(readOnly = true)
    public List<Payments> list(String email) {
        Users user = resolveAuthenticatedUser(email);
        return paymentsRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public Payments getOne(String email, Long paymentId) {
        Users user = resolveAuthenticatedUser(email);
        return findOwned(user.getId(), paymentId);
    }

    @Transactional
    public Payments refund(String email, Long paymentId, String reason) {
        Users user = resolveAuthenticatedUser(email);
        Payments payment = findOwned(user.getId(), paymentId);

        if (!"DONE".equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "환불 가능한 결제 상태가 아닙니다.");
        }

        tossPaymentsClient.cancelPayment(payment.getPgTid(), reason, null);
        payment.markCanceled();
        return payment;
    }

    private Payments findOwned(Long userId, Long paymentId) {
        return paymentsRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
    }

    private Users resolveAuthenticatedUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));
    }
}
