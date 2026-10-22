package org.cookie.usercenter.model.request;

import lombok.Data;

/** 管理页查询条件。Integer 可以区分“没选”与值为 0 的选项。 */
@Data
public class UserQueryRequest {
    private String userAccount;
    private String userName;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
    private Integer identity;
}
