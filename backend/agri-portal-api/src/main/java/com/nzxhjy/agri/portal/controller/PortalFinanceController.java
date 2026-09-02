package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.FinanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalFinanceController {
    private final FinanceService financeService;

    @PostMapping("/refund/apply")
    public Result<FinanceService.RefundView> applyRefund(@Valid @RequestBody RefundRequest request) {
        return Result.success(financeService.applyRefund(UserContext.getUserId(), request.orderId, request.amount, request.reason));
    }
    @GetMapping("/refund/{orderId}")
    public Result<FinanceService.RefundView> refund(@PathVariable Long orderId) { return Result.success(financeService.refund(UserContext.getUserId(), orderId)); }

    @PostMapping("/comment/add")
    public Result<Void> addComment(@Valid @RequestBody CommentRequest request) { financeService.addComment(UserContext.getUserId(), request.orderId, request.productId, request.score, request.content, request.images); return Result.success(); }
    @GetMapping("/comment/page")
    public Result<PageResult<FinanceService.CommentView>> comments(@RequestParam(required = false) Long productId, @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) { return Result.success(financeService.commentPage(productId, pageNum, Math.min(pageSize, 100))); }

    @GetMapping("/wallet/info") public Result<FinanceService.WalletView> walletInfo() { return Result.success(financeService.walletInfo(UserContext.getUserId())); }
    @GetMapping("/wallet/transactions") public Result<PageResult<FinanceService.TransactionView>> transactions(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) Integer transType) { return Result.success(financeService.transactions(UserContext.getUserId(), pageNum, Math.min(pageSize, 100), transType)); }
    @PostMapping("/wallet/deposit") public Result<FinanceService.WalletView> deposit(@Valid @RequestBody AmountRequest request) { return Result.success(financeService.deposit(UserContext.getUserId(), request.amount)); }
    @PostMapping("/wallet/withdraw") public Result<FinanceService.WalletView> withdraw(@Valid @RequestBody AmountRequest request) { return Result.success(financeService.withdraw(UserContext.getUserId(), request.amount)); }

    @GetMapping("/invoice-info/list") public Result<java.util.List<FinanceService.InvoiceInfoView>> invoiceInfos() { return Result.success(financeService.invoiceInfos(UserContext.getUserId())); }
    @PostMapping("/invoice-info") public Result<FinanceService.InvoiceInfoView> createInvoiceInfo(@Valid @RequestBody InvoiceInfoRequest request) { return Result.success(financeService.saveInvoiceInfo(UserContext.getUserId(), null, request.titleType, request.title, request.taxNo, request.bankName, request.bankAccount, request.regAddress, request.phone, request.isDefault)); }
    @PutMapping("/invoice-info/{id}") public Result<FinanceService.InvoiceInfoView> updateInvoiceInfo(@PathVariable Long id, @Valid @RequestBody InvoiceInfoRequest request) { return Result.success(financeService.saveInvoiceInfo(UserContext.getUserId(), id, request.titleType, request.title, request.taxNo, request.bankName, request.bankAccount, request.regAddress, request.phone, request.isDefault)); }
    @DeleteMapping("/invoice-info/{id}") public Result<Void> deleteInvoiceInfo(@PathVariable Long id) { financeService.deleteInvoiceInfo(UserContext.getUserId(), id); return Result.success(); }
    @PostMapping("/invoice/apply") public Result<FinanceService.InvoiceApplyView> applyInvoice(@Valid @RequestBody InvoiceApplyRequest request) { return Result.success(financeService.applyInvoice(UserContext.getUserId(), request.orderId, request.invoiceInfoId, request.amount)); }
    @GetMapping("/invoice/list") public Result<PageResult<FinanceService.InvoiceApplyView>> invoicePage(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) Integer status) { return Result.success(financeService.invoicePage(UserContext.getUserId(), pageNum, Math.min(pageSize, 100), status)); }

    @PostMapping("/loan/apply") public Result<FinanceService.LoanRecordView> applyLoan(@Valid @RequestBody AmountRequest request) { return Result.success(financeService.applyLoan(UserContext.getUserId(), request.amount)); }
    @GetMapping("/loan/info") public Result<FinanceService.LoanInfo> loanInfo() { return Result.success(financeService.loanInfo(UserContext.getUserId())); }

    @Data public static class RefundRequest { @NotNull Long orderId; @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount; @Size(max = 255) String reason; }
    @Data public static class CommentRequest { @NotNull Long orderId; @NotNull Long productId; @NotNull @Min(1) @Max(5) Integer score; @Size(max = 500) String content; @Size(max = 1000) String images; }
    @Data public static class AmountRequest { @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount; }
    @Data public static class InvoiceInfoRequest { Integer titleType; @NotBlank @Size(max = 100) String title; @Size(max = 50) String taxNo; @Size(max = 50) String bankName; @Size(max = 100) String bankAccount; @Size(max = 200) String regAddress; @Size(max = 20) String phone; Integer isDefault; }
    @Data public static class InvoiceApplyRequest { @NotNull Long orderId; @NotNull Long invoiceInfoId; @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount; }
}
