package org.cookie.usercenter.controller;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.cookie.usercenter.common.BaseResponse;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.common.ResultUtils;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.model.request.SendEmailRequest;
import org.cookie.usercenter.model.request.UserLoginRequest;
import org.cookie.usercenter.model.request.UserRegisterRequest;
import org.cookie.usercenter.model.request.UserQueryRequest;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.cookie.usercenter.service.EmailService;
import org.cookie.usercenter.service.UserService;
import org.cookie.usercenter.service.UserAvatarService;
import org.cookie.usercenter.service.AuthSessionService;
import org.cookie.usercenter.service.PasswordResetService;
import org.cookie.usercenter.model.request.PasswordResetRequest;
import org.cookie.usercenter.model.request.PasswordResetVerifyRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.cookie.usercenter.constant.userConstant.ADMIN_ROLE;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private EmailService emailService;
    @Resource
    private AuthSessionService authSessionService;
    @Resource
    private PasswordResetService passwordResetService;
    @Resource
    private UserAvatarService userAvatarService;

    /**
     * 该方法是用来处理用户点击发送验证码的方法
     * @param request 接收用户请求
     */
    @PostMapping("/sendEmail")
    public BaseResponse<Boolean> sendEmailCode(@Valid @RequestBody SendEmailRequest request){
        try {
            boolean success = emailService.sendEmailCode(request.getEmail());
            if (success) {
                return ResultUtils.success(true);
            }
        } catch (MessagingException e) {
            log.error("an error occurred when sending an email, please check!");
            throw new RuntimeException(e);
        }
        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR,"验证码已发送，请稍后再试");
    }
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {

        return ResultUtils.success(userService.userRegister(userRegisterRequest));
    }

    @PostMapping("/password/reset/code")
    public BaseResponse<Boolean> sendResetCode(@Valid @RequestBody SendEmailRequest body) {
        return ResultUtils.success(passwordResetService.sendCode(body));
    }

    @PostMapping("/password/reset/verify")
    public BaseResponse<PasswordResetService.ResetAuthorization> verifyResetCode(
            @Valid @RequestBody PasswordResetVerifyRequest body) {
        return ResultUtils.success(passwordResetService.verifyEmail(body));
    }

    @PostMapping("/password/reset")
    public BaseResponse<Boolean> resetPassword(@Valid @RequestBody PasswordResetRequest body) {
        return ResultUtils.success(passwordResetService.resetPassword(body));
    }

    @PostMapping("/login")
    public BaseResponse<User> userLogin(@Valid @RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {

        return ResultUtils.success(userService.userLogin(userLoginRequest, request));
    }

    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(HttpServletRequest request) {
        if (request == null) {
            return ResultUtils.error(ErrorCode.NO_LOGIN, "当前未登录");
        }
        int result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request){
        return ResultUtils.success(userService.createCleanedUser(requireLogin(request)));
    }

    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(UserQueryRequest query, HttpServletRequest request) {
        requireAdmin(request);
        return ResultUtils.success(userService.searchUsers(query));
    }

    /** 管理员新增用户，不走公开注册的邮箱验证码流程。 */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<Long> addUser(@Valid @RequestBody UserAddRequest body, HttpServletRequest request) {
        requireAdmin(request);
        return ResultUtils.success(userService.addUser(body));
    }

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<Long> addUserWithAvatar(@Valid @RequestPart("user") UserAddRequest body,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar, HttpServletRequest request) {
        requireAdmin(request);
        return ResultUtils.success(userAvatarService.addUser(body, avatar));
    }

    @PostMapping(value = "/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<Boolean> updateUser(@Valid @RequestBody UserUpdateRequest body, HttpServletRequest request) {
        validateSelfUpdate(requireAdmin(request), body);
        return ResultUtils.success(userService.updateUser(body));
    }

    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<Boolean> updateUserWithAvatar(@Valid @RequestPart("user") UserUpdateRequest body,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar, HttpServletRequest request) {
        validateSelfUpdate(requireAdmin(request), body);
        return ResultUtils.success(userAvatarService.updateUser(body, avatar));
    }

    private void validateSelfUpdate(User operator, UserUpdateRequest body) {
        if (operator.getId().equals(body.getId())
                && (Integer.valueOf(1).equals(body.getStatus()) || Integer.valueOf(0).equals(body.getIdentity()))) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "不能封禁自己或取消自己的管理员权限");
        }
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id,HttpServletRequest request) {
        User operator = requireAdmin(request);
        if (id <= 0 || operator.getId().equals(id)) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "用户 ID 无效或正在删除自己");
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }


    @PostMapping("/delete/batch")
    public BaseResponse<Boolean> deleteUsers(@RequestBody List<Long> ids, HttpServletRequest request) {
        User operator = requireAdmin(request);
        if (ids == null || ids.isEmpty() || ids.size() > 100
                || ids.stream().anyMatch(id -> id == null || id <= 0 || id.equals(operator.getId()))) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "请选择 1～100 个其他用户，不能删除自己");
        }
        return ResultUtils.success(userService.removeByIds(ids.stream().distinct().toList()));
    }

    private User requireLogin(HttpServletRequest request) {
        return authSessionService.requireUser(request);
    }

    private User requireAdmin(HttpServletRequest request) {
        User current = requireLogin(request);
        if (!Integer.valueOf(ADMIN_ROLE).equals(current.getIdentity())) {
            throw new BusinessException(ErrorCode.NO_PRIVILEGE, "没有管理员权限");
        }
        return current;
    }
}
