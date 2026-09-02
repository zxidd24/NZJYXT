package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.service.service.FlowManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequirePermission("admin:system")
@RequiredArgsConstructor
public class FlowController {
    private final FlowManagementService flowService;

    @GetMapping("/flow")
    public Result<List<FlowManagementService.FlowView>> list() { return Result.success(flowService.list()); }

    @PutMapping("/flow/{bizType}")
    public Result<Void> update(@PathVariable Integer bizType, @Valid @RequestBody FlowManagementService.FlowUpdate request) { flowService.update(bizType, request); return Result.success(); }

    @PostMapping("/flow-node")
    public Result<Map<String, Long>> addNode(@Valid @RequestBody NodeRequest request) {
        FlowManagementService.FlowNodeCommand command = new FlowManagementService.FlowNodeCommand();
        command.setFlowId(request.getFlowId()); command.setNodeName(request.getNodeName()); command.setRoleId(request.getRoleId()); command.setNodeOrder(request.getNodeOrder());
        return Result.success(Map.of("id", flowService.addNode(command)));
    }

    @DeleteMapping("/flow-node/{id}")
    public Result<Void> deleteNode(@PathVariable Long id) { flowService.deleteNode(id); return Result.success(); }

    @Data
    public static class NodeRequest {
        @NotNull private Long flowId;
        @NotBlank @Size(max = 50) private String nodeName;
        @NotNull private Long roleId;
        private Integer nodeOrder;
    }
}
