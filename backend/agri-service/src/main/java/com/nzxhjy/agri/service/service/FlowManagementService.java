package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.SysRole;
import com.nzxhjy.agri.service.mapper.AuditFlowMapper;
import com.nzxhjy.agri.service.mapper.AuditNodeMapper;
import com.nzxhjy.agri.service.mapper.SysRoleMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowManagementService {
    private final AuditFlowMapper flowMapper;
    private final AuditNodeMapper nodeMapper;
    private final SysRoleMapper roleMapper;

    public List<FlowView> list() {
        return flowMapper.selectList(Wrappers.<AuditFlow>lambdaQuery().orderByAsc(AuditFlow::getBizType))
                .stream().map(this::flowView).toList();
    }

    @Transactional
    public void update(Integer bizType, FlowUpdate command) {
        if (command == null) throw invalid("流程配置不能为空");
        AuditFlow flow = requireFlow(bizType);
        List<FlowNodeCommand> nodes = command.getNodes() == null ? List.of() : command.getNodes();
        validateNodes(flow.getId(), nodes, false);
        flow.setFlowName(command.getFlowName() == null || command.getFlowName().isBlank() ? flow.getFlowName() : command.getFlowName().trim());
        flow.setEnabled(command.getEnabled() == null ? flow.getEnabled() : command.getEnabled() == 1 ? 1 : 0);
        flowMapper.updateById(flow);
        reorder(flow.getId(), nodes);
    }

    @Transactional
    public Long addNode(FlowNodeCommand command) {
        if (command == null || command.getFlowId() == null) throw invalid("流程ID不能为空");
        AuditFlow flow = flowMapper.selectById(command.getFlowId());
        if (flow == null) throw business("审核流程不存在");
        if (command.getNodeName() == null || command.getNodeName().isBlank()) throw invalid("节点名称不能为空");
        if (command.getRoleId() == null || roleMapper.selectById(command.getRoleId()) == null) throw business("审核角色不存在");
        List<AuditNode> current = nodes(flow.getId());
        int target = command.getNodeOrder() == null ? current.size() + 1 : Math.max(1, Math.min(command.getNodeOrder(), current.size() + 1));
        nodeMapper.update(null, Wrappers.<AuditNode>lambdaUpdate().eq(AuditNode::getFlowId, flow.getId()).setSql("node_order = node_order + 1000"));
        int order = 1;
        for (AuditNode node : current) {
            if (order == target) order++;
            node.setNodeOrder(order++);
            nodeMapper.updateById(node);
        }
        AuditNode node = new AuditNode(); node.setFlowId(flow.getId()); node.setNodeName(command.getNodeName().trim()); node.setRoleId(command.getRoleId()); node.setNodeOrder(target); nodeMapper.insert(node);
        return node.getId();
    }

    @Transactional
    public void deleteNode(Long id) {
        AuditNode node = nodeMapper.selectById(id);
        if (node == null) throw business("审核节点不存在");
        if (nodeMapper.selectCount(Wrappers.<AuditNode>lambdaQuery().eq(AuditNode::getFlowId, node.getFlowId())) <= 1) throw business("流程至少保留一个审核节点");
        nodeMapper.deleteById(id);
        List<AuditNode> remaining = nodes(node.getFlowId());
        reorderExisting(node.getFlowId(), remaining);
    }

    private FlowView flowView(AuditFlow flow) {
        List<AuditNode> nodes = nodes(flow.getId());
        Set<Long> roleIds = nodes.stream().map(AuditNode::getRoleId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var roles = roleIds.isEmpty() ? List.<SysRole>of() : roleMapper.selectBatchIds(roleIds);
        return new FlowView(flow.getId(), flow.getBizType(), flow.getFlowName(), flow.getEnabled(), nodes.stream()
                .map(node -> new NodeView(node.getId(), node.getNodeName(), node.getRoleId(), roleName(roles, node.getRoleId()), node.getNodeOrder())).toList());
    }

    private String roleName(List<SysRole> roles, Long id) { return roles.stream().filter(role -> Objects.equals(role.getId(), id)).map(SysRole::getRoleName).findFirst().orElse(null); }
    private List<AuditNode> nodes(Long flowId) { return nodeMapper.selectList(Wrappers.<AuditNode>lambdaQuery().eq(AuditNode::getFlowId, flowId).orderByAsc(AuditNode::getNodeOrder)); }
    private AuditFlow requireFlow(Integer bizType) { AuditFlow flow = flowMapper.selectOne(Wrappers.<AuditFlow>lambdaQuery().eq(AuditFlow::getBizType, bizType)); if (flow == null) throw business("审核流程不存在"); return flow; }

    private void validateNodes(Long flowId, List<FlowNodeCommand> commands, boolean allowNew) {
        List<AuditNode> existing = nodes(flowId); Set<Long> existingIds = existing.stream().map(AuditNode::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> ids = new HashSet<>(); Set<Integer> orders = new HashSet<>();
        if (commands.isEmpty()) throw invalid("流程至少保留一个审核节点");
        for (FlowNodeCommand command : commands) {
            if (command.getId() == null || !existingIds.contains(command.getId()) || !ids.add(command.getId())) throw invalid("审核节点参数不正确");
            if (command.getNodeOrder() == null || command.getNodeOrder() < 1 || !orders.add(command.getNodeOrder())) throw invalid("审核节点顺序不能重复");
            if (command.getRoleId() == null || roleMapper.selectById(command.getRoleId()) == null) throw business("审核角色不存在");
            if (command.getNodeName() == null || command.getNodeName().isBlank()) throw invalid("节点名称不能为空");
        }
        if (ids.size() != existingIds.size() || orders.size() != existing.size()) throw invalid("必须完整提交流程节点");
    }

    private void reorder(Long flowId, List<FlowNodeCommand> commands) {
        nodeMapper.update(null, Wrappers.<AuditNode>lambdaUpdate().eq(AuditNode::getFlowId, flowId).setSql("node_order = node_order + 1000"));
        commands.stream().sorted(Comparator.comparing(FlowNodeCommand::getNodeOrder)).forEach(command -> {
            AuditNode node = nodeMapper.selectById(command.getId()); node.setNodeName(command.getNodeName().trim()); node.setRoleId(command.getRoleId()); node.setNodeOrder(command.getNodeOrder()); nodeMapper.updateById(node);
        });
    }

    private void reorderExisting(Long flowId, List<AuditNode> nodes) {
        nodeMapper.update(null, Wrappers.<AuditNode>lambdaUpdate().eq(AuditNode::getFlowId, flowId).setSql("node_order = node_order + 1000"));
        int order = 1; for (AuditNode node : nodes) { node.setNodeOrder(order++); nodeMapper.updateById(node); }
    }

    private BusinessException invalid(String message) { return new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), message); }
    private BusinessException business(String message) { return new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), message); }

    @Data @AllArgsConstructor public static class FlowView { private Long id; private Integer bizType; private String flowName; private Integer enabled; private List<NodeView> nodes; }
    @Data @AllArgsConstructor public static class NodeView { private Long id; private String nodeName; private Long roleId; private String roleName; private Integer nodeOrder; }
    @Data public static class FlowUpdate { private String flowName; private Integer enabled; private List<FlowNodeCommand> nodes; }
    @Data public static class FlowNodeCommand { private Long id; private Long flowId; private String nodeName; private Long roleId; private Integer nodeOrder; }
}
