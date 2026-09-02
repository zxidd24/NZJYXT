package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nzxhjy.agri.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String password;
    private String realName;
    private Integer userType;
    private String phone;
    private String email;
    private Integer status;
}
