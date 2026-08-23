package com.grab.controller;

import com.grab.common.Result;
import com.grab.entity.GrabOrder;
import com.grab.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单 Controller
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 抢购
     */
    @PostMapping("/grab")
    public Result<GrabOrder> grab(@RequestParam Long userId,
                                   @RequestParam Long activityId,
                                   @RequestParam(defaultValue = "1") Integer quantity) {
        GrabOrder order = orderService.grab(userId, activityId, quantity);
        return Result.ok("抢购成功", order);
    }

    /**
     * 查询用户订单
     */
    @GetMapping("/list")
    public Result<List<GrabOrder>> listByUserId(@RequestParam Long userId) {
        return Result.ok(orderService.listByUserId(userId));
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<GrabOrder> getByOrderNo(@PathVariable String orderNo) {
        GrabOrder order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.ok(order);
    }

    /**
     * 支付订单
     */
    @PostMapping("/pay")
    public Result<String> pay(@RequestParam String orderNo) {
        orderService.pay(orderNo);
        return Result.ok("支付成功", null);
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel")
    public Result<String> cancel(@RequestParam String orderNo) {
        orderService.cancel(orderNo);
        return Result.ok("取消成功", null);
    }
}
