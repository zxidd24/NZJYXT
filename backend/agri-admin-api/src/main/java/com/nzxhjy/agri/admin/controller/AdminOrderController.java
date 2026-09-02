package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
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
@RequestMapping("/api/admin/order")
@RequirePermission("admin:order")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderService orderService;
    @GetMapping("/page") public Result<PageResult<OrderService.OrderView>> page(@RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="10") int pageSize, @RequestParam(required=false) Integer status, @RequestParam(required=false) String orderNo, @RequestParam(required=false) Long categoryId) { return Result.success(orderService.adminPage(pageNum, Math.min(pageSize, 100), status, orderNo, categoryId)); }
    @GetMapping("/{id}/attachments") public Result<List<OrderAttachment>> attachments(@PathVariable Long id) { return Result.success(orderService.attachments(UserContext.getUserId(), id, true)); }
    @PutMapping("/delivery") public Result<Void> delivery(@Valid @RequestBody DeliveryRequest r) { orderService.delivery(UserContext.getUserId(), r.orderId, r.company, r.trackingNo, r.fileUrl, r.fileName); return Result.success(); }
    @PutMapping("/confirm") public Result<Void> confirm(@Valid @RequestBody IdRequest r) { orderService.confirm(UserContext.getUserId(), r.orderId); return Result.success(); }
    @Data public static class DeliveryRequest { @NotNull private Long orderId; @NotBlank private String company; @NotBlank private String trackingNo; @Size(max=255) private String fileUrl; @Size(max=100) private String fileName; }
    @Data public static class IdRequest { @NotNull private Long orderId; }
}
