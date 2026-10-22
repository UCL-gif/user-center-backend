package org.cookie.usercenter.service;

import org.cookie.usercenter.service.impl.AuthSessionServiceImpl;
import org.cookie.usercenter.service.impl.AuthChallengeStoreImpl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.cookie.usercenter.config.RedisSessionConfig;
import org.cookie.usercenter.config.SessionCookieConfig;
import org.cookie.usercenter.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证正式 Redis 会话配置、序列化和用户索引；仅使用隔离的 16389。 */
@EnabledIfSystemProperty(named = "auth.redis.integration", matches = "true")
class AuthRedisSessionIntegrationTest {
    @Test
    void redisRemembersAcrossRequestsAndInvalidatesEverySessionForUser() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("redisConnectionFactory", LettuceConnectionFactory.class,
                    () -> new LettuceConnectionFactory("127.0.0.1", 16389));
            context.register(RedisSessionConfig.class);
            context.refresh();
            var repository = context.getBean(RedisIndexedSessionRepository.class);
            UserMapper mapper = mock(UserMapper.class);
            when(mapper.selectById(424242L)).thenReturn(AuthSessionServiceTest.user(424242));
            var connection = context.getBean(LettuceConnectionFactory.class);
            var service = new AuthSessionServiceImpl(mapper, repository, new AuthChallengeStoreImpl(new StringRedisTemplate(connection)));
            var filter = new SessionRepositoryFilter<>(repository);
            var resolver = new CookieHttpSessionIdResolver();
            resolver.setCookieSerializer(new SessionCookieConfig().cookieSerializer());
            filter.setHttpSessionIdResolver(resolver);
            var first = request(filter, null, req -> service.login(AuthSessionServiceTest.user(424242), req, true));
            var second = request(filter, null, req -> service.login(AuthSessionServiceTest.user(424242), req, false));
            assertEquals(2, repository.findByPrincipalName("424242").size());
            assertTrue(first.getHeader("Set-Cookie").contains("Max-Age=604800"));
            // 模拟浏览器再次打开：只有 Cookie，无旧 request/HttpSession 对象。
            request(filter, first.getCookie("SESSION"), req -> assertEquals(424242L, service.requireUser(req).getId()));
            service.invalidateUserSessions(424242L);
            assertTrue(repository.findByPrincipalName("424242").isEmpty());
            request(filter, second.getCookie("SESSION"), req -> assertThrows(
                    org.cookie.usercenter.exceptions.BusinessException.class, () -> service.requireUser(req)));
        }
    }

    private <S extends Session> MockHttpServletResponse request(SessionRepositoryFilter<S> filter,
            Cookie cookie, Consumer<HttpServletRequest> action) throws Exception {
        var req = new MockHttpServletRequest();
        if (cookie != null) req.setCookies(cookie);
        var response = new MockHttpServletResponse();
        filter.doFilter(req, response, (request, res) -> action.accept((HttpServletRequest) request));
        return response;
    }
}
