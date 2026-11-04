package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.SysRole;
import com.nzxhjy.agri.service.mapper.AuditFlowMapper;
import com.nzxhjy.agri.service.mapper.AuditNodeMapper;
import com.nzxhjy.agri.service.mapper.SysRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审核流程节点数量约束测试。
 *
 * <p>只有订单流程实现了"审核通过后创建下一节点"的推进逻辑，其余业务类型只建首节点。
 * 若允许它们配置多节点，第二节点永远不会生成待办，业务会在只经过首节点的情况下被
 * 终审通过（静默的流程绕过）。这些用例锁定"非订单流程限单节点"的约束。</p>
 */
@ExtendWith(MockitoExtension.class)
class FlowManagementServiceTest {
    @Mock AuditFlowMapper flowMapper;
    @Mock AuditNodeMapper nodeMapper;
    @Mock SysRoleMapper roleMapper;
    FlowManagementService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditFlow.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditNode.class);
        service = new FlowManagementService(flowMapper, nodeMapper, roleMapper);
    }

    /** 非订单流程（以商品上架为例）尝试新增第二个节点应被拒绝。 */
    @Test
    void addNodeRejectsSecondNodeForNonOrderFlow() {
        AuditFlow flow = flow(2L, 2);
        when(flowMapper.selectById(2L)).thenReturn(flow);
        when(roleMapper.selectById(2L)).thenReturn(new SysRole());
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(20L, 2L, 1)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addNode(nodeCommand(2L, "第二审核")));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("该业务类型的审核流程暂只支持单个审核节点，多节点审核仅支持订单流程", exception.getMessage());
        verify(nodeMapper, never()).insert(any(AuditNode.class));
    }

    /** 订单流程允许新增第二个节点。 */
    @Test
    void addNodeAllowsSecondNodeForOrderFlow() {
        AuditFlow flow = flow(4L, 4);
        when(flowMapper.selectById(4L)).thenReturn(flow);
        when(roleMapper.selectById(2L)).thenReturn(new SysRole());
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(40L, 4L, 1)));

        service.addNode(nodeCommand(4L, "主管审核"));

        verify(nodeMapper).insert(any(AuditNode.class));
    }

    /** 保存流程时把非订单流程改成两个节点也应被拒绝。 */
    @Test
    void updateRejectsMultiNodeForNonOrderFlow() {
        AuditFlow flow = flow(5L, 5);
        when(flowMapper.selectOne(any())).thenReturn(flow);
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(50L, 5L, 1)));

        FlowManagementService.FlowUpdate command = new FlowManagementService.FlowUpdate();
        command.setNodes(List.of(updateNode(50L, 1), updateNode(51L, 2)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(5, command));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        verify(flowMapper, never()).updateById(any(AuditFlow.class));
    }

    /** 单节点配置在非订单流程上应正常保存。 */
    @Test
    void updateAllowsSingleNodeForNonOrderFlow() {
        AuditFlow flow = flow(5L, 5);
        when(flowMapper.selectOne(any())).thenReturn(flow);
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(50L, 5L, 1)));
        when(nodeMapper.selectById(50L)).thenReturn(node(50L, 5L, 1));
        when(roleMapper.selectById(2L)).thenReturn(new SysRole());

        FlowManagementService.FlowUpdate command = new FlowManagementService.FlowUpdate();
        command.setNodes(List.of(updateNode(50L, 1)));

        service.update(5, command);

        verify(flowMapper).updateById(any(AuditFlow.class));
    }

    private AuditFlow flow(Long id, Integer bizType) {
        AuditFlow flow = new AuditFlow();
        flow.setId(id);
        flow.setBizType(bizType);
        flow.setFlowName("测试流程");
        flow.setEnabled(1);
        return flow;
    }

    private AuditNode node(Long id, Long flowId, Integer order) {
        AuditNode node = new AuditNode();
        node.setId(id);
        node.setFlowId(flowId);
        node.setNodeName("审核节点");
        node.setRoleId(2L);
        node.setNodeOrder(order);
        return node;
    }

    private FlowManagementService.FlowNodeCommand nodeCommand(Long flowId, String name) {
        FlowManagementService.FlowNodeCommand command = new FlowManagementService.FlowNodeCommand();
        command.setFlowId(flowId);
        command.setNodeName(name);
        command.setRoleId(2L);
        command.setNodeOrder(2);
        return command;
    }

    private FlowManagementService.FlowNodeCommand updateNode(Long id, Integer order) {
        FlowManagementService.FlowNodeCommand command = new FlowManagementService.FlowNodeCommand();
        command.setId(id);
        command.setNodeName("审核节点" + order);
        command.setRoleId(2L);
        command.setNodeOrder(order);
        return command;
    }
}
