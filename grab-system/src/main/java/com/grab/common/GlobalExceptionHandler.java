package com.grab.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 唯一索引冲突（并发场景兜底，如重复抢购/重复用户名）
     * 说明：并发幂等兜底是预期内行为，不打日志（高并发下失败路径日志是性能杀手）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<String> handleDuplicateKeyException(DuplicateKeyException e) {
        return Result.error(400, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.error("系统异常，请稍后重试");
    }

    /**
     * 业务拒绝（库存不足/重复抢购/活动未开始等）
     * 说明：都是预期内的业务分支，不打日志（每请求一条 warn 在高并发下等同于日志风暴）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }
}
