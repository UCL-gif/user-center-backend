package org.cookie.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.service.UserService;
import org.cookie.usercenter.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.cookie.usercenter.constant.userConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现类
* @author NingBao
* @createDate 2026-10-06 00:51:37
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService{
    
    @Resource
    private UserMapper userMapper;
    private static final String SALT = "NingBao";   //盐值,用以混淆密码
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword,String emailAddress) {
        //1.校验
        //1.任何一项为空
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, emailAddress)) {
            throw new BusinessException(ErrorCode.PARAMETERS_NULL_ERROR,"参数为空！");
        }
        //2.账户长度不能小于4
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"用户账户长度过短！");
        }
        //3.密码长度不能小于8
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "密码长度过短！");
        }
        //4.账户不能包含特殊字符
        String validPattern= "[\\u00A0\\s\"`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "账户包含了特殊字符");
        }
        //5.邮箱格式要正确
        String form = "^(\\w+([-.][A-Za-z0-9]+)*){3,18}@\\w+([-.][A-Za-z0-9]+)*\\.\\w+([-.][A-Za-z0-9]+)*$";
        boolean matches = Pattern.matches(form, emailAddress);
        if (!matches) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"邮箱格式不对！");
        }
        //6.校验密码
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"两次输入的密码不匹配");
        }
       
        //7.账户不能重复，去数据库查询
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"账户已存在");
        }
        //8.邮箱不能重复
        queryWrapper.eq("email", emailAddress);
        Long emailExistResult = userMapper.selectCount(queryWrapper);
        if (emailExistResult > 0) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"邮箱已存在");
        }
        //2.加密

        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        System.out.println(encryptPassword);
        
        //3.插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setEmail(emailAddress);
        int saveResult = userMapper.insert(user);
        if (saveResult <= 0) {
            return -1;
        }
        return user.getId();
    }
    
    /**
     *
     * @param userAccount   用户输入的账户
     * @param userPassword  用户输入的密码
     * @param request   tomcat传来的请求包裹
     * @return  返回脱敏后的用户信息
     */
    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1.校验
        //1)账户为空
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"参数为空！");
        }
        //2)账户长度不能小于4
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "账号长度太短");
        }
        //3)密码长度不能小于8
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR,"密码长度太短");
        }
        //4)账户不能包含特殊字符
        String validPattern= "[\\u00A0\\s\"`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR);
        }
        
        //加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        System.out.println(encryptPassword);
        
        //5)账户是否存在，去数据库查询
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        queryWrapper.eq("user_password", encryptPassword);
        
         User originalUser = userMapper.selectOne(queryWrapper);
        //用户不存在
        if (originalUser == null) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "账号或密码错误");
        } else {
            //用户脱敏
            User cleanedUser = createCleanedUser(originalUser);
            
            //2.用户登录状态
            request.getSession().setAttribute(USER_LOGIN_STATE, cleanedUser);
            
            return cleanedUser;
        }
        
        
        
    }
    
    /**
     * 用户脱敏方法
     * @param originalUser 查询到的用户数据
     * @return  返回脱敏后的用户数据
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
        cleanedUser.setStatus(0);
        cleanedUser.setCreateTime(originalUser.getCreateTime());
        return cleanedUser;
    }
    
    /**
     * 该方法用户注销
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
        
    }
    
}




