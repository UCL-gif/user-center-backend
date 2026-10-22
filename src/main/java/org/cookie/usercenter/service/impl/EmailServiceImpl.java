package org.cookie.usercenter.service.impl;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;

@Service
public class EmailServiceImpl implements EmailService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    @Value("${spring.mail.username}")
    private String senderAddress;
    @Resource
    private JavaMailSender javaMailSender;
    @Resource
    private AuthChallengeStore challengeStore;

    @Override
    public boolean sendEmailCode(String email) throws MessagingException {
        return sendCode(email, CodePurpose.REGISTER, true);
    }

    @Override
    public boolean sendCode(String email, CodePurpose purpose, boolean deliver) throws MessagingException {
        String code = generateCode(6);
        if (!challengeStore.issueCode(purpose, email, code)) return false;
        // 不存在的邮箱也有发送间隔，但不发送邮件、不暴露账号是否存在。
        if (!deliver) return true;
        try {
            var message = javaMailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(senderAddress);
            helper.setTo(email);
            String purposeText = purpose == CodePurpose.REGISTER ? "注册" : "重置密码";
            helper.setSubject("用户管理系统：" + purposeText + "验证码");
            helper.setText("您的" + purposeText + "验证码是：" + code
                    + "。1分钟内有效，最多输错3次。请勿将验证码告知他人。");
            javaMailSender.send(message);
            return true;
        } catch (MessagingException | RuntimeException error) {
            challengeStore.discardCode(purpose, email, code);
            throw error;
        }
    }

    @Override
    public void verifyCode(String email, String code, CodePurpose purpose) {
        switch (challengeStore.consumeCode(purpose, email, code)) {
            case VALID -> { }
            case LOCKED -> throw new BusinessException(ErrorCode.PARAMETERS_ERROR,
                    "验证码已输错3次，请在发送间隔结束后重新获取");
            case EXPIRED -> throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "验证码已过期或未发送，请重新获取");
            case INVALID -> throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "邮箱验证码错误，最多允许输错3次");
        }
    }

    private String generateCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }
}
