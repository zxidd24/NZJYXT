package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.UserAddress;
import com.nzxhjy.agri.service.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final UserAddressMapper addressMapper;

    public List<UserAddress> list(Long userId) {
        return addressMapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    @Transactional
    public Long create(Long userId, UserAddress address) {
        address.setId(null);
        address.setUserId(userId);
        address.setDeleted(0);
        boolean firstAddress = addressMapper.selectCount(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)) == 0;
        address.setIsDefault(firstAddress || Integer.valueOf(1).equals(address.getIsDefault()) ? 1 : 0);
        if (address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        addressMapper.insert(address);
        return address.getId();
    }

    @Transactional
    public void update(Long userId, Long id, UserAddress input) {
        UserAddress address = requireOwned(userId, id);
        address.setReceiverName(input.getReceiverName());
        address.setReceiverPhone(input.getReceiverPhone());
        address.setProvince(input.getProvince());
        address.setCity(input.getCity());
        address.setDistrict(input.getDistrict());
        address.setDetailAddress(input.getDetailAddress());
        if (Integer.valueOf(1).equals(input.getIsDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        addressMapper.updateById(address);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserAddress address = requireOwned(userId, id);
        addressMapper.deleteById(id);
        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            UserAddress replacement = addressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                    .eq(UserAddress::getUserId, userId).orderByDesc(UserAddress::getId).last("LIMIT 1"));
            if (replacement != null) {
                replacement.setIsDefault(1);
                addressMapper.updateById(replacement);
            }
        }
    }

    @Transactional
    public void setDefault(Long userId, Long id) {
        UserAddress address = requireOwned(userId, id);
        clearDefault(userId);
        address.setIsDefault(1);
        addressMapper.updateById(address);
    }

    private UserAddress requireOwned(Long userId, Long id) {
        UserAddress address = addressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId, id).eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "收货地址不存在");
        }
        return address;
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getUserId, userId).set(UserAddress::getIsDefault, 0));
    }
}
