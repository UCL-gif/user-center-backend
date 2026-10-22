package org.cookie.usercenter.service;

import org.springframework.web.multipart.MultipartFile;

/** 对图床的访问契约，与用户资料保存分开。 */
public interface ImageHostingService {
    UploadedImage upload(MultipartFile file);
    void delete(long imageId);
    record UploadedImage(long id, String url) { }
}
