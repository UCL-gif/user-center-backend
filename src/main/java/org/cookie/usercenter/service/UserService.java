package org.cookie.usercenter.service;

import jakarta.servlet.http.HttpServletRequest;
import org.cookie.usercenter.model.domain.User;
import com.baomidou.mybatisplus.spring.service.IService;
import org.cookie.usercenter.model.request.UserQueryRequest;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.cookie.usercenter.model.request.UserLoginRequest;
import org.cookie.usercenter.model.request.UserRegisterRequest;
import java.util.List;

/**
 * 用户服务
* @author NingBao
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-10-06 00:51:37
*/
public interface UserService extends IService<User> {



    /**
     * 用户注册
     * @param request 注册数据；邮箱验证码的真伪由注册入口检查
     * @return  返回新用户的id
     */
    long userRegister(UserRegisterRequest request);

    /**
     *这个是登录的方法
     * @param input 用户输入的账号和密码
     * @return 返回脱敏后的信息
     */
    User userLogin(UserLoginRequest input, HttpServletRequest request);

    User createCleanedUser(User originalUser);

    int userLogout(HttpServletRequest request);

    List<User> searchUsers(UserQueryRequest query);

    long addUser(UserAddRequest request);

    /** 上传前检查资料；真正写入前 addUser 仍会重新检查。 */
    void validateAddRequest(UserAddRequest request);

    /** 上传前检查资料；真正写入前 updateUser 仍会重新检查。 */
    User validateUpdateRequest(UserUpdateRequest request);

    boolean updateUser(UserUpdateRequest request);

}
