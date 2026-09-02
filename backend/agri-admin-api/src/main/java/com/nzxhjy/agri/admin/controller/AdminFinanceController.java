package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.FinanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequirePermission("admin:finance")
@RequiredArgsConstructor
public class AdminFinanceController {
    private final FinanceService financeService;
    @GetMapping("/finance/account-detail") public Result<PageResult<FinanceService.TransactionView>> accountDetail(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Integer transType) { return Result.success(financeService.accountDetail(pageNum, Math.min(pageSize, 100), transType)); }
    @PostMapping("/finance/account-detail/{id}/voucher") public Result<Void> voucher(@PathVariable Long id, @RequestBody VoucherRequest request) { financeService.attachVoucher(id, request.voucherUrl); return Result.success(); }
    @GetMapping("/finance/income-expense") public Result<java.util.List<FinanceService.IncomeExpense>> incomeExpense(@RequestParam(defaultValue = "day") String period, @RequestParam(required = false) LocalDate date) { return Result.success(financeService.incomeExpense(period, date)); }
    @GetMapping("/finance/statement") public Result<PageResult<FinanceService.TransactionView>> statement(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Integer transType) { return Result.success(financeService.accountDetail(pageNum, Math.min(pageSize, 100), transType)); }
    @GetMapping("/finance/statement/export") public ResponseEntity<ByteArrayResource> exportStatement(@RequestParam(required = false) Integer transType) { byte[] bytes = financeService.statementExport(transType); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=finance-statement.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).contentLength(bytes.length).body(new ByteArrayResource(bytes)); }
    @GetMapping("/invoice/page") public Result<PageResult<FinanceService.InvoiceApplyView>> invoices(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Integer status) { return Result.success(financeService.invoicePage(null, pageNum, Math.min(pageSize, 100), status)); }
    @PostMapping("/invoice/{id}/issue") public Result<Void> issue(@PathVariable Long id, @Valid @RequestBody InvoiceIssueRequest request) { financeService.issueInvoice(UserContext.getUserId(), id, request.invoiceNo, request.remark); return Result.success(); }
    @PostMapping("/invoice/{id}/reject") public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) InvoiceRejectRequest request) { financeService.rejectInvoice(UserContext.getUserId(), id, request == null ? null : request.remark); return Result.success(); }
    @GetMapping("/loan/page") public Result<PageResult<FinanceService.LoanRecordView>> loans(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Integer status) { return Result.success(financeService.loanPage(null, pageNum, Math.min(pageSize, 100), status)); }
    @PostMapping("/loan/{id}/audit") public Result<Void> auditLoan(@PathVariable Long id, @Valid @RequestBody LoanAuditRequest request) { financeService.auditLoan(UserContext.getUserId(), id, request.approved, request.remark); return Result.success(); }
    @PostMapping("/loan/{id}/release") public Result<Void> releaseLoan(@PathVariable Long id) { financeService.releaseLoan(UserContext.getUserId(), id); return Result.success(); }
    @PostMapping("/loan/{id}/repay") public Result<Void> repayLoan(@PathVariable Long id) { financeService.repayLoan(UserContext.getUserId(), id); return Result.success(); }
    @Data public static class InvoiceIssueRequest { @NotBlank @Size(max = 50) String invoiceNo; @Size(max = 255) String remark; }
    @Data public static class InvoiceRejectRequest { @Size(max = 255) String remark; }
    @Data public static class LoanAuditRequest { @NotNull Boolean approved; @Size(max = 255) String remark; }
    @Data public static class VoucherRequest { @Size(max = 255) String voucherUrl; }
}
