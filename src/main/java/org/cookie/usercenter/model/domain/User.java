package org.cookie.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;

import java.util.Date;
import lombok.Data;

/**
 * 用户表
 * user
 */
@TableName(value ="user")
@Data
public class User {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户的昵称
     */
    private String userName;

    /**
     * 用户的账号
     */
    private String userAccount;

    /**
     * 用户的密码
     */
    private String userPassword;

    /**
     * 用户头像
     */
    private String avatarUrl;

    /**
     * 用户性别
     */
    private Integer gender;

    /**
     * 用户的电话
     */
    private String phone;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 用户的状态，0-正常
     */
    private Integer status;
    /**
     *用户的权限，0为普通用户，1为管理员
     */
    private Integer identity;
    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否为删除状态
     */
    @TableLogic
    private Integer Deleted;
}