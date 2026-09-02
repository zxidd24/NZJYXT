package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portal/cart")
@RequiredArgsConstructor
public class PortalCartController {
    private final OrderService orderService;
    @GetMapping("/list") public Result<List<OrderService.CartView>> list() { return Result.success(orderService.cartList(UserContext.getUserId())); }
    @PostMapping public Result<Void> add(@Valid @RequestBody CartRequest r) { orderService.addCart(UserContext.getUserId(), r.productId, r.quantity); return Result.success(); }
    @PutMapping public Result<Void> update(@Valid @RequestBody CartRequest r) { orderService.updateCart(UserContext.getUserId(), r.productId, r.quantity, r.selected); return Result.success(); }
    @DeleteMapping("/{productId}") public Result<Void> delete(@PathVariable Long productId) { orderService.deleteCart(UserContext.getUserId(), productId); return Result.success(); }
    @Data public static class CartRequest { @NotNull private Long productId; @NotNull @Min(1) private Integer quantity; private Integer selected; }
}
