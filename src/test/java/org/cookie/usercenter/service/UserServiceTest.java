package org.cookie.usercenter.service;


import jakarta.annotation.Resource;

import org.cookie.usercenter.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * @author NingBao
 */
@SpringBootTest
class UserServiceTest {

    @Resource
    private UserService userService;

    @Test
    public void testAddUser(){
        User user = new User();

        user.setUserName("NingBao");
        user.setUserAccount("wqy123");
        user.setUserPassword("123456");
        user.setAvatarUrl("https://pic.code-nav.cn/user_avatar/2104165853049253890/thumbnail/XijriAuhLYXfoLE6.jpeg");
        user.setGender(0);
        user.setPhone("15812345678");
        user.setEmail("111@example.com");

        boolean result = userService.save(user);
        System.out.println(user.getId());
        Assertions.assertTrue(result);
    }
    
    @Test
    //@Transactional  //让测试新插入的数据在结束时自动回滚
    void userRegister() {
        //1.账户为空
        String userAccount = "";
        String userPassword = "";
        String checkPassword = "123456";
        String emailAddress = "111222@example.com";
        long result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
        
        //2.账户长度小于4
        userAccount = "bao";
        result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
        
        //3.密码长度小于8
        userAccount = "NingBao";
        userPassword = "123456";
        result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
        
        //4.账户包含特殊字符
        userAccount = "NingBao~";
        userPassword = "12345678";
        result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
        
        //5.两次输入密码不对
        userAccount = "NingBao";
        checkPassword = "123456789";
        result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
        
        //6.账户不能重复
        checkPassword = "12345678";
        result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
       //7.邮箱不能重复
        userService.userRegister(userAccount, userPassword, checkPassword, emailAddress);
        Assertions.assertEquals(-1, result);
        //校验失败
        userAccount = "Test1";
        result = userService.userRegister(userAccount, userPassword, checkPassword,emailAddress);
        Assertions.assertEquals(-1, result);
        
    }
}