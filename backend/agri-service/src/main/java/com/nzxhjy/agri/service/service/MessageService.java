package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.service.entity.MessageConfig;
import com.nzxhjy.agri.service.entity.MessageRecord;
import com.nzxhjy.agri.service.entity.SysUserRole;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.mapper.AuditNodeMapper;
import com.nzxhjy.agri.service.mapper.MessageConfigMapper;
import com.nzxhjy.agri.service.mapper.MessageRecordMapper;
import com.nzxhjy.agri.service.mapper.SysUserRoleMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageConfigMapper configMapper;
    private final MessageRecordMapper recordMapper;
    private final AuditNodeMapper nodeMapper;
    private final SysUserRoleMapper userRoleMapper;

    public List<ConfigView> configs() { return configMapper.selectList(Wrappers.<MessageConfig>lambdaQuery().orderByAsc(MessageConfig::getId)).stream().map(this::configView).toList(); }
    public ConfigView getConfig(Long id) { return configView(config(id)); }

    @Transactional public Long createConfig(String code, String title, String template, Integer enabled) {
        validate(code, title, template); if (configMapper.selectCount(Wrappers.<MessageConfig>lambdaQuery().eq(MessageConfig::getCode, code.trim())) > 0) throw duplicate("消息编码已存在");
        MessageConfig config = new MessageConfig(); config.setCode(code.trim()); config.setTitle(title.trim()); config.setContentTemplate(template.trim()); config.setEnabled(enabled == null || enabled == 1 ? 1 : 0); configMapper.insert(config); return config.getId();
    }
    @Transactional public void updateConfig(Long id, String code, String title, String template, Integer enabled) {
        MessageConfig config = config(id); validate(code, title, template); if (configMapper.selectCount(Wrappers.<MessageConfig>lambdaQuery().eq(MessageConfig::getCode, code.trim()).ne(MessageConfig::getId, id)) > 0) throw duplicate("消息编码已存在");
        config.setCode(code.trim()); config.setTitle(title.trim()); config.setContentTemplate(template.trim()); config.setEnabled(enabled == null ? config.getEnabled() : enabled == 1 ? 1 : 0); configMapper.updateById(config);
    }
    @Transactional public void deleteConfig(Long id) { config(id); configMapper.deleteById(id); }

    public PageResult<MessageView> page(Long userId, int pageNum, int pageSize, Integer unreadOnly) {
        IPage<MessageRecord> page = recordMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<MessageRecord>lambdaQuery().eq(MessageRecord::getUserId, userId).eq(unreadOnly != null && unreadOnly == 1, MessageRecord::getIsRead, 0).orderByDesc(MessageRecord::getCreatedAt));
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::messageView).toList());
    }
    public long unreadCount(Long userId) { return recordMapper.selectCount(Wrappers.<MessageRecord>lambdaQuery().eq(MessageRecord::getUserId, userId).eq(MessageRecord::getIsRead, 0)); }
    @Transactional public void read(Long userId, Long id) { MessageRecord record = owned(userId, id); if (record.getIsRead() == null || record.getIsRead() == 0) { record.setIsRead(1); record.setReadTime(LocalDateTime.now()); recordMapper.updateById(record); } }
    @Transactional public void readAll(Long userId) { recordMapper.update(null, Wrappers.<MessageRecord>lambdaUpdate().eq(MessageRecord::getUserId, userId).eq(MessageRecord::getIsRead, 0).set(MessageRecord::getIsRead, 1).set(MessageRecord::getReadTime, LocalDateTime.now())); }

    @Transactional
    public void sendTodo(Long nodeId, Integer bizType, Long bizId) {
        AuditNode node = nodeMapper.selectById(nodeId);
        if (node == null) return;
        userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, node.getRoleId())).stream()
                .map(SysUserRole::getUserId).distinct()
                .forEach(userId -> send(userId, "TODO_REMIND", bizType, bizId, Map.of("业务类型", bizTypeName(bizType))));
    }

    @Transactional public void send(Long userId, String code, Integer bizType, Long bizId, Map<String, ?> variables) {
        MessageConfig config = configMapper.selectOne(Wrappers.<MessageConfig>lambdaQuery().eq(MessageConfig::getCode, code));
        if (config == null || !Integer.valueOf(1).equals(config.getEnabled())) return;
        MessageRecord record = new MessageRecord(); record.setUserId(userId); record.setConfigCode(code); record.setTitle(config.getTitle()); record.setContent(render(config.getContentTemplate(), variables)); record.setBizType(bizType); record.setBizId(bizId); record.setIsRead(0); recordMapper.insert(record);
    }
    private String render(String template, Map<String, ?> variables) { String result = template == null ? "" : template; if (variables != null) for (var entry : variables.entrySet()) result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue() == null ? "" : entry.getValue())); return result; }
    private String bizTypeName(Integer bizType) { return switch (bizType == null ? 0 : bizType) { case 1 -> "实名认证"; case 2 -> "商品上架"; case 3 -> "商品量价修改"; case 4 -> "订单"; case 5 -> "退款"; case 6 -> "贷款"; default -> "业务"; }; }
    private MessageConfig config(Long id) { MessageConfig config = configMapper.selectById(id); if (config == null) throw business("消息配置不存在"); return config; }
    private MessageRecord owned(Long userId, Long id) { MessageRecord record = recordMapper.selectById(id); if (record == null || !userId.equals(record.getUserId())) throw business("消息不存在"); return record; }
    private ConfigView configView(MessageConfig config) { return new ConfigView(config.getId(), config.getCode(), config.getTitle(), config.getContentTemplate(), config.getEnabled()); }
    private MessageView messageView(MessageRecord record) { return new MessageView(record.getId(), record.getConfigCode(), record.getTitle(), record.getContent(), record.getBizType(), record.getBizId(), record.getIsRead(), record.getReadTime(), record.getCreatedAt()); }
    private void validate(String code, String title, String template) { if (code == null || code.isBlank()) throw invalid("消息编码不能为空"); if (title == null || title.isBlank()) throw invalid("消息标题不能为空"); if (template == null || template.isBlank()) throw invalid("消息模板不能为空"); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), message); }
    private BusinessException business(String message) { return new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), message); }
    private BusinessException duplicate(String message) { return new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), message); }
    @Data @AllArgsConstructor public static class ConfigView { private Long id; private String code; private String title; private String contentTemplate; private Integer enabled; }
    @Data @AllArgsConstructor public static class MessageView { private Long id; private String configCode; private String title; private String content; private Integer bizType; private Long bizId; private Integer isRead; private LocalDateTime readTime; private LocalDateTime createdAt; }
}
