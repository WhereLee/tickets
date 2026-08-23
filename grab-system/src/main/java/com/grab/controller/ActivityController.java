package com.grab.controller;

import com.grab.common.Result;
import com.grab.entity.Activity;
import com.grab.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动 Controller
 */
@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    /**
     * 查询所有活动
     */
    @GetMapping("/list")
    public Result<List<Activity>> list() {
        return Result.ok(activityService.listAll());
    }

    /**
     * 查询进行中的活动
     */
    @GetMapping("/active")
    public Result<List<Activity>> listActive() {
        return Result.ok(activityService.listActive());
    }

    /**
     * 查询活动详情
     */
    @GetMapping("/{id}")
    public Result<Activity> getById(@PathVariable Long id) {
        Activity activity = activityService.getById(id);
        if (activity == null) {
            return Result.error("活动不存在");
        }
        return Result.ok(activity);
    }

    /**
     * 创建活动
     */
    @PostMapping("/create")
    public Result<Activity> create(@RequestBody Activity activity) {
        return Result.ok("创建成功", activityService.create(activity));
    }
}
