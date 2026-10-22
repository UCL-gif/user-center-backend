package org.cookie.usercenter.service;

/** 注册验证码不能拿来重置密码，反过来也不可以。 */
public enum CodePurpose { REGISTER, RESET_PASSWORD }
