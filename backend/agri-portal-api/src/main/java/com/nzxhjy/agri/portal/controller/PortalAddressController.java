package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.entity.UserAddress;
import com.nzxhjy.agri.service.service.AddressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal/address")
@RequiredArgsConstructor
public class PortalAddressController {
    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<UserAddress>> list() {
        return Result.success(addressService.list(UserContext.getUserId()));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.create(UserContext.getUserId(), request.toEntity()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        addressService.update(UserContext.getUserId(), id, request.toEntity());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(UserContext.getUserId(), id);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(UserContext.getUserId(), id);
        return Result.success();
    }

    @Data
    public static class AddressRequest {
        @NotBlank(message = "收货人不能为空")
        @Size(max = 50)
        private String receiverName;
        @NotBlank(message = "收货电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货电话格式不正确")
        private String receiverPhone;
        @Size(max = 20)
        private String province;
        @Size(max = 20)
        private String city;
        @Size(max = 20)
        private String district;
        @NotBlank(message = "详细地址不能为空")
        @Size(max = 200)
        private String detailAddress;
        private Integer isDefault;

        private UserAddress toEntity() {
            UserAddress entity = new UserAddress();
            entity.setReceiverName(receiverName);
            entity.setReceiverPhone(receiverPhone);
            entity.setProvince(province);
            entity.setCity(city);
            entity.setDistrict(district);
            entity.setDetailAddress(detailAddress);
            entity.setIsDefault(isDefault);
            return entity;
        }
    }
}
