package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequirePermission("admin:home")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardService.DashboardView> dashboard() {
        return Result.success(dashboardService.overview());
    }
}
