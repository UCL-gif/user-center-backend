package org.cookie.usercenter.service;

import org.cookie.usercenter.config.BeeImgProperties;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.service.impl.UserAvatarServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class UserAvatarCommitFailureTest {
    @Test void uncertainCommitNeverDeletesPotentiallyCommittedAvatar() throws Exception {
        UserService users = mock(UserService.class);
        ImageHostingService hosting = mock(ImageHostingService.class);
        var config = new BeeImgProperties(); config.setCleanupOnSaveFailure(true);
        var commitFailure = new TransactionSystemException("提交结果不确定");
        // 模拟 JDBC 在提交时连接丢失：不能据此断定数据库已经回滚。
        var manager = new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object tx, TransactionDefinition def) { }
            protected void doCommit(DefaultTransactionStatus status) { throw commitFailure; }
            protected void doRollback(DefaultTransactionStatus status) { fail("未知提交结果不能假设回滚"); }
        };
        var service = new UserAvatarServiceImpl(users, hosting, config, manager);
        when(hosting.upload(any())).thenReturn(new ImageHostingService.UploadedImage(123, "https://images.example.com/new.png"));
        when(users.addUser(any())).thenReturn(9L);
        assertSame(commitFailure, assertThrows(TransactionSystemException.class,
                () -> service.addUser(new UserAddRequest(), ImageHostingServiceTest.png())));
        verify(hosting, never()).delete(anyLong());
    }
}
