package org.cookie.usercenter.service;

import org.cookie.usercenter.service.impl.AuthSessionServiceImpl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.cookie.usercenter.config.SessionCookieConfig;
import org.cookie.usercenter.mapper.UserMapper;
import org.cookie.usercenter.testsupport.TestSessionConfiguration.MemoryIndexedSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionHttpTest {
    private MemoryIndexedSessionRepository repository;
    private AuthSessionService service;
    private SessionRepositoryFilter<MapSession> filter;

    @BeforeEach
    void setUp() {
        repository = new MemoryIndexedSessionRepository();
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectById(42L)).thenReturn(AuthSessionServiceTest.user(42L));
        when(mapper.selectById(43L)).thenReturn(AuthSessionServiceTest.user(43L));
        service = new AuthSessionServiceImpl(mapper, repository, mock(AuthChallengeStore.class));
        filter = new SessionRepositoryFilter<>(repository);
        CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(new SessionCookieConfig().cookieSerializer());
        filter.setHttpSessionIdResolver(resolver);
    }

    @Test
    void ordinaryLoginSetsBrowserSessionCookieAndPersistsOnlyThirtyMinutes() throws Exception {
        MockHttpServletResponse response = request(null, false, req -> service.login(AuthSessionServiceTest.user(42L), req, false));
        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.startsWith("SESSION="));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
        assertFalse(header.contains("Max-Age"));
        assertFalse(header.contains("Secure"));
        assertEquals(1800, repository.findById(sessionId(response)).getMaxInactiveInterval().toSeconds());
    }

    @Test
    void rememberedLoginSetsSevenDayCookieWithSecureForHttps() throws Exception {
        MockHttpServletResponse response = request(null, true, req -> service.login(AuthSessionServiceTest.user(42L), req, true));
        String header = response.getHeader("Set-Cookie");
        assertTrue(header.contains("Max-Age=604800"));
        assertTrue(header.contains("Secure"));
        assertEquals(604800, repository.findById(sessionId(response)).getMaxInactiveInterval().toSeconds());
    }

    @Test
    void reauthenticationChangesIdAndRemovesOldRepositorySession() throws Exception {
        MockHttpServletResponse first = request(null, false, req -> service.login(AuthSessionServiceTest.user(42L), req, true));
        MockHttpServletResponse second = request(cookie(first), false, req -> service.login(AuthSessionServiceTest.user(42L), req, false));
        assertNotEquals(sessionId(first), sessionId(second));
        assertNull(repository.findById(sessionId(first)));
        assertNotNull(repository.findById(sessionId(second)));
        assertFalse(second.getHeader("Set-Cookie").contains("Max-Age"));
    }

    @Test
    void logoutDeletesPersistedSessionAndClearsCookieWithZeroAge() throws Exception {
        MockHttpServletResponse first = request(null, false, req -> service.login(AuthSessionServiceTest.user(42L), req, true));
        MockHttpServletResponse response = request(cookie(first), false, service::logout);
        assertNull(repository.findById(sessionId(first)));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }

    @Test
    void invalidatingUserSessionsRemovesEveryBrowserButPreservesOtherUsers() throws Exception {
        MockHttpServletResponse one = request(null, false, req -> service.login(AuthSessionServiceTest.user(42L), req, false));
        MockHttpServletResponse two = request(null, false, req -> service.login(AuthSessionServiceTest.user(42L), req, true));
        MockHttpServletResponse other = request(null, false, req -> service.login(AuthSessionServiceTest.user(43L), req, false));
        assertEquals(2, repository.findByPrincipalName("42").size());
        service.invalidateUserSessions(42L);
        assertNull(repository.findById(sessionId(one)));
        assertNull(repository.findById(sessionId(two)));
        assertNotNull(repository.findById(sessionId(other)));
    }

    private MockHttpServletResponse request(Cookie cookie, boolean secure, Consumer<HttpServletRequest> action) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(secure);
        if (cookie != null) request.setCookies(cookie);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> action.accept((HttpServletRequest) req));
        return response;
    }

    private Cookie cookie(MockHttpServletResponse response) {
        return response.getCookie("SESSION");
    }

    private String sessionId(MockHttpServletResponse response) {
        return new String(Base64.getDecoder().decode(cookie(response).getValue()), StandardCharsets.UTF_8);
    }
}
