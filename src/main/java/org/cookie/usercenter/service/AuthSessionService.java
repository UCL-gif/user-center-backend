package org.cookie.usercenter.service;

import jakarta.servlet.http.HttpServletRequest;
import org.cookie.usercenter.model.domain.User;

/** 登录、校验当前身份以及使用户会话失效的业务契约。 */
public interface AuthSessionService {
    String USER_ID_ATTRIBUTE = "authUserId";
    String CREDENTIAL_STAMP_ATTRIBUTE = "authCredentialStamp";
    String REMEMBER_ME_ATTRIBUTE = "authRememberMe";
    String ABSOLUTE_EXPIRES_AT_ATTRIBUTE = "authAbsoluteExpiresAt";
    String CREDENTIAL_EPOCH_ATTRIBUTE = "authCredentialEpoch";
    int IDLE_TIMEOUT_SECONDS = 1800;
    int REMEMBER_ME_SECONDS = 604800;

    void login(User user, HttpServletRequest request, boolean remember);

    User requireUser(HttpServletRequest request);

    void logout(HttpServletRequest request);

    void invalidateUserSessions(long userId);
}
