package com.kosta.darfin.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendTempPasswordMail(String toEmail, String tempPassword) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[Darfin] 임시 비밀번호 안내");
            helper.setText(buildMailBody(tempPassword), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 중 오류가 발생했습니다.", e);
        }
    }

    public void sendSubscriptionDowngradedMail(String toEmail, String reason) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[Darfin] 정기결제 실패로 Basic 요금제로 전환되었습니다");
            helper.setText(buildDowngradeMailBody(reason), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 중 오류가 발생했습니다.", e);
        }
    }

    private String buildDowngradeMailBody(String reason) {
        return "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>"
             + "<h2 style='color: #333;'>정기결제 실패 안내</h2>"
             + "<p>정기결제를 2회 연속 시도했으나 실패하여 Basic(무료) 요금제로 전환되었습니다.</p>"
             + "<p style='color:#888; font-size:13px;'>실패 사유: " + reason + "</p>"
             + "<p>결제 수단을 갱신하신 후 다시 구독을 시작해주세요.</p>"
             + "</div>";
    }

    private String buildMailBody(String tempPassword) {
        return "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>"
             + "<h2 style='color: #333;'>Darfin 임시 비밀번호 안내</h2>"
             + "<p>요청하신 임시 비밀번호가 발급되었습니다.</p>"
             + "<div style='background:#f5f5f5; padding:16px; border-radius:8px; font-size:18px; "
             +      "letter-spacing:2px; font-weight:bold; color:#222;'>"
             + tempPassword
             + "</div>"
             + "<p style='color:#e53935; font-weight:bold; margin-top:20px;'>"
             + "⚠ 보안을 위해 로그인 후 반드시 비밀번호를 변경해주세요."
             + "</p>"
             + "<p style='color:#888; font-size:12px;'>본인이 요청하지 않은 경우 이 메일을 무시하세요.</p>"
             + "</div>";
    }
}
