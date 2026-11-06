package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.entity.OrderAttachment;
import com.nzxhjy.agri.service.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portal/order")
@RequiredArgsConstructor
public class PortalOrderController {
    private final OrderService orderService;
    @GetMapping("/token") public Result<String> token() { return Result.success(orderService.orderToken(UserContext.getUserId())); }
    /** 平台收款账户（只读）：买家下单后据此完成线下转账，避免为了拿账户而调用支付接口。 */
    @GetMapping("/settlement-account") public Result<com.nzxhjy.agri.service.pay.SettlementAccount> settlementAccount() { return Result.success(orderService.settlementAccount()); }
    @PostMapping("/create") public Result<OrderService.OrderView> create(@Valid @RequestBody CreateRequest r) { return Result.success(orderService.create(UserContext.getUserId(), r.token, r.addressId, r.items, r.buyerNote)); }
    @PostMapping("/checkout") public Result<OrderService.PayResult> checkout(@Valid @RequestBody CreateRequest r) { return Result.success(orderService.checkout(UserContext.getUserId(), r.token, r.addressId, r.items, r.buyerNote)); }
    @PostMapping("/pay") public Result<OrderService.PayResult> pay(@Valid @RequestBody PayRequest r) { return Result.success(orderService.pay(UserContext.getUserId(), r.orderNo, r.payMethod)); }
    @GetMapping("/page") public Result<PageResult<OrderService.OrderView>> page(@RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="10") int pageSize, @RequestParam(required=false) Integer status) { return Result.success(orderService.page(UserContext.getUserId(), pageNum, Math.min(pageSize, 100), status)); }
    @GetMapping("/{id}") public Result<OrderService.OrderView> detail(@PathVariable Long id) { return Result.success(orderService.detail(id, UserContext.getUserId())); }
    @PutMapping("/cancel") public Result<Void> cancel(@Valid @RequestBody CancelRequest r) { orderService.cancel(UserContext.getUserId(), r.orderNo); return Result.success(); }
    @PostMapping("/{id}/rebuy") public Result<Void> rebuy(@PathVariable Long id) { orderService.rebuy(UserContext.getUserId(), id); return Result.success(); }
    @GetMapping("/{id}/delivery") public Result<List<OrderAttachment>> delivery(@PathVariable Long id) { return Result.success(orderService.attachments(UserContext.getUserId(), id, false)); }
    @PostMapping("/{id}/receipt") public Result<Void> receipt(@PathVariable Long id, @Valid @RequestBody AttachmentRequest r) { orderService.addAttachment(UserContext.getUserId(), id, 1, r.fileUrl, r.fileName); return Result.success(); }
    /** 上传转账凭证并发起订单审核；此步不置已支付，到账由后台审核确认。 */
    @PostMapping("/{id}/transfer-voucher") public Result<OrderService.PayResult> transferVoucher(@PathVariable Long id, @Valid @RequestBody AttachmentRequest r) { return Result.success(orderService.uploadTransferVoucher(UserContext.getUserId(), id, r.fileUrl, r.fileName)); }
    @Data public static class CreateRequest { @NotBlank private String token; @NotNull private Long addressId; @NotNull @Size(min=1) private List<OrderService.ItemCommand> items; @Size(max=255) private String buyerNote; }
    @Data public static class PayRequest { @NotBlank private String orderNo; @NotBlank private String payMethod; }
    @Data public static class CancelRequest { @NotBlank private String orderNo; }
    @Data public static class AttachmentRequest { @NotBlank private String fileUrl; @Size(max=100) private String fileName; }
}
