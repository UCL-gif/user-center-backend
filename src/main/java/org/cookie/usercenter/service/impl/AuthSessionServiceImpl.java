package org.cookie.usercenter.service.impl;

import org.cookie.usercenter.service.AuthChallengeStore;
import org.cookie.usercenter.service.AuthSessionService;

import jakarta.servlet.http.HttpServletRequest;
import org.cookie.usercenter.mapper.UserMapper;
import org.cookie.usercenter.model.domain.User;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.utils.PasswordUtils;

@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private final UserMapper userMapper;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final AuthChallengeStore challenges;

    public AuthSessionServiceImpl(UserMapper userMapper, FindByIndexNameSessionRepository<? extends Session> sessionRepository,
                              AuthChallengeStore challenges) {
        this.userMapper = userMapper;
        this.sessionRepository = sessionRepository;
        this.challenges = challenges;
    }

    @Override
    public void login(User user, HttpServletRequest request, boolean remember) {
        if (user == null) throw expired();
        long epoch = challenges.credentialEpoch(user.getId());
        // 登录查询之后可能恰好有人重置了密码；重新确认，不能给旧密码创建新会话。
        User current = user == null ? null : userMapper.selectById(user.getId());
        if (!usable(current) || !current.getUserPassword().equals(user.getUserPassword())) throw expired();
        logout(request);
        var session = request.getSession(true);
        session.setAttribute(USER_ID_ATTRIBUTE, current.getId());
        session.setAttribute(CREDENTIAL_STAMP_ATTRIBUTE, PasswordUtils.sha256(current.getUserPassword()));
        session.setAttribute(CREDENTIAL_EPOCH_ATTRIBUTE, epoch);
        session.setAttribute(REMEMBER_ME_ATTRIBUTE, remember);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, current.getId().toString());
        session.setMaxInactiveInterval(remember ? REMEMBER_ME_SECONDS : IDLE_TIMEOUT_SECONDS);
        if (remember) session.setAttribute(ABSOLUTE_EXPIRES_AT_ATTRIBUTE,
                System.currentTimeMillis() + REMEMBER_ME_SECONDS * 1000L);
    }

    @Override
    public User requireUser(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) throw expired();
        Object id = session.getAttribute(USER_ID_ATTRIBUTE);
        Object absolute = session.getAttribute(ABSOLUTE_EXPIRES_AT_ATTRIBUTE);
        if (!(id instanceof Long) || (absolute != null
                && (!(absolute instanceof Long) || (Long) absolute <= System.currentTimeMillis()))) {
            logout(request);
            throw expired();
        }
        // 只在会话里存 ID；当前角色、封禁状态和密码指纹以数据库为准。
        User current = userMapper.selectById((Long) id);
        if (!usable(current) || !PasswordUtils.sha256(current.getUserPassword())
                .equals(session.getAttribute(CREDENTIAL_STAMP_ATTRIBUTE))
                || !Long.valueOf(challenges.credentialEpoch((Long) id))
                .equals(session.getAttribute(CREDENTIAL_EPOCH_ATTRIBUTE))) {
            logout(request);
            throw expired();
        }
        return current;
    }

    @Override
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @Override
    public void invalidateUserSessions(long userId) {
        sessionRepository.findByPrincipalName(String.valueOf(userId)).keySet()
                .forEach(sessionRepository::deleteById);
    }

    private boolean usable(User user) {
        return user != null && user.getUserPassword() != null
                && Integer.valueOf(0).equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted())
                && (Integer.valueOf(0).equals(user.getIdentity()) || Integer.valueOf(1).equals(user.getIdentity()));
    }

    private BusinessException expired() {
        return new BusinessException(ErrorCode.NO_LOGIN, "登录已失效，请重新登录");
    }
}
