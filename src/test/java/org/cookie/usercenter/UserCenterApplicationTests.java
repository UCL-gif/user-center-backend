package org.cookie.usercenter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.DigestUtils;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SpringBootTest
@ActiveProfiles("test")
class UserCenterApplicationTests {
    
    @Test
    void testDigest() throws NoSuchAlgorithmException {
        MessageDigest md5 = null;
        md5 = MessageDigest.getInstance("MD5");
        
        String newPassword = DigestUtils.md5DigestAsHex(("abcd" + "mypassword").getBytes());
        System.out.println(newPassword);
    }

    @Test
    void contextLoads() {
    }

}
