package org.cookie.usercenter.controller;

import org.cookie.usercenter.service.impl.AuthSessionServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.cookie.usercenter.exceptions.GlobalExceptionHandler;
import org.cookie.usercenter.mapper.UserMapper;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.service.EmailService;
import org.cookie.usercenter.service.AuthSessionService;
import org.cookie.usercenter.service.AuthChallengeStore;
import org.cookie.usercenter.testsupport.TestSessionConfiguration.MemoryIndexedSessionRepository;
import org.cookie.usercenter.utils.PasswordUtils;
import org.cookie.usercenter.service.impl.UserServiceImpl;
import org.cookie.usercenter.utils.RequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.cookie.usercenter.constant.userConstant.USER_LOGIN_STATE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 只替换数据库边界：真实执行控制器、权限检查、校验和密码处理，不连接真实数据库。 */
class UserManagementTest {
    private UserMapper mapper;
    private UserServiceImpl service;
    private MockMvc mvc;
    private MockHttpSession adminSession;
    private ValidatorFactory validatorFactory;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        validatorFactory = Validation.buildDefaultValidatorFactory();
        RequestValidator requestValidator = new RequestValidator();
        ReflectionTestUtils.setField(requestValidator, "validator", validatorFactory.getValidator());
        ReflectionTestUtils.setField(service, "requestValidator", requestValidator);
        AuthSessionService sessions = new AuthSessionServiceImpl(mapper, new MemoryIndexedSessionRepository(), mock(AuthChallengeStore.class));
        ReflectionTestUtils.setField(service, "authSessionService", sessions);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "authSessionService", sessions);
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "emailService", mock(EmailService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        User admin = user(1L, "admin", 1);
        when(mapper.selectById(1L)).thenReturn(admin);
        adminSession = new MockHttpSession();
        authenticate(adminSession, admin);
    }

    @AfterEach
    void closeValidator() {
        validatorFactory.close();
    }

    private User user(long id, String account, int role) {
        User user = new User();
        user.setId(id);
        user.setUserAccount(account);
        user.setIdentity(role);
        user.setStatus(0);
        user.setDeleted(0);
        user.setUserPassword("test-password-hash");
        return user;
    }

    private void authenticate(MockHttpSession session, User user) {
        session.setAttribute(AuthSessionService.USER_ID_ATTRIBUTE, user.getId());
        session.setAttribute(AuthSessionService.CREDENTIAL_STAMP_ATTRIBUTE, PasswordUtils.sha256(user.getUserPassword()));
        session.setAttribute(AuthSessionService.CREDENTIAL_EPOCH_ATTRIBUTE, 0L);
    }

    private String createBody() {
        return """
                {"userAccount":"newuser","userPassword":"TestUser2026!","userName":"新用户",
                 "gender":0,"email":"new@example.com","phone":"00123","identity":0,"status":0}
                """;
    }

    @Test
    void anonymousCannotCreateUsers() throws Exception {
        mvc.perform(post("/api/user/add").contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40100));
        verify(mapper, never()).insert(any(User.class));
    }

    @Test
    void ordinaryUserCannotCreateAdministrators() throws Exception {
        User ordinary = user(2L, "ordinary", 0);
        when(mapper.selectById(2L)).thenReturn(ordinary);
        MockHttpSession session = new MockHttpSession();
        authenticate(session, ordinary);
        mvc.perform(post("/api/user/add").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void administratorCreatesLoginCompatiblePasswordAndKeepsGenderZero() throws Exception {
        when(mapper.insert(any(User.class))).thenAnswer(call -> {
            call.getArgument(0, User.class).setId(7L);
            return 1;
        });
        mvc.perform(post("/api/user/add").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(7));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(mapper).insert(saved.capture());
        assertEquals(0, saved.getValue().getGender());
        assertEquals("00123", saved.getValue().getPhone());
        assertEquals("425c93d4e11dd3d9609b868e11ef16a5", saved.getValue().getUserPassword());
    }

    @Test
    void duplicateAccountAndInvalidGenderCannotBeSaved() throws Exception {
        when(mapper.selectCount(any())).thenReturn(1L);
        mvc.perform(post("/api/user/add").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(jsonPath("$.code").value(40000));
        reset(mapper);
        when(mapper.selectById(1L)).thenReturn(user(1, "admin", 1));
        mvc.perform(post("/api/user/add").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody().replace("\"gender\":0", "\"gender\":9")))
                .andExpect(jsonPath("$.code").value(40000));
        verify(mapper, never()).insert(any(User.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void combinedSearchIncludesEveryConditionAndZeroValues() throws Exception {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        mvc.perform(get("/api/user/search").session(adminSession)
                        .param("userAccount", "new").param("userName", "用户").param("phone", "001")
                        .param("email", "example.com").param("gender", "0").param("status", "0")
                        .param("identity", "0"))
                .andExpect(jsonPath("$.code").value(0));
        ArgumentCaptor<QueryWrapper<User>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        for (String column : List.of("user_account", "user_name", "phone", "email", "gender", "status", "identity")) {
            assertTrue(sql.contains(column), "查询遗漏字段：" + column);
        }
        assertFalse(sql.contains(" OR "));
    }

    @Test
    void editingUsesSelectedUserAndDoesNotEraseExistingPassword() throws Exception {
        User existing = user(2, "member", 0);
        existing.setUserPassword("existing-password-hash");
        when(mapper.selectById(2L)).thenReturn(existing);
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        mvc.perform(post("/api/user/update").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2,"userAccount":"member","userName":"修改后的昵称",
                                 "email":"member@example.com","gender":1,"status":1,"identity":0}
                                """))
                .andExpect(jsonPath("$.code").value(0));
        ArgumentCaptor<UpdateWrapper<User>> saved = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), saved.capture());
        assertFalse(saved.getValue().getSqlSet().contains("user_password"),
                "编辑资料的 SQL 不能覆盖并发重置后的密码");
        assertFalse(saved.getValue().getSqlSet().contains("avatar_url"),
                "未提交新头像时，SQL 不能覆盖其他窗口刚更新的头像");
        assertTrue(saved.getValue().getSqlSegment().contains("id"));
        assertTrue(saved.getValue().getParamNameValuePairs().containsValue("修改后的昵称"));
        assertTrue(saved.getValue().getParamNameValuePairs().containsValue(2L));
    }

    @Test
    void bannedUserCannotContinueUsingAnOldSession() throws Exception {
        User banned = user(1, "admin", 1);
        banned.setStatus(1);
        when(mapper.selectById(1L)).thenReturn(banned);
        mvc.perform(get("/api/user/search").session(adminSession))
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void publicUserDataKeepsActualStatusAndNeverLeaksPassword() {
        User original = user(2, "member", 0);
        original.setStatus(1);
        original.setUserPassword("private-password-hash");
        User result = service.createCleanedUser(original);
        assertEquals(1, result.getStatus());
        assertNull(result.getUserPassword());
    }

    @Test
    void logoutInvalidatesSessionRatherThanLeavingOtherAttributesAlive() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(USER_LOGIN_STATE, user(1, "admin", 1));
        request.setSession(session);
        assertEquals(1, service.userLogout(request));
        assertTrue(session.isInvalid());
    }

    @Test
    void missingBodyAndMalformedJsonHaveReadableParameterError() throws Exception {
        for (String body : List.of("", "{broken-json")) {
            mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.description").value("请求体不能为空或JSON格式不正确"));
        }
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void administratorCannotCreateBannedUser() throws Exception {
        mvc.perform(post("/api/user/add").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody().replace("\"status\":0", "\"status\":1")))
                .andExpect(jsonPath("$.code").value(40000));
        verify(mapper, never()).insert(any(User.class));
    }

    @Test
    void editingRequiresAnExistingPositiveIdBeforeAnyWrite() throws Exception {
        for (String idField : List.of("", "\"id\":0,", "\"id\":-1,")) {
            mvc.perform(post("/api/user/update").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                            .content("{" + idField + "\"userAccount\":\"member\",\"email\":\"member@example.com\",\"status\":0,\"identity\":0}"))
                    .andExpect(jsonPath("$.code").value(40000));
        }
        verify(mapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void initialPasswordMustBePresentAndWithinAddLimit() throws Exception {
        for (String password : List.of("", "short", "a".repeat(21))) {
            mvc.perform(post("/api/user/add").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody().replace("TestUser2026!", password)))
                    .andExpect(jsonPath("$.code").value(40000));
        }
        verify(mapper, never()).insert(any(User.class));
    }
}
