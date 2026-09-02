package com.nzxhjy.agri.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nzxhjy.agri")
@MapperScan("com.nzxhjy.agri.service.mapper")
@EnableScheduling
public class AgriAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgriAdminApplication.class, args);
    }
}
