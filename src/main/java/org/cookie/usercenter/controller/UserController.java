package org.cookie.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cookie.usercenter.common.BaseResponse;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.common.ResultUtils;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.model.request.SendEmailRequest;
import org.cookie.usercenter.model.request.UserLoginRequest;
import org.cookie.usercenter.model.request.UserRegisterRequest;
import org.cookie.usercenter.service.EmailService;
import org.cookie.usercenter.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.cookie.usercenter.constant.userConstant.ADMIN_ROLE;
import static org.cookie.usercenter.constant.userConstant.USER_LOGIN_STATE;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @Resource
    private UserService userService;
    @Resource
    private EmailService emailService;
    
    /**
     * 该方法是用来处理用户点击发送验证码的方法
     * @param request 接收用户请求
     */
    @PostMapping("/sendEmail")
    public BaseResponse<Boolean> sendEmailCode(@RequestBody SendEmailRequest request){
        try {
            boolean success = emailService.sendEmailCode(request.getEmail());
            if (success) {
                return ResultUtils.success(true);
            }
        } catch (MessagingException e) {
            log.error("an error occurred when sending an email, please check!");
            throw new RuntimeException(e);
        }
        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR,"发送失败，请检查邮箱格式是否正确");
    }
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            return ResultUtils.error(ErrorCode.PARAMETERS_ERROR);
        }
        
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String emailAddress = userRegisterRequest.getEmail();
        String verificationCode = userRegisterRequest.getVerificationCode();
        if (StringUtils.isAnyEmpty(userAccount, userPassword, checkPassword, emailAddress, verificationCode)) {
            return null;
        }
        
        if (emailService.SuccessCheckEmailCode(emailAddress,verificationCode)) {
            long result = userService.userRegister(userAccount, userPassword, checkPassword, emailAddress);
            return ResultUtils.success(result);
        } else{
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "邮箱验证码错误");
        }
        
    }
    
    @PostMapping("/login")
    public  BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.NO_LOGIN, "请先登录");
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        
        
        if (StringUtils.isAnyEmpty(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMETERS_NULL_ERROR, "用户的账号或密码为空");
        }
        
        if (userService.userLogin(userAccount, userPassword, request) == null) {
            
            throw new BusinessException(ErrorCode.PARAMETERS_NULL_ERROR, "用户不存在");
        }
        return ResultUtils.success(userService.userLogin(userAccount, userPassword, request));
    }
    
    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(   HttpServletRequest request) {
        if (request == null) {
            return ResultUtils.error(ErrorCode.NO_LOGIN, "当前未登录");
        }
        int result = userService.userLogout(request);
        return ResultUtils.success(result);
    }
    
    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request){
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        
        if (currentUser == null) {
            return ResultUtils.error(ErrorCode.NO_LOGIN, "请先登录");
        }
        
        //todo verify user identity
        Long userId = currentUser.getId();
        User user = userService.getById(userId);
        
        User cleanedUser = userService.createCleanedUser(user);
        return ResultUtils.success(cleanedUser);
    }
    
    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String userName, HttpServletRequest request) {
        
        boolean hasAdminPrivilege = isAdmin(request);
        if (hasAdminPrivilege) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            //有搜索词才给盒子加条件；没有搜索词就让盒子保持为空，再调用 list 查询全部
            if (StringUtils.isNotBlank(userName)) {
                queryWrapper.like("user_name", userName);
            }
            List<User> userList = userService.list(queryWrapper);
            //把查出的每个用户对象里的密码设为 null，再组成一个列表返回
            List<User> list = userList.stream().map(user -> userService.createCleanedUser(user)).toList();
            return ResultUtils.success(list);
        } else {
            throw new BusinessException(ErrorCode.NO_PRIVILEGE, "没有管理员权限");
        }
        
    }
    
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id,HttpServletRequest request) {
        boolean hasAdminPrivilege = isAdmin(request);
        if (!hasAdminPrivilege) {
            return null;
        }
        if (id <= 0) {
            return null;
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }
    
    
    private boolean isAdmin(HttpServletRequest request){
        //查询需要有管理员权限
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getIdentity() == ADMIN_ROLE;
    }
}
