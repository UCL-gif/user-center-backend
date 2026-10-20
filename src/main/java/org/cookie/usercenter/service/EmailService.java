package org.cookie.usercenter.service;


import jakarta.mail.MessagingException;


public interface EmailService {
    
    boolean sendEmailCode(String targetEmail) throws MessagingException;
    
    boolean SuccessCheckEmailCode(String targetEmail,String inputCode);
    
    String generateCode(int length);
}
