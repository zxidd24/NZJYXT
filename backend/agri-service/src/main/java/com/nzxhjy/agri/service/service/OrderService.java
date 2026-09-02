package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.service.entity.*;
import com.nzxhjy.agri.service.mapper.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final ShoppingCartMapper cartMapper;
    private final OrderMainMapper orderMapper;
    private final OrderDetailMapper detailMapper;
    private final OrderAttachmentMapper attachmentMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final UserAddressMapper addressMapper;
    private final PortalUserInfoMapper userInfoMapper;
    private final WalletAccountMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;
    private final RefundApplyMapper refundMapper;
    private final RedisUtils redisUtils;
    private final AuditFlowMapper auditFlowMapper;
    private final AuditNodeMapper auditNodeMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final MessageService messageService;
    private final AccessControlService accessControlService;

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public List<CartView> cartList(Long userId) {
        return cartMapper.selectList(Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId)
                .orderByDesc(ShoppingCart::getUpdatedAt)).stream().map(this::cartView).toList();
    }

    @Transactional
    public void addCart(Long userId, Long productId, int quantity) {
        checkQuantity(quantity);
        Product product = requireOnSale(productId);
        if (product.getStock() < quantity) throw business("库存不足");
        ShoppingCart cart = cartMapper.selectOne(Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId).eq(ShoppingCart::getProductId, productId));
        if (cart == null) {
            cart = new ShoppingCart(); cart.setUserId(userId); cart.setProductId(productId); cart.setQuantity(quantity); cart.setSelected(1); cartMapper.insert(cart);
        } else {
            int total = cart.getQuantity() + quantity;
            if (total > product.getStock()) throw business("购物车数量超过库存");
            cart.setQuantity(total); cartMapper.updateById(cart);
        }
    }

    @Transactional
    public void updateCart(Long userId, Long productId, int quantity, Integer selected) {
        checkQuantity(quantity);
        ShoppingCart cart = requireCart(userId, productId);
        Product product = requireOnSale(productId);
        if (quantity > product.getStock()) throw business("购物车数量超过库存");
        cart.setQuantity(quantity); if (selected != null) cart.setSelected(selected == 1 ? 1 : 0); cartMapper.updateById(cart);
    }

    @Transactional public void deleteCart(Long userId, Long productId) { cartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId).eq(ShoppingCart::getProductId, productId)); }

    public String orderToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(orderTokenKey(token), String.valueOf(userId), Duration.ofMinutes(10));
        return token;
    }

    @Transactional
    public OrderView create(Long userId, String token, Long addressId, List<ItemCommand> items, String buyerNote) {
        if (token == null || !String.valueOf(userId).equals(redisUtils.get(orderTokenKey(token)))) throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "下单令牌无效或已使用");
        redisUtils.delete(orderTokenKey(token));
        PortalUserInfo info = userInfoMapper.selectById(userId);
        if (info == null || !Integer.valueOf(StatusEnums.AuthStatus.VERIFIED.value).equals(info.getAuthStatus())) throw business("请先完成实名认证");
        UserAddress address = addressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getId, addressId).eq(UserAddress::getUserId, userId));
        if (address == null) throw business("收货地址不存在");
        if (items == null || items.isEmpty()) throw business("请选择商品");
        OrderMain order = new OrderMain(); BigDecimal total = BigDecimal.ZERO; List<OrderDetail> details = new ArrayList<>();
        for (ItemCommand item : items) {
            if (item == null || item.productId() == null || item.quantity() == null) throw business("商品参数错误"); checkQuantity(item.quantity());
            Product product = requireOnSale(item.productId());
            int updated = productMapper.update(null, Wrappers.<Product>lambdaUpdate().eq(Product::getId, product.getId()).eq(Product::getStatus, StatusEnums.ProductStatus.ON_SALE.value).ge(Product::getStock, item.quantity()).setSql("stock = stock - " + item.quantity()));
            if (updated == 0) throw business("商品库存不足或已下架");
            BigDecimal line = product.getPrice().multiply(BigDecimal.valueOf(item.quantity())); total = total.add(line);
            OrderDetail detail = new OrderDetail(); detail.setProductId(product.getId()); detail.setProductName(product.getName()); detail.setCategoryId(product.getCategoryId()); detail.setCategoryName(categoryName(product.getCategoryId())); detail.setProductPrice(product.getPrice()); detail.setUnit(product.getUnit()); detail.setQuantity(item.quantity()); detail.setTotalPrice(line); details.add(detail);
            cartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId).eq(ShoppingCart::getProductId, product.getId()));
        }
        order.setOrderNo(generateNo("O")); order.setUserId(userId); order.setTotalAmount(total); order.setPayAmount(total); order.setFreight(BigDecimal.ZERO); order.setOrderStatus(StatusEnums.OrderStatus.PENDING_PAYMENT.value); order.setPayStatus(StatusEnums.PayStatus.UNPAID.value); order.setAddressId(addressId); order.setReceiverName(address.getReceiverName()); order.setReceiverPhone(address.getReceiverPhone()); order.setReceiverAddress(String.join("", Optional.ofNullable(address.getProvince()).orElse(""), Optional.ofNullable(address.getCity()).orElse(""), Optional.ofNullable(address.getDistrict()).orElse(""), address.getDetailAddress())); order.setBuyerNote(buyerNote); orderMapper.insert(order);
        for (OrderDetail detail : details) { detail.setOrderId(order.getId()); detailMapper.insert(detail); }
        return detail(order.getId(), userId);
    }

    @Transactional
    public PayResult pay(Long userId, String orderNo, String payMethod) {
        OrderMain order = orderMapper.selectOwnedByNoForUpdate(orderNo, userId);
        if (order == null) throw business("订单不存在");
        if (Integer.valueOf(StatusEnums.PayStatus.PAID.value).equals(order.getPayStatus())) return new PayResult(true, order.getOrderStatus(), "订单已支付");
        if (!Integer.valueOf(StatusEnums.OrderStatus.PENDING_PAYMENT.value).equals(order.getOrderStatus())) throw business("当前订单不可支付");
        String method = payMethod == null ? "WALLET" : payMethod.toUpperCase(Locale.ROOT);
        if (!Set.of("WALLET", "BANK_TRANSFER", "CREDIT").contains(method)) throw business("支付方式不支持");
        if ("WALLET".equals(method)) payFromWallet(userId, order);
        order.setPayMethod(method); order.setPayStatus(StatusEnums.PayStatus.PAID.value); order.setPayTime(LocalDateTime.now()); order.setOrderStatus(StatusEnums.OrderStatus.PENDING_AUDIT.value); orderMapper.updateById(order);
        createOrderAudit(order); return new PayResult(true, order.getOrderStatus(), "支付成功");
    }

    public PageResult<OrderView> page(Long userId, int pageNum, int pageSize, Integer status) {
        var query = Wrappers.<OrderMain>lambdaQuery().eq(OrderMain::getUserId, userId).orderByDesc(OrderMain::getCreatedAt);
        if (status != null) query.eq(OrderMain::getOrderStatus, status);
        IPage<OrderMain> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(o -> detail(o.getId(), userId)).toList());
    }

    public OrderView detail(Long id, Long userId) {
        OrderMain order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw business("订单不存在");
        return view(order);
    }

    @Transactional
    public void cancel(Long userId, String orderNo) {
        OrderMain order = requireOwnedByNo(userId, orderNo);
        if (!Integer.valueOf(StatusEnums.OrderStatus.PENDING_PAYMENT.value).equals(order.getOrderStatus())) throw business("只有待付款订单可以取消");
        cancelAndRestore(order);
    }

    @Transactional
    public void rebuy(Long userId, Long orderId) {
        OrderMain order = orderMapper.selectById(orderId); if (order == null || !Objects.equals(order.getUserId(), userId)) throw business("订单不存在");
        for (OrderDetail detail : details(orderId)) { Product product = productMapper.selectById(detail.getProductId()); if (product != null && product.getStatus() == StatusEnums.ProductStatus.ON_SALE.value && product.getStock() > 0) addCart(userId, product.getId(), Math.min(detail.getQuantity(), product.getStock())); }
    }

    public List<OrderAttachment> attachments(Long userId, Long orderId, boolean admin) {
        OrderMain order = orderMapper.selectById(orderId); if (order == null || (!admin && !Objects.equals(order.getUserId(), userId))) throw business("订单不存在");
        return attachmentMapper.selectList(Wrappers.<OrderAttachment>lambdaQuery().eq(OrderAttachment::getOrderId, orderId).orderByDesc(OrderAttachment::getCreatedAt));
    }

    @Transactional public void addAttachment(Long userId, Long orderId, int type, String fileUrl, String fileName) { OrderMain order = orderMapper.selectById(orderId); if (order == null || !Objects.equals(order.getUserId(), userId)) throw business("订单不存在"); if (type == 1 && order.getOrderStatus() != StatusEnums.OrderStatus.PENDING_RECEIPT.value) throw business("当前订单不可上传回传单"); OrderAttachment a = new OrderAttachment(); a.setOrderId(orderId); a.setType(type); a.setFileUrl(fileUrl); a.setFileName(fileName); a.setUploadedBy(userId); attachmentMapper.insert(a); }
    public byte[] voucher(Long userId, Long orderId) { OrderView v = detail(orderId, userId); if (v.orderStatus < 1 || v.payStatus != 1) throw business("支付并审核通过后才能下载凭证"); String text = "AGRI TRADING VOUCHER\nOrder: " + v.orderNo + "\nAmount: " + v.payAmount + "\nReceiver: " + v.receiverName + " " + v.receiverAddress; return simplePdf(text); }

    public PageResult<OrderView> adminPage(int pageNum, int pageSize, Integer status, String orderNo, Long categoryId) {
        IPage<OrderMain> page = orderMapper.selectAdminPage(new Page<>(pageNum, pageSize), status,
                orderNo == null ? null : orderNo.trim(), categoryId);
        List<OrderView> rows = page.getRecords().stream().map(this::view).toList();
        return new PageResult<>(page.getTotal(), pageNum, pageSize, rows);
    }

    @Transactional
    public void auditOrder(Long auditorId, Long recordId, boolean approved, String remark) {
        AuditRecord record = auditRecordMapper.selectById(recordId); if (record == null || record.getBizType() != StatusEnums.AuditBizType.ORDER.value) throw business("订单审核任务不存在"); if (!canAudit(auditorId, record)) throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage()); if (!approved && (remark == null || remark.isBlank())) throw business("驳回时必须填写原因");
        int updated = auditRecordMapper.update(null, Wrappers.<AuditRecord>lambdaUpdate().eq(AuditRecord::getId, recordId).eq(AuditRecord::getStatus, 0).set(AuditRecord::getStatus, approved ? 1 : 2).set(AuditRecord::getAuditorId, auditorId).set(AuditRecord::getAuditTime, LocalDateTime.now()).set(AuditRecord::getRemark, remark)); if (updated == 0) throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "该任务已处理");
        OrderMain order = orderMapper.selectById(record.getBizId()); if (!approved) { cancelAndRestore(order); notifyAudit(order, "驳回", remark); return; }
        AuditNode current = auditNodeMapper.selectById(record.getFlowNodeId()); AuditNode next = auditNodeMapper.selectOne(Wrappers.<AuditNode>lambdaQuery().eq(AuditNode::getFlowId, current.getFlowId()).gt(AuditNode::getNodeOrder, current.getNodeOrder()).orderByAsc(AuditNode::getNodeOrder).last("LIMIT 1"));
        if (next != null) { AuditRecord nextRecord = new AuditRecord(); nextRecord.setBizType(4); nextRecord.setBizId(order.getId()); nextRecord.setBizNo(order.getOrderNo()); nextRecord.setBizSummary("订单审核：" + order.getOrderNo()); nextRecord.setFlowNodeId(next.getId()); nextRecord.setNodeName(next.getNodeName()); nextRecord.setStatus(0); nextRecord.setApplicantId(order.getUserId()); nextRecord.setApplyTime(LocalDateTime.now()); auditRecordMapper.insert(nextRecord); order.setAuditNodeId(next.getId()); orderMapper.updateById(order); }
        else { order.setOrderStatus(StatusEnums.OrderStatus.PENDING_SHIPMENT.value); orderMapper.updateById(order); }
        notifyAudit(order, "通过", next == null ? "" : "进入" + next.getNodeName());
    }

    @Transactional
    public void delivery(Long adminId, Long orderId, String company, String trackingNo, String fileUrl, String fileName) { OrderMain order = orderMapper.selectById(orderId); if (order == null) throw business("订单不存在"); if (order.getOrderStatus() != StatusEnums.OrderStatus.PENDING_SHIPMENT.value) throw business("当前订单不可登记发货"); order.setSellerNote(company + " " + trackingNo); order.setOrderStatus(StatusEnums.OrderStatus.PENDING_RECEIPT.value); orderMapper.updateById(order); if (fileUrl != null && !fileUrl.isBlank()) { OrderAttachment a = new OrderAttachment(); a.setOrderId(orderId); a.setType(2); a.setFileUrl(fileUrl); a.setFileName(fileName); a.setUploadedBy(adminId); attachmentMapper.insert(a); } messageService.send(order.getUserId(), "ORDER_SHIPPED", 4, orderId, java.util.Map.of("订单号", order.getOrderNo(), "物流公司", company, "物流单号", trackingNo)); }
    @Transactional public void confirm(Long adminId, Long orderId) { OrderMain order = orderMapper.selectById(orderId); if (order == null || order.getOrderStatus() != StatusEnums.OrderStatus.PENDING_RECEIPT.value) throw business("当前订单不可确认"); long receipt = attachmentMapper.selectCount(Wrappers.<OrderAttachment>lambdaQuery().eq(OrderAttachment::getOrderId, orderId).eq(OrderAttachment::getType, 1)); if (receipt == 0) throw business("请先上传客户盖章回传单"); order.setOrderStatus(StatusEnums.OrderStatus.COMPLETED.value); orderMapper.updateById(order); }

    @Transactional
    public void cancelExpired() {
        List<OrderMain> expired = orderMapper.selectList(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getOrderStatus, StatusEnums.OrderStatus.PENDING_PAYMENT.value)
                .lt(OrderMain::getCreatedAt, LocalDateTime.now().minusMinutes(30))
                .last("LIMIT 100"));
        for (OrderMain order : expired) {
            cancelAndRestore(order);
        }
    }

    private void payFromWallet(Long userId, OrderMain order) { WalletAccount account = walletMapper.selectOne(Wrappers.<WalletAccount>lambdaQuery().eq(WalletAccount::getUserId, userId)); if (account == null || account.getBalance() == null || account.getBalance().compareTo(order.getPayAmount()) < 0) throw business("钱包余额不足"); int updated = walletMapper.update(null, Wrappers.<WalletAccount>lambdaUpdate().eq(WalletAccount::getUserId, userId).ge(WalletAccount::getBalance, order.getPayAmount()).setSql("balance = balance - " + order.getPayAmount())); if (updated == 0) throw business("钱包余额不足"); BigDecimal after = account.getBalance().subtract(order.getPayAmount()); WalletTransaction tx = new WalletTransaction(); tx.setUserId(userId); tx.setOrderId(order.getId()); tx.setTransNo(generateNo("T")); tx.setAmount(order.getPayAmount()); tx.setDirection(2); tx.setTransType(3); tx.setTransStatus(1); tx.setBalanceAfter(after); tx.setRemark("订单支付"); transactionMapper.insert(tx); }
    private void createOrderAudit(OrderMain order) { AuditFlow flow = auditFlowMapper.selectOne(Wrappers.<AuditFlow>lambdaQuery().eq(AuditFlow::getBizType, 4).eq(AuditFlow::getEnabled, 1)); AuditNode node = flow == null ? null : auditNodeMapper.selectOne(Wrappers.<AuditNode>lambdaQuery().eq(AuditNode::getFlowId, flow.getId()).orderByAsc(AuditNode::getNodeOrder).last("LIMIT 1")); if (node == null) throw business("订单审核流程未配置"); AuditRecord r = new AuditRecord(); r.setBizType(4); r.setBizId(order.getId()); r.setBizNo(order.getOrderNo()); r.setBizSummary("订单审核：" + order.getOrderNo()); r.setFlowNodeId(node.getId()); r.setNodeName(node.getNodeName()); r.setStatus(0); r.setApplicantId(order.getUserId()); r.setApplyTime(LocalDateTime.now()); auditRecordMapper.insert(r); order.setAuditNodeId(node.getId()); orderMapper.updateById(order); messageService.sendTodo(node.getId(), 4, order.getId()); }
    private void cancelAndRestore(OrderMain order) {
        if (order == null) return;
        int updated = orderMapper.update(null, Wrappers.<OrderMain>update()
                .eq("id", order.getId())
                .eq("deleted", 0)
                .ne("order_status", StatusEnums.OrderStatus.CANCELLED.value)
                .set("order_status", StatusEnums.OrderStatus.CANCELLED.value)
                .set("cancel_time", LocalDateTime.now()));
        if (updated == 0) return;
        for (OrderDetail d : details(order.getId())) {
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, d.getProductId())
                    .setSql("stock = stock + " + d.getQuantity()));
        }
        order.setOrderStatus(StatusEnums.OrderStatus.CANCELLED.value);
        order.setCancelTime(LocalDateTime.now());
    }
    private boolean canAudit(Long userId, AuditRecord record) { if (accessControlService.isSuperAdmin(userId)) return true; AuditNode node = auditNodeMapper.selectById(record.getFlowNodeId()); return node != null && accessControlService.roleIds(userId).contains(node.getRoleId()); }
    private void notifyAudit(OrderMain order, String result, String remark) { messageService.send(order.getUserId(), "ORDER_AUDIT_RESULT", 4, order.getId(), java.util.Map.of("订单号", order.getOrderNo(), "结果", result, "备注", remark == null ? "" : remark)); }
    private List<OrderDetail> details(Long orderId) { return detailMapper.selectList(Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, orderId)); }
    private OrderView view(OrderMain o) { return new OrderView(o.getId(), o.getOrderNo(), o.getTotalAmount(), o.getPayAmount(), o.getOrderStatus(), o.getPayStatus(), o.getPayMethod(), o.getReceiverName(), o.getReceiverPhone(), o.getReceiverAddress(), o.getSellerNote(), details(o.getId()).stream().map(d -> new DetailView(d.getProductId(), d.getProductName(), d.getCategoryId(), d.getCategoryName(), d.getProductPrice(), d.getQuantity(), d.getTotalPrice(), d.getUnit())).toList()); }
    private CartView cartView(ShoppingCart c) { Product p = productMapper.selectById(c.getProductId()); return new CartView(c.getProductId(), p == null ? null : p.getName(), p == null ? null : p.getPrice(), c.getQuantity(), c.getSelected(), p == null ? null : p.getStock(), p == null ? null : p.getImages()); }
    private Product requireOnSale(Long id) { Product p = productMapper.selectById(id); if (p == null || p.getStatus() != StatusEnums.ProductStatus.ON_SALE.value) throw business("商品不存在或已下架"); return p; }
    private ShoppingCart requireCart(Long userId, Long productId) { ShoppingCart c = cartMapper.selectOne(Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId).eq(ShoppingCart::getProductId, productId)); if (c == null) throw business("购物车商品不存在"); return c; }
    private OrderMain requireOwnedByNo(Long userId, String no) { OrderMain o = orderMapper.selectOne(Wrappers.<OrderMain>lambdaQuery().eq(OrderMain::getOrderNo, no).eq(OrderMain::getUserId, userId)); if (o == null) throw business("订单不存在"); return o; }
    private String categoryName(Long id) { ProductCategory c = id == null ? null : categoryMapper.selectById(id); return c == null ? null : c.getName(); }
    private void checkQuantity(int q) { if (q <= 0 || q > 1_000_000) throw business("商品数量必须大于0"); }
    private BusinessException business(String message) { return new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), message); }
    private String generateNo(String prefix) { return prefix + LocalDateTime.now().format(NO_FORMAT) + String.format("%04d", new Random().nextInt(10000)); }
    private String orderTokenKey(String token) { return "agri:order:token:" + token; }

    private byte[] simplePdf(String text) {
        String[] lines = text.split("\\n", -1);
        StringBuilder content = new StringBuilder("BT /F1 12 Tf 50 780 Td ");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) content.append("0 -16 Td ");
            content.append('<').append(toPdfUnicodeHex(lines[i])).append("> Tj ");
        }
        content.append("ET");
        byte[] contentBytes = content.toString().getBytes(StandardCharsets.US_ASCII);
        String[] objects = {
                "<</Type/Catalog/Pages 2 0 R>>",
                "<</Type/Pages/Count 1/Kids[3 0 R]>>",
                "<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>",
                "<</Type/Font/Subtype/Type0/BaseFont/STSong-Light/Encoding/UniGB-UCS2-H/DescendantFonts[6 0 R]>>",
                "<</Length " + contentBytes.length + ">>stream\n" + new String(contentBytes, StandardCharsets.US_ASCII) + "\nendstream",
                "<</Type/Font/Subtype/CIDFontType0/BaseFont/STSong-Light/CIDSystemInfo<</Registry(Adobe)/Ordering(GB1)/Supplement 5>>/DW 1000>>"
        };
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            int[] offsets = new int[objects.length + 1];
            for (int i = 0; i < objects.length; i++) {
                offsets[i + 1] = out.size();
                out.write((i + 1 + " 0 obj\n" + objects[i] + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
            }
            int xrefOffset = out.size();
            out.write(("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
            for (int i = 1; i < offsets.length; i++) {
                out.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.US_ASCII));
            }
            out.write(("trailer\n<</Size " + (objects.length + 1) + "/Root 1 0 R>>\nstartxref\n" + xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("电子凭证生成失败", exception);
        }
    }

    private String toPdfUnicodeHex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
        StringBuilder hex = new StringBuilder("FEFF");
        for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02X", b & 0xff));
        return hex.toString();
    }

    public record ItemCommand(Long productId, Integer quantity) {}
    @Data @AllArgsConstructor public static class CartView { private Long productId; private String productName; private BigDecimal price; private Integer quantity; private Integer selected; private Integer stock; private String images; }
    @Data @AllArgsConstructor public static class DetailView { private Long productId; private String productName; private Long categoryId; private String categoryName; private BigDecimal productPrice; private Integer quantity; private BigDecimal totalPrice; private String unit; }
    @Data @AllArgsConstructor public static class OrderView { private Long id; private String orderNo; private BigDecimal totalAmount; private BigDecimal payAmount; private Integer orderStatus; private Integer payStatus; private String payMethod; private String receiverName; private String receiverPhone; private String receiverAddress; private String sellerNote; private List<DetailView> details; }
    @Data @AllArgsConstructor public static class PayResult { private boolean success; private int orderStatus; private String message; }
}
