package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.MessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portal/message")
@RequiredArgsConstructor
@Validated
public class PortalMessageController {
    private final MessageService messageService;

    @GetMapping("/page")
    public Result<PageResult<MessageService.MessageView>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                                                 @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                                                                 @RequestParam(required = false) Integer unreadOnly) {
        return Result.success(messageService.page(UserContext.getUserId(), pageNum, pageSize, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() { return Result.success(messageService.unreadCount(UserContext.getUserId())); }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) { messageService.read(UserContext.getUserId(), id); return Result.success(); }

    @PutMapping("/read-all")
    public Result<Void> readAll() { messageService.readAll(UserContext.getUserId()); return Result.success(); }
}
