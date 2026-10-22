package org.cookie.usercenter.service;

import jakarta.annotation.Resource;
import org.cookie.usercenter.config.BeeImgProperties;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 真正提交/回滚 H2 事务，仅图床 HTTP 使用替身，不接触真实数据库或图床。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:avatartest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,IDENTITY;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class UserAvatarServiceTest {
    @Resource UserAvatarService avatars;
    @MockitoSpyBean UserService users;
    @MockitoBean ImageHostingService hosting;
    @MockitoBean AuthChallengeStore challenges;
    @Resource BeeImgProperties config;
    @Resource JdbcTemplate jdbc;
    private final ImageHostingService.UploadedImage uploaded =
            new ImageHostingService.UploadedImage(123L, "https://images.example.com/avatar.png");

    @BeforeEach void prepare() { config.setCleanupOnSaveFailure(false); }
    @AfterEach void clean() { jdbc.update("DELETE FROM `user`"); }

    private UserAddRequest input() {
        var body = new UserAddRequest();
        body.setUserAccount("avataruser"); body.setEmail("avatar@example.com");
        body.setUserPassword("TestUser2026!"); body.setIdentity(0);
        return body;
    }
    private MockMultipartFile file() {
        try { return ImageHostingServiceTest.png(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private UserUpdateRequest edit(long id) {
        var body = new UserUpdateRequest();
        body.setId(id); body.setUserAccount("avataruser"); body.setEmail("avatar@example.com");
        body.setStatus(0); body.setIdentity(0); return body;
    }
    @Test void createCommitsProviderUrlInsteadOfClientSuppliedUrl() {
        when(hosting.upload(any())).thenReturn(uploaded);
        var body = input(); body.setAvatarUrl("https://forged.example.com/fake.png");
        long id = avatars.addUser(body, file());
        assertEquals(uploaded.url(), users.getById(id).getAvatarUrl());
        assertEquals(0, users.getById(id).getStatus());
        verify(hosting, never()).delete(anyLong());
    }
    @Test void editWithoutNewFilePreservesExistingAvatarAndPassword() {
        var original = input(); original.setAvatarUrl("https://images.example.com/original.png");
        long id = users.addUser(original);
        String password = users.getById(id).getUserPassword();
        var edit = edit(id); edit.setAvatarUrl("https://forged.example.com/ignored.png");
        assertTrue(avatars.updateUser(edit, null));
        assertEquals(original.getAvatarUrl(), users.getById(id).getAvatarUrl());
        assertEquals(password, users.getById(id).getUserPassword());
        verifyNoInteractions(hosting);
    }
    @Test void rejectsInvalidOrDuplicateDataBeforeUpload() {
        var bad = input(); bad.setEmail("invalid");
        assertThrows(BusinessException.class, () -> avatars.addUser(bad, file()));
        users.addUser(input());
        assertThrows(BusinessException.class, () -> avatars.addUser(input(), file()));
        verifyNoInteractions(hosting);
    }
    @Test void failedUploadDoesNotChangeUser() {
        long id = users.addUser(input());
        when(hosting.upload(any())).thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "图床暂不可用"));
        var edit = edit(id); edit.setUserName("不应保存");
        assertThrows(BusinessException.class, () -> avatars.updateUser(edit, file()));
        assertEquals("", users.getById(id).getUserName());
        assertEquals("/Elaina1.jpeg", users.getById(id).getAvatarUrl());
    }
    private BusinessException failAfterInsert() {
        var failure = new BusinessException(ErrorCode.SYSTEM_ERROR, "模拟数据库失败");
        doAnswer(call -> { call.callRealMethod(); throw failure; }).when(users).addUser(any());
        when(hosting.upload(any())).thenReturn(uploaded);
        return failure;
    }
    @Test void defaultUploadOnlyTokenDoesNotAttemptDeletionOnRollback() {
        var failure = failAfterInsert();
        assertSame(failure, assertThrows(BusinessException.class, () -> avatars.addUser(input(), file())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
        verify(hosting, never()).delete(anyLong());
    }
    @Test void confirmedRollbackDeletesNewImageOnceWhenEnabled() {
        config.setCleanupOnSaveFailure(true);
        var failure = failAfterInsert();
        assertSame(failure, assertThrows(BusinessException.class, () -> avatars.addUser(input(), file())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
        verify(hosting).delete(123L);
    }
    @Test void failedCleanupDoesNotHideOriginalDatabaseError() {
        config.setCleanupOnSaveFailure(true);
        var failure = failAfterInsert();
        doThrow(new RuntimeException("模拟删除失败")).when(hosting).delete(123L);
        assertSame(failure, assertThrows(BusinessException.class, () -> avatars.addUser(input(), file())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
        verify(hosting).delete(123L);
    }
    @Test void rollbackDoesNotDeleteImageAlreadyReferencedByAnotherUser() {
        config.setCleanupOnSaveFailure(true);
        var existing = input(); existing.setUserAccount("existinguser");
        existing.setEmail("existing@example.com"); existing.setAvatarUrl(uploaded.url());
        long id = users.addUser(existing);
        failAfterInsert();
        assertThrows(BusinessException.class, () -> avatars.addUser(input(), file()));
        assertEquals(uploaded.url(), users.getById(id).getAvatarUrl());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
        verify(hosting, never()).delete(anyLong());
    }
}
