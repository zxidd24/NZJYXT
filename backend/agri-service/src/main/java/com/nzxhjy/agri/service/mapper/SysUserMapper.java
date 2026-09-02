package com.nzxhjy.agri.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nzxhjy.agri.service.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsernameIncludingDeleted(@Param("username") String username);
}
