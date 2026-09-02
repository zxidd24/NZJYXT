package com.nzxhjy.agri.service.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.nzxhjy.agri.service.service.OrderService;
import lombok.RequiredArgsConstructor;

/** 订单超时取消任务骨架，订单模块完成后接入库存回补逻辑。 */
@Component
@ConditionalOnProperty(name = "agri.order-timeout.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderTimeoutJob {
    private final OrderService orderService;
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    @Scheduled(fixedDelayString = "${agri.order-timeout.fixed-delay-ms:60000}")
    public void cancelExpiredOrders() {
        orderService.cancelExpired();
        log.debug("Order timeout job executed");
    }
}
