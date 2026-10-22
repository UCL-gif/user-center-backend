package org.cookie.usercenter.controller;

import org.cookie.usercenter.exceptions.*;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserAvatarControllerTest {
    MockMvc mvc;
    UserAvatarService avatars;
    AuthSessionService sessions;
    final String create = """
        {"userAccount":"newuser","email":"new@example.com","userPassword":"TestUser2026!"}
        """;
    @BeforeEach void setup() {
        var controller = new UserController();
        avatars = mock(UserAvatarService.class); sessions = mock(AuthSessionService.class);
        ReflectionTestUtils.setField(controller, "authSessionService", sessions);
        ReflectionTestUtils.setField(controller, "userAvatarService", avatars);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        var admin = new User(); admin.setId(1L); admin.setIdentity(1);
        when(sessions.requireUser(any())).thenReturn(admin);
    }
    MockMultipartFile json(String value) { return new MockMultipartFile("user", "", "application/json", value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    MockMultipartFile avatar() { return new MockMultipartFile("avatar", "face.png", "image/png", new byte[]{1}); }
    @Test void multipartCreateUsesSameEndpointAndResponseShape() throws Exception {
        when(avatars.addUser(any(), any())).thenReturn(99L);
        mvc.perform(multipart("/api/user/add").file(json(create)).file(avatar()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(99));
        verify(avatars).addUser(argThat(body -> "newuser".equals(body.getUserAccount())), any());
    }
    @Test void validationRunsBeforeImageUpload() throws Exception {
        mvc.perform(multipart("/api/user/add").file(json(create.replace("new@example.com", "invalid"))).file(avatar()))
                .andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(avatars);
    }
    @Test void anonymousCannotUpload() throws Exception {
        when(sessions.requireUser(any())).thenThrow(new BusinessException(ErrorCode.NO_LOGIN));
        mvc.perform(multipart("/api/user/add").file(json(create)).file(avatar()))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_LOGIN.getCode()));
        verifyNoInteractions(avatars);
    }
    @Test void ordinaryUserCannotUpload() throws Exception {
        var member = new User(); member.setId(2L); member.setIdentity(0);
        when(sessions.requireUser(any())).thenReturn(member);
        mvc.perform(multipart("/api/user/add").file(json(create)).file(avatar()))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_PRIVILEGE.getCode()));
        verifyNoInteractions(avatars);
    }
    @Test void updateRetainsSelfProtectionAndAllowsEditingOthers() throws Exception {
        String update = """
            {"id":1,"userAccount":"newuser","email":"new@example.com","status":1,"identity":0}
            """;
        mvc.perform(multipart("/api/user/update").file(json(update)).file(avatar()))
                .andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(avatars);
        when(avatars.updateUser(any(), any())).thenReturn(true);
        mvc.perform(multipart("/api/user/update").file(json(update.replace("\"id\":1", "\"id\":2"))).file(avatar()))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(true));
    }
    @Test void missingUserPartReturnsReadableValidationError() throws Exception {
        mvc.perform(multipart("/api/user/add").file(avatar()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(avatars);
    }
}
