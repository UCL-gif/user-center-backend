package org.cookie.usercenter.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 图床令牌只从后端配置读取，不返回给浏览器。 */
@Data
@ConfigurationProperties(prefix = "image-hosting.beeimg")
public class BeeImgProperties {
    private String uploadUrl = "https://www.beeimg.cn/api/v2/upload";
    private String deleteUrl = "https://www.beeimg.cn/api/v2/user/photos";
    @ToString.Exclude
    private String token = "";
    private long storageId = 1;
    // 当前令牌只有上传权限，默认不尝试删除；取得删除权限后可开启简单补偿。
    private boolean cleanupOnSaveFailure = false;
}
