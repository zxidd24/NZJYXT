package com.nzxhjy.agri.service.service;

import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.mapper.PortalOrderStatsMapper;
import com.nzxhjy.agri.service.mapper.PortalUserInfoMapper;
import com.nzxhjy.agri.service.mapper.SysOperLogMapper;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortalUserManagementServiceTest {
    @Mock SysUserMapper userMapper;
    @Mock PortalUserInfoMapper infoMapper;
    @Mock PortalOrderStatsMapper orderStatsMapper;
    @Mock SysOperLogMapper operLogMapper;
    @Mock AesUtils aesUtils;
    @Mock FinanceService financeService;
    @InjectMocks PortalUserManagementService service;

    /** 评级弹窗依赖列表返回的授信额度回填，缺失会导致保存时被重置为0。 */
    @Test
    void pageReturnsCreditLimitForDialogPrefill() {
        SysUser user = portalUser(7L);
        when(userMapper.selectPage(any(), any())).thenReturn(page(user));
        PortalUserInfo info = new PortalUserInfo();
        info.setUserId(7L);
        info.setCreditGrade("A");
        info.setCreditLimit(new BigDecimal("50000.00"));
        when(infoMapper.selectBatchIds(any())).thenReturn(List.of(info));

        PortalUserManagementService.PortalUserListItem item =
                service.page(1, 10, null, null).getList().get(0);

        assertEquals(new BigDecimal("50000.00"), item.getCreditLimit());
        assertEquals("A", item.getCreditGrade());
    }

    /** 没有扩展档案的用户（如刚注册）应回填0而不是报错。 */
    @Test
    void pageReturnsZeroCreditLimitWhenInfoMissing() {
        when(userMapper.selectPage(any(), any())).thenReturn(page(portalUser(8L)));
        when(infoMapper.selectBatchIds(any())).thenReturn(List.of());

        PortalUserManagementService.PortalUserListItem item =
                service.page(1, 10, null, null).getList().get(0);

        assertEquals(BigDecimal.ZERO, item.getCreditLimit());
    }

    /** 额度不得下调到已占用额度以下，避免出现超额占用。 */
    @Test
    void creditLimitCannotDropBelowUsedAmount() {
        when(userMapper.selectById(7L)).thenReturn(portalUser(7L));
        PortalUserInfo info = new PortalUserInfo();
        info.setUserId(7L);
        info.setCreditLimit(new BigDecimal("100.00"));
        when(infoMapper.selectById(7L)).thenReturn(info);
        when(financeService.loanUsed(7L)).thenReturn(new BigDecimal("80.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateCredit(9L, 7L, "A", new BigDecimal("50.00")));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("授信额度不能低于已占用额度80.00元", exception.getMessage());
        verify(infoMapper, never()).updateById(any(PortalUserInfo.class));
        verify(operLogMapper, never()).insert(any(com.nzxhjy.agri.service.entity.SysOperLog.class));
    }

    /** 额度等于已占用额度时允许保存（边界）。 */
    @Test
    void creditLimitEqualToUsedAmountIsAccepted() {
        when(userMapper.selectById(7L)).thenReturn(portalUser(7L));
        PortalUserInfo info = new PortalUserInfo();
        info.setUserId(7L);
        info.setCreditLimit(new BigDecimal("100.00"));
        when(infoMapper.selectById(7L)).thenReturn(info);
        when(financeService.loanUsed(7L)).thenReturn(new BigDecimal("80.00"));

        service.updateCredit(9L, 7L, "B", new BigDecimal("80.00"));

        assertEquals("B", info.getCreditGrade());
        assertEquals(new BigDecimal("80.00"), info.getCreditLimit());
        verify(infoMapper).updateById(info);
        verify(operLogMapper).insert(any(com.nzxhjy.agri.service.entity.SysOperLog.class));
    }

    /** 无占用时仍可自由上调/下调到0。 */
    @Test
    void creditLimitCanBeZeroWhenNothingUsed() {
        when(userMapper.selectById(7L)).thenReturn(portalUser(7L));
        PortalUserInfo info = new PortalUserInfo();
        info.setUserId(7L);
        info.setCreditLimit(new BigDecimal("100.00"));
        when(infoMapper.selectById(7L)).thenReturn(info);
        when(financeService.loanUsed(7L)).thenReturn(BigDecimal.ZERO);

        service.updateCredit(9L, 7L, "C", BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, info.getCreditLimit());
        verify(infoMapper).updateById(info);
    }

    private SysUser portalUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("buyer" + id);
        user.setRealName("买家" + id);
        user.setUserType(1);
        user.setPhone("1380000000" + id);
        user.setStatus(1);
        return user;
    }

    private <T> com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page(T record) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1);
        return page;
    }
}
