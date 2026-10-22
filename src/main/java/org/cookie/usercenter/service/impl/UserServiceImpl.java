package org.cookie.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.mapper.UserMapper;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.model.request.UserQueryRequest;
import org.cookie.usercenter.model.request.UserAddRequest;
import org.cookie.usercenter.model.request.UserUpdateRequest;
import org.cookie.usercenter.model.request.UserLoginRequest;
import org.cookie.usercenter.model.request.UserRegisterRequest;
import org.cookie.usercenter.utils.RequestValidator;
import org.cookie.usercenter.service.UserService;
import org.cookie.usercenter.service.EmailService;
import org.cookie.usercenter.service.CodePurpose;
import org.cookie.usercenter.service.AuthSessionService;
import static org.cookie.usercenter.utils.PasswordUtils.hashPassword;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 用户服务实现类
 *
 * @author NingBao
 * @createDate 2026-10-06 00:51:37
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private RequestValidator requestValidator;
    @Resource
    private EmailService emailService;
    @Resource
    private AuthSessionService authSessionService;

    @Override
    public long userRegister(UserRegisterRequest request) {
        requestValidator.validate(request);
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        String emailAddress = request.getEmail();

        //校验密码
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "两次输入的密码不匹配");
        }
        //账户不能重复，去数据库查询
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "账户已存在");
        }
        //邮箱不能重复
        //邮箱需要单独查询；不能和上面的账号条件组合成 AND，否则会漏掉重复邮箱。
        Long emailExistResult = userMapper.selectCount(new QueryWrapper<User>().eq("email", emailAddress));
        if (emailExistResult > 0) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "邮箱已存在");
        }

        // 先完成字段、密码一致性和重复账号检查，再一次性消费注册验证码。
        emailService.verifyCode(emailAddress, request.getVerificationCode(), CodePurpose.REGISTER);

        //2.加密
        String encryptPassword = hashPassword(userPassword);

        //3.插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setEmail(emailAddress);
        user.setStatus(0);
        user.setIdentity(0);
        user.setDeleted(0);
        int saveResult = userMapper.insert(user);
        if (saveResult <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }
        return user.getId();
    }

    /**
     *
     * @param input 用户输入的账号和密码
     * @param request      tomcat传来的请求包裹
     * @return 返回脱敏后的用户信息
     */

    @Override
    public User userLogin(UserLoginRequest input, HttpServletRequest request) {
        requestValidator.validate(input);
        String userAccount = input.getUserAccount();
        String userPassword = input.getUserPassword();

        //加密
        String encryptPassword = hashPassword(userPassword);

        //5)账户是否存在，去数据库查询
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        queryWrapper.eq("user_password", encryptPassword);

        User originalUser = userMapper.selectOne(queryWrapper);
        //用户不存在
        if (originalUser == null) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "账号或密码错误");
        } else {
            if (originalUser.getStatus() != null && originalUser.getStatus() != 0) {
                throw new BusinessException(ErrorCode.NO_PRIVILEGE, "账号已被封禁");
            }
            //用户脱敏
            User cleanedUser = createCleanedUser(originalUser);

            //2.用户登录状态
            authSessionService.login(originalUser, request, Boolean.TRUE.equals(input.getAutoLogin()));

            return cleanedUser;
        }


    }

    /**
     * 用户脱敏方法
     *
     * @param originalUser 查询到的用户数据
     * @return 返回脱敏后的用户数据
     */
    @Override
    public User createCleanedUser(User originalUser) {
        if (originalUser == null) {
            return null;
        }
        User cleanedUser = new User();
        cleanedUser.setId(originalUser.getId());
        cleanedUser.setUserName(originalUser.getUserName());
        cleanedUser.setUserAccount(originalUser.getUserAccount());
        cleanedUser.setAvatarUrl(originalUser.getAvatarUrl());
        cleanedUser.setIdentity(originalUser.getIdentity());
        cleanedUser.setGender(originalUser.getGender());
        cleanedUser.setPhone(originalUser.getPhone());
        cleanedUser.setEmail(originalUser.getEmail());
        cleanedUser.setStatus(originalUser.getStatus());
        cleanedUser.setCreateTime(originalUser.getCreateTime());
        return cleanedUser;
    }

    /**
     * 该方法用户注销
     *
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        authSessionService.logout(request);
        return 1;

    }

    @Override
    public List<User> searchUsers(UserQueryRequest query) {
        if (query == null) query = new UserQueryRequest();
        validateChoice(query.getGender(), "性别");
        validateChoice(query.getStatus(), "状态");
        validateChoice(query.getIdentity(), "角色");
        // QueryWrapper 使用参数绑定，多个条件默认是 AND；不要把用户输入拼进 SQL。
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        addLike(wrapper, "user_account", query.getUserAccount());
        addLike(wrapper, "user_name", query.getUserName());
        addLike(wrapper, "phone", query.getPhone());
        addLike(wrapper, "email", query.getEmail());
        wrapper.eq(query.getGender() != null, "gender", query.getGender());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        wrapper.eq(query.getIdentity() != null, "identity", query.getIdentity());
        wrapper.orderByDesc("id");
        return userMapper.selectList(wrapper).stream().map(this::createCleanedUser).toList();
    }

    @Override
    public long addUser(UserAddRequest request) {
        validateAddRequest(request);
        User user = new User();
        copyProfile(request.getUserAccount(), request.getUserName(), request.getPhone(),
                request.getEmail(), request.getAvatarUrl(), request.getGender(), user);
        // 新增不接受自定义 ID；状态由业务明确设为正常，不能创建封禁用户。
        user.setStatus(0);
        user.setIdentity(request.getIdentity() == null ? 0 : request.getIdentity());
        user.setUserPassword(hashPassword(request.getUserPassword()));
        user.setDeleted(0);
        if (userMapper.insert(user) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "新增用户失败");
        }
        return user.getId();
    }

    @Override
    public boolean updateUser(UserUpdateRequest request) {
        User user = validateUpdateRequest(request);
        copyProfile(request.getUserAccount(), request.getUserName(), request.getPhone(),
                request.getEmail(), request.getAvatarUrl() == null ? user.getAvatarUrl() : request.getAvatarUrl(), request.getGender(), user);
        user.setStatus(request.getStatus());
        user.setIdentity(request.getIdentity());
        // 明确列出允许更新的字段。不能把读取到的整个实体写回：
        // 如果读取后恰好发生了密码重置，整实体更新会把旧密码覆盖回去。
        UpdateWrapper<User> updates = new UpdateWrapper<User>().eq("id", user.getId())
                .set("user_account", user.getUserAccount()).set("user_name", user.getUserName())
                .set("phone", user.getPhone()).set("email", user.getEmail())
                // 没有新头像时不写这一列，保留其他窗口可能刚更新的值。
                .set(request.getAvatarUrl() != null, "avatar_url", user.getAvatarUrl()).set("gender", user.getGender())
                .set("status", user.getStatus()).set("identity", user.getIdentity())
                .set("update_time", user.getUpdateTime());
        if (userMapper.update(null, updates) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存用户失败");
        }
        return true;
    }

    @Override
    public void validateAddRequest(UserAddRequest request) {
        requestValidator.validate(request);
        validateAvatar(request.getAvatarUrl());
        ensureUnique(request.getUserAccount(), request.getEmail(), null);
    }

    @Override
    public User validateUpdateRequest(UserUpdateRequest request) {
        requestValidator.validate(request);
        validateAvatar(request.getAvatarUrl());
        User user = userMapper.selectById(request.getId());
        if (user == null) throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "用户不存在或已被删除");
        ensureUnique(request.getUserAccount(), request.getEmail(), user.getId());
        return user;
    }

    private void addLike(QueryWrapper<User> wrapper, String column, String value) {
        if (StringUtils.isNotBlank(value)) {
            if (value.length() > 512) throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "查询条件过长");
            wrapper.like(column, value.trim());
        }
    }

    private void validateChoice(Integer value, String name) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, name + "只能选择 0 或 1");
        }
    }

    /** 长度由 DTO 校验；这里保留支持的头像地址形式检查。 */
    private void validateAvatar(String value) {
        String avatar = StringUtils.trimToEmpty(value);
        if (!avatar.isEmpty() && !avatar.matches("https?://[^\\s]+|/(?!/)[^\\s]*")) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,
                    "头像请使用http(s)地址或以/开头的站内路径");
        }
    }

    private void ensureUnique(String userAccount, String emailAddress, Long excludedId) {
        QueryWrapper<User> account = new QueryWrapper<User>().eq("user_account", userAccount);
        account.ne(excludedId != null, "id", excludedId);
        if (userMapper.selectCount(account) > 0) throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "账号已存在");
        QueryWrapper<User> email = new QueryWrapper<User>().eq("email", emailAddress);
        email.ne(excludedId != null, "id", excludedId);
        if (userMapper.selectCount(email) > 0) throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "邮箱已存在");
    }

    /** 显式复制资料白名单：不接触 ID、密码和创建时间。 */
    private void copyProfile(String account, String name, String phone, String email,
                             String avatar, Integer gender, User user) {
        user.setUserAccount(account);
        user.setUserName(StringUtils.trimToEmpty(name));
        user.setPhone(StringUtils.trimToEmpty(phone));
        user.setEmail(email);
        user.setAvatarUrl(StringUtils.defaultIfBlank(avatar, "/Elaina1.jpeg").trim());
        user.setGender(gender);
        user.setUpdateTime(new java.util.Date());
    }

}




