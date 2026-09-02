package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.service.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/message-config")
@RequirePermission("admin:system")
@RequiredArgsConstructor
public class MessageConfigController {
    private final MessageService messageService;

    @GetMapping
    public Result<List<MessageService.ConfigView>> list() { return Result.success(messageService.configs()); }

    @GetMapping("/{id}")
    public Result<MessageService.ConfigView> get(@PathVariable Long id) { return Result.success(messageService.getConfig(id)); }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ConfigRequest request) {
        return Result.success(Map.of("id", messageService.createConfig(request.getCode(), request.getTitle(), request.getContentTemplate(), request.getEnabled())));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ConfigRequest request) { messageService.updateConfig(id, request.getCode(), request.getTitle(), request.getContentTemplate(), request.getEnabled()); return Result.success(); }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { messageService.deleteConfig(id); return Result.success(); }

    @Data
    public static class ConfigRequest {
        @NotBlank @Size(max = 50) private String code;
        @NotBlank @Size(max = 100) private String title;
        @NotBlank @Size(max = 500) private String contentTemplate;
        private Integer enabled;
    }
}
