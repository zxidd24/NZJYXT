package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nzxhjy.agri.service.entity.UserAddress;
import com.nzxhjy.agri.service.mapper.UserAddressMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 收货地址默认标记测试。
 *
 * <p>修复前 {@code update} 只在 isDefault=1 时赋值，传 0 时字段不更新，
 * 用户无法取消默认地址（前端开关看起来关了，刷新后又变回开启）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock UserAddressMapper addressMapper;
    AddressService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAddress.class);
        service = new AddressService(addressMapper);
    }

    /** 取消默认：传入 isDefault=0 必须真正落库为 0。 */
    @Test
    void updateCanCancelDefaultAddress() {
        UserAddress existing = address(1L, 7L, 1);
        when(addressMapper.selectOne(any())).thenReturn(existing);

        service.update(7L, 1L, input(0));

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getIsDefault());
        // 取消默认不应清空其他地址的默认标记。
        verify(addressMapper, never()).update(isNull(), any());
    }

    /** 设为默认：先清掉用户其他地址的默认标记，再把自己置 1。 */
    @Test
    void updateCanSetAddressAsDefault() {
        UserAddress existing = address(2L, 7L, 0);
        when(addressMapper.selectOne(any())).thenReturn(existing);

        service.update(7L, 2L, input(1));

        verify(addressMapper).update(isNull(), any());
        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getIsDefault());
    }

    /** 未传 isDefault（null）按取消处理，避免"不传就保持"造成的不确定行为。 */
    @Test
    void updateTreatsNullDefaultAsCancel() {
        when(addressMapper.selectOne(any())).thenReturn(address(3L, 7L, 1));

        service.update(7L, 3L, input(null));

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getIsDefault());
    }

    /** 新增第一个地址时自动设为默认。 */
    @Test
    void firstAddressBecomesDefaultAutomatically() {
        when(addressMapper.selectCount(any())).thenReturn(0L);
        UserAddress input = input(0);

        service.create(7L, input);

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getIsDefault());
    }

    /** 后续新增地址默认不抢默认标记。 */
    @Test
    void subsequentAddressIsNotDefaultUnlessRequested() {
        when(addressMapper.selectCount(any())).thenReturn(1L);
        UserAddress input = input(0);

        service.create(7L, input);

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getIsDefault());
    }

    private UserAddress input(Integer isDefault) {
        UserAddress address = new UserAddress();
        address.setReceiverName("张三");
        address.setReceiverPhone("13500000000");
        address.setProvince("陕西省");
        address.setCity("西安市");
        address.setDistrict("未央区");
        address.setDetailAddress("测试路 1 号");
        address.setIsDefault(isDefault);
        return address;
    }

    private UserAddress address(Long id, Long userId, int isDefault) {
        UserAddress address = input(isDefault);
        address.setId(id);
        address.setUserId(userId);
        return address;
    }
}
