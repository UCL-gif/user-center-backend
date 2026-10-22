package org.cookie.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.cookie.usercenter.config.BeeImgProperties;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.cookie.usercenter.service.ImageHostingService;
import org.cookie.usercenter.service.UserAvatarService;
import org.cookie.usercenter.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** 图床 HTTP 放在数据库事务外；仅对已确认回滚的新图做一次可选清理。 */
@Service
@Slf4j
public class UserAvatarServiceImpl implements UserAvatarService {
    private final UserService users;
    private final ImageHostingService hosting;
    private final BeeImgProperties config;
    private final TransactionTemplate transaction;

    public UserAvatarServiceImpl(UserService users, ImageHostingService hosting,
                                 BeeImgProperties config, PlatformTransactionManager manager) {
        this.users = users; this.hosting = hosting; this.config = config;
        transaction = new TransactionTemplate(manager);
        // 独立完成保存，避免调用方外层事务尚未提交时就误判图片已保存。
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public long addUser(UserAddRequest request, MultipartFile avatar) {
        // multipart 的 URL 只信任图床返回值，客户端不能用此入口伪造地址。
        if (request != null) request.setAvatarUrl(null);
        users.validateAddRequest(request);
        var image = avatar == null ? null : hosting.upload(avatar);
        if (image != null) request.setAvatarUrl(image.url());
        return save(image, () -> users.addUser(request));
    }

    @Override
    public boolean updateUser(UserUpdateRequest request, MultipartFile avatar) {
        if (request != null) request.setAvatarUrl(null);
        // null URL 由 UserService 在写入时保留当时数据库中的头像。
        users.validateUpdateRequest(request);
        var image = avatar == null ? null : hosting.upload(avatar);
        if (image != null) request.setAvatarUrl(image.url());
        return save(image, () -> users.updateUser(request));
    }

    private <T> T save(ImageHostingService.UploadedImage image, Supplier<T> operation) {
        var completion = new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
        try {
            return transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int state) { completion.set(state); }
                });
                return operation.get();
            });
        } catch (RuntimeException e) {
            if (image != null) {
                if (completion.get() == TransactionSynchronization.STATUS_ROLLED_BACK) cleanAfterRollback(image);
                else log.warn("头像保存结果不确定，未自动删除图片，请人工核实，图床图片ID={}", image.id());
            }
            throw e;
        }
    }

    private void cleanAfterRollback(ImageHostingService.UploadedImage image) {
        if (!config.isCleanupOnSaveFailure()) {
            log.warn("用户保存已回滚，图床清理未开启，请人工核实新图片，图床图片ID={}", image.id());
            return;
        }
        try {
            // 图床可能复用同一张图片；已有用户仍引用此 URL 时不能删除。
            if (users.count(new QueryWrapper<User>().eq("avatar_url", image.url())) == 0) hosting.delete(image.id());
        } catch (RuntimeException ignored) {
            // 清理不能覆盖原来的保存错误，也不输出可能带令牌的远程异常正文。
            log.warn("新头像清理未完成，请到图床后台人工核实，图床图片ID={}", image.id());
        }
    }
}
