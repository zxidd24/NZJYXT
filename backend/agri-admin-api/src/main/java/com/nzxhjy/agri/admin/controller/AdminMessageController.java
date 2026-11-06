package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.MessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台消息中心：查看当前登录管理员收到的站内消息。
 *
 * <p>复用 {@link MessageService} 已有的按用户分页能力——待办提醒、审核结果等
 * 消息本就按 userId 写入，此前后台只有侧边栏数字角标，看不到消息正文。</p>
 *
 * <p>不加 {@code @RequirePermission}：任何能登录后台的账号都应当能看到自己的消息，
 * 与权限矩阵无关。</p>
 */
@RestController
@RequestMapping("/api/admin/message")
@RequiredArgsConstructor
@Validated
public class AdminMessageController {
    private final MessageService messageService;

    @GetMapping("/page")
    public Result<PageResult<MessageService.MessageView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer unreadOnly) {
        return Result.success(messageService.page(UserContext.getUserId(), pageNum, pageSize, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount(UserContext.getUserId()));
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        messageService.read(UserContext.getUserId(), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> readAll() {
        messageService.readAll(UserContext.getUserId());
        return Result.success();
    }
}
