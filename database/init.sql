-- 农资现货交易系统数据库初始化脚本
-- 目标：MySQL 8.0+（已在本地 MySQL 9.3 验证）
-- 本脚本不包含任何真实密码或密钥，可重复执行。

CREATE DATABASE IF NOT EXISTS `agri_trading`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE `agri_trading`;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录账号（门户用户为手机号）',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt加密密码',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `user_type` tinyint DEFAULT NULL COMMENT '1-自然人 2-法人 3-后台管理员',
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '0-禁用 1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表，保存后台管理员和门户用户账号信息';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL,
  `description` varchar(200) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表，定义管理员角色及职责';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表，维护用户与角色的多对多关系';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0',
  `name` varchar(50) DEFAULT NULL,
  `type` tinyint DEFAULT NULL COMMENT '1-菜单 2-按钮',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识，如 admin:product:add',
  `url` varchar(200) DEFAULT NULL,
  `sort` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表，保存菜单和按钮权限定义';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表，维护角色可访问的菜单和按钮';

CREATE TABLE IF NOT EXISTS `portal_user_info` (
  `user_id` bigint NOT NULL COMMENT '关联sys_user.id',
  `company_name` varchar(100) DEFAULT NULL COMMENT '法人用户的企业名称',
  `contact_name` varchar(50) DEFAULT NULL COMMENT '联系人姓名',
  `id_card` varchar(255) DEFAULT NULL COMMENT '身份证号（AES加密）',
  `business_license` varchar(100) DEFAULT NULL COMMENT '营业执照号（统一社会信用代码）',
  `business_license_img` varchar(255) DEFAULT NULL COMMENT '营业执照图片URL',
  `id_card_front` varchar(255) DEFAULT NULL COMMENT '身份证正面URL',
  `id_card_back` varchar(255) DEFAULT NULL COMMENT '身份证反面URL',
  `bank_card` varchar(255) DEFAULT NULL COMMENT '银行卡号（AES加密）',
  `bank_name` varchar(50) DEFAULT NULL COMMENT '开户行',
  `credit_grade` varchar(20) DEFAULT NULL COMMENT '商家等级/信用等级 A/B/C',
  `credit_limit` decimal(18,2) DEFAULT '0.00' COMMENT '授信额度',
  `auth_status` tinyint DEFAULT '0' COMMENT '0-未认证 1-审核中 2-已认证 3-驳回',
  `auth_submit_time` datetime DEFAULT NULL,
  `auth_audit_time` datetime DEFAULT NULL,
  `auth_audit_remark` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户用户扩展信息表，保存实名认证、银行卡和授信资料';

CREATE TABLE IF NOT EXISTS `user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `receiver_name` varchar(50) NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) NOT NULL,
  `province` varchar(20) DEFAULT NULL,
  `city` varchar(20) DEFAULT NULL,
  `district` varchar(20) DEFAULT NULL,
  `detail_address` varchar(200) NOT NULL COMMENT '详细地址',
  `is_default` tinyint DEFAULT '0' COMMENT '1-默认地址',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址表，支持一个用户维护多个地址';

CREATE TABLE IF NOT EXISTS `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '0为顶级，支持多级分类',
  `name` varchar(50) NOT NULL,
  `sort` int DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '0-停用 1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表，支持多级分类树';

CREATE TABLE IF NOT EXISTS `product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `sub_title` varchar(300) DEFAULT NULL,
  `brand` varchar(50) DEFAULT NULL,
  `price` decimal(18,2) NOT NULL COMMENT '单价',
  `stock` int NOT NULL COMMENT '库存',
  `unit` varchar(10) DEFAULT NULL COMMENT '单位（吨/袋/瓶）',
  `description` text,
  `images` varchar(1000) DEFAULT NULL COMMENT '逗号分隔图片URL',
  `status` tinyint DEFAULT '0' COMMENT '0-待审核 1-已上架 2-已下架 3-审核驳回',
  `is_recommend` tinyint DEFAULT '0' COMMENT '推荐位 0-否 1-是',
  `sort` int DEFAULT '0' COMMENT '展示位置排序值',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表，保存农资商品价格、库存和上下架信息';

CREATE TABLE IF NOT EXISTS `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `selected` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表，保存用户待购买商品和数量';

CREATE TABLE IF NOT EXISTS `order_main` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '商品总额',
  `pay_amount` decimal(18,2) DEFAULT NULL COMMENT '应付金额',
  `freight` decimal(10,2) DEFAULT '0.00' COMMENT '运费',
  `order_status` tinyint DEFAULT '0' COMMENT '0-待付款 1-待审核 2-待发货 3-待收货 4-已完成 5-已取消 6-退款中 7-已退款',
  `pay_status` tinyint DEFAULT '0' COMMENT '0-未支付 1-已支付',
  `pay_time` datetime DEFAULT NULL,
  `pay_method` varchar(20) DEFAULT NULL COMMENT '钱包/银行转账/额度',
  `address_id` bigint DEFAULT NULL COMMENT '下单所选地址ID',
  `receiver_name` varchar(50) DEFAULT NULL COMMENT '收货人快照',
  `receiver_phone` varchar(20) DEFAULT NULL,
  `receiver_address` varchar(300) DEFAULT NULL COMMENT '完整收货地址快照',
  `audit_node_id` bigint DEFAULT NULL COMMENT '当前审核节点',
  `cancel_time` datetime DEFAULT NULL,
  `buyer_note` varchar(255) DEFAULT NULL,
  `seller_note` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`,`order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表，保存订单金额、状态、支付和收货快照';

CREATE TABLE IF NOT EXISTS `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name` varchar(200) DEFAULT NULL COMMENT '商品名快照',
  `category_id` bigint DEFAULT NULL COMMENT '商品分类快照',
  `category_name` varchar(50) DEFAULT NULL COMMENT '分类名快照',
  `product_price` decimal(18,2) DEFAULT NULL COMMENT '成交单价快照',
  `unit` varchar(10) DEFAULT NULL COMMENT '单位快照',
  `quantity` int DEFAULT NULL,
  `total_price` decimal(18,2) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表，保存商品成交信息和分类快照';

CREATE TABLE IF NOT EXISTS `order_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '1-客户盖章回传单 2-签章发货单',
  `file_url` varchar(255) NOT NULL,
  `file_name` varchar(100) DEFAULT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单附件表，保存客户回传单和签章发货单';

CREATE TABLE IF NOT EXISTS `refund_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_no` varchar(32) NOT NULL COMMENT '退款单号',
  `order_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `order_amount` decimal(18,2) DEFAULT NULL,
  `refund_amount` decimal(18,2) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0-待审核 1-同意 2-驳回 3-已退款',
  `refund_channel` varchar(20) DEFAULT NULL COMMENT '钱包/银行转账（原路退回）',
  `prev_status` tinyint DEFAULT NULL COMMENT '发起退款前订单状态快照',
  `apply_time` datetime DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `auditor_id` bigint DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款申请表，记录退款金额、原因、审核和原路退款状态';

CREATE TABLE IF NOT EXISTS `order_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `score` int DEFAULT NULL COMMENT '1-5分',
  `content` varchar(500) DEFAULT NULL,
  `images` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_product` (`order_id`,`product_id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单评价表，保存用户对已完成订单商品的评价';

CREATE TABLE IF NOT EXISTS `wallet_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `balance` decimal(18,2) DEFAULT '0.00' COMMENT '可用余额',
  `frozen_amount` decimal(18,2) DEFAULT '0.00' COMMENT '冻结金额',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包账户表，保存用户虚拟钱包余额和冻结金额';

CREATE TABLE IF NOT EXISTS `wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `trans_no` varchar(32) NOT NULL COMMENT '流水号',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '发生额（正数）',
  `direction` tinyint DEFAULT NULL COMMENT '1-入账 2-出账',
  `trans_type` tinyint DEFAULT NULL COMMENT '1-入金 2-出金 3-支付 4-退款',
  `trans_status` tinyint DEFAULT '0' COMMENT '0-处理中 1-成功 2-失败',
  `balance_after` decimal(18,2) DEFAULT NULL COMMENT '交易后余额',
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trans_no` (`trans_no`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包资金流水表，记录入金、出金、支付和退款流水';

SET @voucher_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_transaction' AND COLUMN_NAME = 'voucher_url'
);
SET @voucher_column_sql = IF(@voucher_column_exists = 0,
  'ALTER TABLE `wallet_transaction` ADD COLUMN `voucher_url` varchar(255) DEFAULT NULL COMMENT ''财务凭证影像链接''',
  'SELECT 1');
PREPARE voucher_stmt FROM @voucher_column_sql;
EXECUTE voucher_stmt;
DEALLOCATE PREPARE voucher_stmt;

CREATE TABLE IF NOT EXISTS `loan_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `loan_no` varchar(32) NOT NULL COMMENT '贷款申请编号',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '贷款金额',
  `credit_limit_used` decimal(18,2) DEFAULT NULL COMMENT '占用授信额度',
  `status` tinyint DEFAULT NULL COMMENT '0-申请中 1-已放款 2-已还款 3-驳回',
  `apply_time` datetime DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `auditor_id` bigint DEFAULT NULL,
  `audit_remark` varchar(255) DEFAULT NULL,
  `release_time` datetime DEFAULT NULL COMMENT '放款时间',
  `repay_time` datetime DEFAULT NULL COMMENT '还款时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_loan_no` (`loan_no`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='贷款记录表，保存贷款申请、放款、还款及授信占用信息';

CREATE TABLE IF NOT EXISTS `invoice_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title_type` tinyint DEFAULT '2' COMMENT '1-个人 2-企业',
  `title` varchar(100) NOT NULL COMMENT '发票抬头',
  `tax_no` varchar(50) DEFAULT NULL COMMENT '纳税人识别号',
  `bank_name` varchar(50) DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(255) DEFAULT NULL COMMENT '银行账号（AES加密）',
  `reg_address` varchar(200) DEFAULT NULL COMMENT '注册地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '注册电话',
  `is_default` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票抬头信息表，保存用户开票资料';

CREATE TABLE IF NOT EXISTS `invoice_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(32) NOT NULL COMMENT '开票申请编号',
  `user_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `invoice_info_id` bigint NOT NULL,
  `amount` decimal(18,2) NOT NULL COMMENT '开票金额',
  `status` tinyint DEFAULT '0' COMMENT '0-待开票 1-已开票 2-驳回',
  `apply_time` datetime DEFAULT NULL,
  `issue_time` datetime DEFAULT NULL,
  `issuer_id` bigint DEFAULT NULL,
  `invoice_no` varchar(50) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_order` (`order_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开票申请表，记录订单开票申请和处理结果';

CREATE TABLE IF NOT EXISTS `article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) DEFAULT NULL,
  `content` text,
  `category_id` bigint DEFAULT NULL COMMENT '栏目',
  `author` varchar(50) DEFAULT NULL,
  `author_id` bigint DEFAULT NULL COMMENT '发布人',
  `source` varchar(100) DEFAULT NULL,
  `is_published` tinyint DEFAULT '0' COMMENT '0-草稿 1-已发布',
  `publish_time` datetime DEFAULT NULL,
  `sort` int DEFAULT '0',
  `view_count` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯文章表，保存门户行业资讯和公司消息';

CREATE TABLE IF NOT EXISTS `article_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) DEFAULT NULL,
  `sort` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯栏目表，维护文章分类栏目';

CREATE TABLE IF NOT EXISTS `message_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '消息编码',
  `title` varchar(100) DEFAULT NULL,
  `content_template` varchar(500) DEFAULT NULL COMMENT '支持{变量}占位',
  `enabled` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息配置表，维护站内消息模板和启用状态';

CREATE TABLE IF NOT EXISTS `message_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '接收人',
  `config_code` varchar(50) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `content` varchar(500) NOT NULL,
  `biz_type` tinyint DEFAULT NULL,
  `biz_id` bigint DEFAULT NULL,
  `is_read` tinyint DEFAULT '0' COMMENT '0-未读 1-已读',
  `read_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录表，保存用户待办提醒和系统通知';

CREATE TABLE IF NOT EXISTS `audit_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_type` tinyint NOT NULL COMMENT '1-实名认证 2-商品上架 3-商品量价修改 4-订单审核 5-退款审核 6-贷款审核',
  `flow_name` varchar(50) NOT NULL,
  `enabled` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核流程表，按业务类型配置可用审核流程';

CREATE TABLE IF NOT EXISTS `audit_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_id` bigint NOT NULL,
  `node_name` varchar(50) NOT NULL,
  `role_id` bigint NOT NULL,
  `node_order` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_flow` (`flow_id`),
  UNIQUE KEY `uk_flow_order` (`flow_id`,`node_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核节点表，配置流程中的审核角色和顺序';

CREATE TABLE IF NOT EXISTS `audit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_type` tinyint NOT NULL COMMENT '1-实名认证 2-商品上架 3-商品量价修改 4-订单审核 5-退款审核 6-贷款审核',
  `biz_id` bigint NOT NULL,
  `biz_no` varchar(64) DEFAULT NULL,
  `biz_summary` varchar(255) DEFAULT NULL,
  `flow_node_id` bigint DEFAULT NULL,
  `node_name` varchar(50) DEFAULT NULL,
  `status` tinyint NOT NULL COMMENT '0-待审核 1-已通过 2-已驳回',
  `applicant_id` bigint DEFAULT NULL,
  `apply_time` datetime DEFAULT NULL,
  `auditor_id` bigint DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_status_node` (`status`,`flow_node_id`),
  KEY `idx_auditor` (`auditor_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表，统一记录待办、已办和审核流转过程';

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint NOT NULL,
  `module` varchar(50) DEFAULT NULL,
  `action` varchar(100) DEFAULT NULL,
  `target_type` varchar(50) DEFAULT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `detail` varchar(1000) DEFAULT NULL COMMENT 'JSON，含变更前后值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表，记录评级、额度和其他关键后台操作';

-- 为已存在的数据库同步表级注释；重复执行不会改变表结构和业务数据。
ALTER TABLE `sys_user` COMMENT='系统用户表，保存后台管理员和门户用户账号信息';
ALTER TABLE `sys_role` COMMENT='系统角色表，定义管理员角色及职责';
ALTER TABLE `sys_user_role` COMMENT='用户角色关联表，维护用户与角色的多对多关系';
ALTER TABLE `sys_permission` COMMENT='系统权限表，保存菜单和按钮权限定义';
ALTER TABLE `sys_role_permission` COMMENT='角色权限关联表，维护角色可访问的菜单和按钮';
ALTER TABLE `portal_user_info` COMMENT='门户用户扩展信息表，保存实名认证、银行卡和授信资料';
ALTER TABLE `user_address` COMMENT='用户收货地址表，支持一个用户维护多个地址';
ALTER TABLE `product_category` COMMENT='商品分类表，支持多级分类树';
ALTER TABLE `product` COMMENT='商品主表，保存农资商品价格、库存和上下架信息';
ALTER TABLE `shopping_cart` COMMENT='购物车表，保存用户待购买商品和数量';
ALTER TABLE `order_main` COMMENT='订单主表，保存订单金额、状态、支付和收货快照';
ALTER TABLE `order_detail` COMMENT='订单明细表，保存商品成交信息和分类快照';
ALTER TABLE `order_attachment` COMMENT='订单附件表，保存客户回传单和签章发货单';
ALTER TABLE `refund_apply` COMMENT='退款申请表，记录退款金额、原因、审核和原路退款状态';
ALTER TABLE `order_comment` COMMENT='订单评价表，保存用户对已完成订单商品的评价';
ALTER TABLE `wallet_account` COMMENT='钱包账户表，保存用户虚拟钱包余额和冻结金额';
ALTER TABLE `wallet_transaction` COMMENT='钱包资金流水表，记录入金、出金、支付和退款流水';
ALTER TABLE `loan_record` COMMENT='贷款记录表，保存贷款申请、放款、还款及授信占用信息';
ALTER TABLE `invoice_info` COMMENT='发票抬头信息表，保存用户开票资料';
ALTER TABLE `invoice_apply` COMMENT='开票申请表，记录订单开票申请和处理结果';
ALTER TABLE `article` COMMENT='资讯文章表，保存门户行业资讯和公司消息';
ALTER TABLE `article_category` COMMENT='资讯栏目表，维护文章分类栏目';
ALTER TABLE `message_config` COMMENT='消息配置表，维护站内消息模板和启用状态';
ALTER TABLE `message_record` COMMENT='消息记录表，保存用户待办提醒和系统通知';
ALTER TABLE `audit_flow` COMMENT='审核流程表，按业务类型配置可用审核流程';
ALTER TABLE `audit_node` COMMENT='审核节点表，配置流程中的审核角色和顺序';
ALTER TABLE `audit_record` COMMENT='审核记录表，统一记录待办、已办和审核流转过程';
ALTER TABLE `sys_oper_log` COMMENT='系统操作日志表，记录评级、额度和其他关键后台操作';

-- 初始化角色、管理员、菜单、分类、审核流和消息模板。
INSERT INTO `sys_role` (`id`,`role_name`,`description`) VALUES
  (1,'超级管理员','系统全部权限'),
  (2,'运营主管','商品/订单/退款终审，用户评级，财务'),
  (3,'销售员','商品维护、订单销售审核、物流登记'),
  (4,'客服','门户用户咨询支持、实名认证材料预检协助')
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`), `description`=VALUES(`description`);

INSERT INTO `sys_user` (`id`,`username`,`password`,`real_name`,`user_type`,`status`) VALUES
  (1,'admin','$2b$10$9TEuQ4cWQTjJOAGN0OEJnOKZbv3wJePvPtPSXkoDh2BFqDgvTtaBC','系统管理员',3,1)
ON DUPLICATE KEY UPDATE `real_name`=VALUES(`real_name`), `user_type`=VALUES(`user_type`), `status`=VALUES(`status`);

INSERT INTO `sys_user_role` (`user_id`,`role_id`) VALUES (1,1)
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`);

INSERT INTO `sys_permission` (`id`,`parent_id`,`name`,`type`,`perms`,`sort`) VALUES
  (1,0,'首页看板',1,'admin:home',1),
  (2,0,'任务管理',1,'admin:task',2),
  (3,0,'商品管理',1,'admin:product',3),
  (4,0,'订单管理',1,'admin:order',4),
  (5,0,'用户管理',1,'admin:portal-user',5),
  (6,0,'财务管理',1,'admin:finance',6),
  (7,0,'资讯管理',1,'admin:article',7),
  (8,0,'系统管理',1,'admin:system',8)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `perms`=VALUES(`perms`), `sort`=VALUES(`sort`);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT 1, `id` FROM `sys_permission`;

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT r.id, p.id FROM `sys_role` r JOIN `sys_permission` p
WHERE (r.id = 2 AND p.perms IN ('admin:finance','admin:order','admin:task'))
   OR (r.id = 3 AND p.perms IN ('admin:order','admin:task'));

INSERT INTO `product_category` (`id`,`parent_id`,`name`,`sort`) VALUES
  (1,0,'肥料',1),(2,0,'农药',2),(3,0,'种子',3),(4,0,'农资用品',4),
  (11,1,'氮肥',1),(12,1,'磷肥',2),(13,1,'钾肥',3),(14,1,'复合肥',4),(15,1,'有机肥',5),
  (21,2,'杀虫剂',1),(22,2,'杀菌剂',2),(23,2,'除草剂',3),
  (31,3,'粮食种子',1),(32,3,'蔬菜种子',2),
  (41,4,'农膜',1),(42,4,'农机具',2)
ON DUPLICATE KEY UPDATE `parent_id`=VALUES(`parent_id`), `name`=VALUES(`name`), `sort`=VALUES(`sort`);

INSERT INTO `article_category` (`id`,`name`,`sort`) VALUES
  (1,'行业动态',1),(2,'公司新闻',2)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `sort`=VALUES(`sort`);

INSERT INTO `audit_flow` (`id`,`biz_type`,`flow_name`) VALUES
  (1,1,'实名认证审核'),(2,2,'商品上架审核'),(3,3,'商品量价修改审核'),
  (4,4,'订单审核'),(5,5,'退款审核'),(6,6,'贷款审核')
ON DUPLICATE KEY UPDATE `flow_name`=VALUES(`flow_name`), `enabled`=1;

INSERT INTO `audit_node` (`flow_id`,`node_name`,`role_id`,`node_order`) VALUES
  (1,'认证审核',2,1),(2,'上架审核',2,1),(3,'量价审核',2,1),
  (4,'销售审核',3,1),(4,'主管审核',2,2),(5,'退款审核',2,1),(6,'贷款审核',2,1)
ON DUPLICATE KEY UPDATE `node_name`=VALUES(`node_name`), `role_id`=VALUES(`role_id`);

INSERT INTO `message_config` (`code`,`title`,`content_template`) VALUES
  ('AUTH_RESULT','实名认证结果','您的实名认证已{结果}。{备注}'),
  ('PRODUCT_AUDIT_RESULT','商品审核结果','商品《{商品名}》审核{结果}。{备注}'),
  ('ORDER_AUDIT_RESULT','订单审核结果','订单{订单号}审核{结果}。{备注}'),
  ('ORDER_SHIPPED','订单发货通知','订单{订单号}已发货，物流公司：{物流公司}，单号：{物流单号}。'),
  ('REFUND_RESULT','退款处理结果','订单{订单号}退款申请已{结果}。{备注}'),
  ('LOAN_RESULT','贷款处理结果','您的贷款申请（{贷款编号}）已{结果}。{备注}'),
  ('TODO_REMIND','新的待办事项','您有新的{业务类型}待审核，请及时处理。'),
  ('SYSTEM_NOTICE','系统通知','{内容}')
ON DUPLICATE KEY UPDATE `title`=VALUES(`title`), `content_template`=VALUES(`content_template`);
