package org.cookie.usercenter.service.impl;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.cookie.usercenter.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class EmailServiceImpl
         implements EmailService {
    //用注解的方式获取数据
    @Value("${spring.mail.username}")
    private String EMAIL_ADDRESS;
    @Resource
    private JavaMailSender javaMailSender;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    
    public boolean sendEmailCode(String targetEmail) throws MessagingException {
        String form = "^(\\w+([-.][A-Za-z0-9]+)*){3,18}@\\w+([-.][A-Za-z0-9]+)*\\.\\w+([-.][A-Za-z0-9]+)*$";
        boolean matches = Pattern.matches(form, targetEmail);
        if (!matches) {
            return false;
        }
        //定义唯一标识符
        String redisKey = "email:code:" + targetEmail;
        if (redisTemplate.hasKey(redisKey)) {
            return false;
        }
        
        //1.生成6位数验证码
        String code = generateCode(6);
       
        //2.设置邮箱信息，标题，内容之类的
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true); //需要用到helper来构建
        helper.setFrom(EMAIL_ADDRESS);  //来自哪里
        helper.setTo(targetEmail);      //发到哪里
        helper.setSubject("这封邮件是用户管理系统的验证码哦");
        String content = "您好，注册验证码哦是 " + code + " 1分钟内有效";
        helper.setText(content);
        
        //3.存入redis，设置过期时间
        
        redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(1)); //一分钟过期
        //4.发送验证码到目标邮箱，并带上信息
        javaMailSender.send(mimeMessage);
        return true;
    }
    
    @Override
    public boolean SuccessCheckEmailCode(String targetEmail, String inputCode) {
        String redisKey = "email:code:" + targetEmail;
        
        String realCode = redisTemplate.opsForValue().get(redisKey);
        if(realCode == null){
            return false;
        }
        // 校验成功之后删除验证码，防止重复使用
        boolean match = realCode.equals(inputCode);
        if(match){
            redisTemplate.delete(redisKey);
        }
        return match;
    }
    
    @Override
    public String generateCode(int length) {
        UUID uuid = UUID.randomUUID();
        String string = uuid.toString();
        return string.substring(0, length);
    }
    
}
