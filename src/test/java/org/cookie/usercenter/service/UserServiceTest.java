package org.cookie.usercenter.service;

import jakarta.annotation.Resource;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.model.request.UserQueryRequest;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.cookie.usercenter.model.request.UserLoginRequest;
import org.cookie.usercenter.model.request.UserRegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

/** 通过真实 Mapper 和 H2 验证业务，不在真实数据库插入测试用户。每条测试结束自动回滚。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {
    @Resource
    private UserService userService;
    @MockitoBean
    private AuthChallengeStore challengeStore;

    private UserAddRequest input(String account, String email) {
        UserAddRequest body = new UserAddRequest();
        body.setUserAccount(account);
        body.setUserName("测试用户");
        body.setEmail(email);
        body.setUserPassword("TestUser2026!");
        body.setGender(0);
        body.setPhone("00123");
        body.setStatus(0);
        body.setIdentity(0);
        return body;
    }

    private UserLoginRequest login(String account, String password) {
        UserLoginRequest input = new UserLoginRequest();
        input.setUserAccount(account);
        input.setUserPassword(password);
        return input;
    }

    private UserRegisterRequest registration(String account, String password, String confirmation, String email) {
        UserRegisterRequest input = new UserRegisterRequest();
        input.setUserAccount(account);
        input.setUserPassword(password);
        input.setCheckPassword(confirmation);
        input.setEmail(email);
        input.setVerificationCode("a1b2c3");
        return input;
    }

    @Test
    void newlyCreatedUserCanLogInWithInitialPassword() {
        long id = userService.addUser(input("member01", "member01@example.com"));
        User loggedIn = userService.userLogin(login("member01", "TestUser2026!"), new MockHttpServletRequest());
        assertEquals(id, loggedIn.getId());
        assertEquals(0, loggedIn.getGender());
        assertEquals("00123", loggedIn.getPhone());
        assertNull(loggedIn.getUserPassword());
        assertEquals("/Elaina1.jpeg", loggedIn.getAvatarUrl());
    }

    @Test
    void everySearchConditionIsAppliedAndZeroIsNotIgnored() {
        userService.addUser(input("member01", "member01@example.com"));
        UserAddRequest other = input("another01", "other@example.com");
        other.setGender(1);
        userService.addUser(other);
        UserQueryRequest query = new UserQueryRequest();
        query.setUserAccount("member");
        query.setUserName("测试");
        query.setPhone("001");
        query.setEmail("member01@");
        query.setGender(0);
        query.setStatus(0);
        query.setIdentity(0);
        var results = userService.searchUsers(query);
        assertEquals(1, results.size());
        assertEquals("member01", results.get(0).getUserAccount());
        query.setGender(1);
        assertTrue(userService.searchUsers(query).isEmpty());
    }

    @Test
    void editPreservesPasswordAndCanClearGenderThenSoftDeleteWorks() {
        UserAddRequest created = input("member01", "member01@example.com");
        long id = userService.addUser(created);
        UserUpdateRequest body = new UserUpdateRequest();
        org.springframework.beans.BeanUtils.copyProperties(created, body);
        String passwordHash = userService.getById(id).getUserPassword();
        body.setId(id);
        body.setUserName("新昵称");
        body.setGender(null);
        body.setStatus(1);
        assertTrue(userService.updateUser(body));
        User saved = userService.getById(id);
        assertEquals(passwordHash, saved.getUserPassword());
        assertEquals("新昵称", saved.getUserName());
        assertNull(saved.getGender());
        assertEquals(1, userService.searchUsers(new UserQueryRequest()).get(0).getStatus());
        assertThrows(BusinessException.class,
                () -> userService.userLogin(login("member01", "TestUser2026!"), new MockHttpServletRequest()));
        assertTrue(userService.removeById(id));
        assertNull(userService.getById(id));
        assertTrue(userService.searchUsers(new UserQueryRequest()).isEmpty());
    }

    @Test
    void registrationRejectsEmailAlreadyUsedByAnotherAccount() {
        userService.addUser(input("member01", "member01@example.com"));
        assertThrows(BusinessException.class,
                () -> userService.userRegister(registration("member02", "TestUser2026!", "TestUser2026!", "member01@example.com")));
    }

    @Test
    void invalidRegistrationIsAnExceptionRatherThanMinusOne() {
        assertThrows(BusinessException.class,
                () -> userService.userRegister(registration("", "", "", "test@example.com")));
        assertThrows(BusinessException.class,
                () -> userService.userRegister(registration("abc", "TestUser2026!", "TestUser2026!", "test@example.com")));
        assertThrows(BusinessException.class,
                () -> userService.userRegister(registration("member01", "short", "short", "test@example.com")));
    }

    @Test
    void directlyCallingServiceCannotCreateBannedUser() {
        UserAddRequest body = input("member01", "member01@example.com");
        body.setStatus(1);
        assertThrows(BusinessException.class, () -> userService.addUser(body));
        assertEquals(0, userService.count());
    }

    @Test
    void directlyCallingServiceStillChecksDtoEmailRule() {
        UserAddRequest body = input("member01", "invalid-email");
        assertThrows(BusinessException.class, () -> userService.addUser(body));
        assertEquals(0, userService.count());
    }

    @Test
    void directlyCallingLoginWithMissingPasswordIsParameterError() {
        assertThrows(BusinessException.class,
                () -> userService.userLogin(login("member01", null), new MockHttpServletRequest()));
    }

    @Test
    void addDefaultsToNormalWithoutImposingRegistrationPasswordStrength() {
        UserAddRequest body = input("member01", "member01@example.com");
        body.setStatus(null);
        body.setIdentity(null);
        body.setUserPassword("12345678");
        long id = userService.addUser(body);
        assertEquals(0, userService.getById(id).getStatus());
        assertEquals(0, userService.getById(id).getIdentity());
        assertEquals(id, userService.userLogin(login("member01", "12345678"), new MockHttpServletRequest()).getId());
    }

    @Test
    void invalidAvatarCannotBePersistedByDirectServiceCall() {
        UserAddRequest body = input("member01", "member01@example.com");
        body.setAvatarUrl("not-an-image-url");
        assertThrows(BusinessException.class, () -> userService.addUser(body));
        assertEquals(0, userService.count());
    }
}
