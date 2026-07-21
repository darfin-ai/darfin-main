package com.kosta.darfin.service.payment;

import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.payment.PaymentMethods;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.payment.PaymentMethodsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodsRepository paymentMethodsRepository;
    private final UsersRepository usersRepository;
    private final TossPaymentsClient tossPaymentsClient;

    @Transactional
    public PaymentMethods registerCard(String email, String authKey, String cardName) {
        Users user = resolveAuthenticatedUser(email);

        TossPaymentsClient.BillingKeyResult result =
                tossPaymentsClient.issueBillingKey(authKey, "USER_" + user.getId());

        boolean isFirst = paymentMethodsRepository.countByUser_Id(user.getId()) == 0;
        String resolvedCardName = (cardName != null && !cardName.isBlank())
                ? cardName.trim()
                : result.getCardCompany();
        return paymentMethodsRepository.save(PaymentMethods.builder()
                .user(user)
                .billingKey(result.getBillingKey())
                .cardCompany(result.getCardCompany())
                .cardName(resolvedCardName)
                .maskedCardNum(result.getMaskedCardNumber())
                .isDefault(isFirst)
                .build());
    }

    @Transactional(readOnly = true)
    public List<PaymentMethods> list(String email) {
        Users user = resolveAuthenticatedUser(email);
        return paymentMethodsRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void delete(String email, Long methodId) {
        Users user = resolveAuthenticatedUser(email);
        PaymentMethods method = paymentMethodsRepository.findByIdAndUser_Id(methodId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 수단을 찾을 수 없습니다."));
        try {
            // payments 테이블이 이 결제수단을 참조 중이면 FK 제약조건 위반이 발생하므로,
            // flush로 즉시 실행시켜 이 트랜잭션 안에서 잡아 명확한 에러로 변환한다.
            paymentMethodsRepository.delete(method);
            paymentMethodsRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "결제 이력이 있는 카드는 삭제할 수 없습니다. 다른 카드를 기본 결제수단으로 설정해주세요.");
        }
    }

    @Transactional
    public void setDefault(String email, Long methodId) {
        Users user = resolveAuthenticatedUser(email);
        List<PaymentMethods> methods = paymentMethodsRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        boolean found = false;
        for (PaymentMethods method : methods) {
            if (method.getId().equals(methodId)) {
                method.markDefault();
                found = true;
            } else {
                method.unmarkDefault();
            }
        }
        if (!found) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 수단을 찾을 수 없습니다.");
        }
    }

    private Users resolveAuthenticatedUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));
    }
}
