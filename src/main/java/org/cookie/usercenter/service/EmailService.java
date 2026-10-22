package org.cookie.usercenter.service;


import jakarta.mail.MessagingException;


public interface EmailService {
    
    boolean sendEmailCode(String targetEmail) throws MessagingException;
    
    boolean sendCode(String email, CodePurpose purpose, boolean deliver) throws MessagingException;

    void verifyCode(String email, String code, CodePurpose purpose);
}
