package org.cookie.usercenter.service;

import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

/** 管理员保存用户及可选的新头像；文件上传与数据库事务的协调入口。 */
public interface UserAvatarService {
    long addUser(UserAddRequest request, MultipartFile avatar);
    boolean updateUser(UserUpdateRequest request, MultipartFile avatar);
}
