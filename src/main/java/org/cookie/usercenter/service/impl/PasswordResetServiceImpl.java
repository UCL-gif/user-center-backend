package org.cookie.usercenter.service.impl;

import org.cookie.usercenter.service.AuthChallengeStore;
import org.cookie.usercenter.service.AuthSessionService;
import org.cookie.usercenter.service.CodePurpose;
import org.cookie.usercenter.service.EmailService;
import org.cookie.usercenter.service.PasswordResetService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.mapper.UserMapper;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.model.request.*;
import org.cookie.usercenter.utils.PasswordUtils;
import org.cookie.usercenter.utils.RequestValidator;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 邮箱验证 → 一次性凭证 → 修改凭证绑定用户的密码。 */
@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {
    private final UserMapper mapper;
    private final EmailService emails;
    private final AuthChallengeStore challenges;
    private final AuthSessionService sessions;
    private final RequestValidator validator;

    public PasswordResetServiceImpl(UserMapper mapper, EmailService emails, AuthChallengeStore challenges,
                                AuthSessionService sessions, RequestValidator validator) {
        this.mapper = mapper; this.emails = emails; this.challenges = challenges;
        this.sessions = sessions; this.validator = validator;
    }

    @Override
    public boolean sendCode(SendEmailRequest request) {
        validator.validate(request);
        boolean exists = findByEmail(request.getEmail()) != null;
        try {
            emails.sendCode(request.getEmail(), CodePurpose.RESET_PASSWORD, exists);
        } catch (MessagingException | MailException e) {
            // 对外统一提示，不能通过发送结果判断邮箱是否注册；不记录邮箱、验证码或凭证。
            log.warn("找回密码邮件暂时未能发送，请检查邮件服务配置");
        }
        return true;
    }

    @Override
    public ResetAuthorization verifyEmail(PasswordResetVerifyRequest request) {
        validator.validate(request);
        emails.verifyCode(request.getEmail(), request.getVerificationCode(), CodePurpose.RESET_PASSWORD);
        User user = findByEmail(request.getEmail());
        if (user == null) throw invalidTicket();
        String token = challenges.issueResetToken(user.getId(), PasswordUtils.sha256(user.getUserPassword()));
        return new ResetAuthorization(token, AuthChallengeStore.RESET_TOKEN_SECONDS);
    }

    @Transactional
    @Override
    public boolean resetPassword(PasswordResetRequest request) {
        validator.validate(request);
        // 两字段之间的关系属于业务校验，保留在这里；校验失败不消费重置凭证。
        if (!request.getNewPassword().equals(request.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "两次输入的密码不匹配");
        }
        var ticket = challenges.consumeResetToken(request.getResetToken());
        if (ticket == null) throw invalidTicket();
        // 重置事务锁住这一行，多个重置必须依次检查版本和更新，避免 A→B→A 的并发覆盖。
        User user = mapper.selectOne(new QueryWrapper<User>().eq("id", ticket.userId()).last("FOR UPDATE"));
        if (user == null || !PasswordUtils.sha256(user.getUserPassword()).equals(ticket.credentialStamp())
                || challenges.credentialEpoch(ticket.userId()) != ticket.credentialEpoch()) {
            throw invalidTicket();
        }
        String newHash = PasswordUtils.hashPassword(request.getNewPassword());
        if (newHash.equals(user.getUserPassword())) {
            // 每次成功重置都必须改变密码指纹，才能使并发登录及尚未使用的旧凭证失效。
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "新密码不能与原密码相同，请重新验证邮箱");
        }
        // 乐观条件防止两个重置请求互相覆盖；只修改密码，不修改账号、角色和封禁状态。
        int changed = mapper.update(null, new UpdateWrapper<User>()
                .eq("id", user.getId()).eq("user_password", user.getUserPassword())
                .set("user_password", newHash)
                .set("update_time", new java.util.Date()));
        if (changed != 1) throw invalidTicket();
        challenges.rotateCredentialEpoch(user.getId());
        sessions.invalidateUserSessions(user.getId());
        return true;
    }

    private User findByEmail(String email) {
        return mapper.selectOne(new QueryWrapper<User>().eq("email", email));
    }

    private BusinessException invalidTicket() {
        return new BusinessException(ErrorCode.PARAMETERS_ERROR, "重置凭证已过期或失效，请重新验证邮箱");
    }

}
