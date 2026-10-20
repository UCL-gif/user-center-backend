package org.cookie.usercenter.service;

import jakarta.servlet.http.HttpServletRequest;
import org.cookie.usercenter.model.domain.User;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * 用户服务
* @author NingBao
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-10-06 00:51:37
*/
public interface UserService extends IService<User> {
    
    
    
    /**
     * 用户注册
     * @param userAccount 用户输入的账户名
     * @param userPassword  用户输入的密码
     * @param checkPassword 确认的密码
     * @return  返回新用户的id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword,String emailAddress);
    
    /**
     *这个是登录的方法
     * @param userAccount   用户输入的账户
     * @param userPassword  用户输入的密码
     * @return 返回脱敏后的信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);
    
    User createCleanedUser(User originalUser);
    
    int userLogout(HttpServletRequest request);
    
}
