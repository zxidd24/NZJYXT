package com.nzxhjy.agri.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nzxhjy.agri.service.entity.WalletAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WalletAccountMapper extends BaseMapper<WalletAccount> {
    @Select("SELECT * FROM wallet_account WHERE user_id = #{userId} FOR UPDATE")
    WalletAccount selectByUserIdForUpdate(@Param("userId") Long userId);
}
