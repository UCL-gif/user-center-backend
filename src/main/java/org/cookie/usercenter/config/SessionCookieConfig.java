package org.cookie.usercenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.cookie.usercenter.service.AuthSessionService;

@Configuration(proxyBeanMethods = false)
public class SessionCookieConfig {
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer() {
            @Override
            public void writeCookieValue(CookieValue value) {
                // 退出登录的空 Cookie 必须保持 Max-Age=0；否则浏览器不会删除旧登录标识。
                if (!value.getCookieValue().isEmpty()) {
                    var session = value.getRequest().getSession(false);
                    boolean remember = session != null && Boolean.TRUE.equals(
                            session.getAttribute(AuthSessionService.REMEMBER_ME_ATTRIBUTE));
                    value.setCookieMaxAge(remember ? AuthSessionService.REMEMBER_ME_SECONDS : -1);
                }
                super.writeCookieValue(value);
            }
        };
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
