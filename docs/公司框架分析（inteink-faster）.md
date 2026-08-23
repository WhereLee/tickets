# 公司实习项目分析（inteink-faster）

> 基于 **人人开源** 框架二次开发的企业级后台管理系统，可作为面试项目的企业级参考。

---

## 一、技术栈总览

| 类别 | 技术选型 | 说明 |
|------|---------|------|
| **基础框架** | Spring Boot 2.6.11 | 主框架 |
| **持久层** | MyBatis-Plus 3.4.2 | ORM 框架 |
| **数据库** | MySQL 8.0 + Druid 连接池 | 支持多数据源动态切换 |
| **缓存** | Redis + Spring Session | 会话管理、数据缓存 |
| **文档存储** | MongoDB | 日志存储（日志量大，用文档库更合适） |
| **权限认证** | Shiro + JWT | 后台用 Shiro，APP 端用 JWT |
| **定时任务** | Quartz | 任务调度 |
| **接口文档** | Knife4j (Swagger 增强) | API 文档自动生成 |
| **工具库** | Hutool、FastJSON、Lombok | 日常开发工具 |
| **文件存储** | 七牛云 / 阿里云 OSS / 腾讯云 COS | 多云存储支持 |
| **消息队列** | Kafka（配置中有） | 异步消息处理 |

---

## 二、项目结构

```
inteink-faster/
├── inteink-main/                    # 主业务模块
│   └── src/main/java/com/inteink/
│       ├── common/                  # 公共组件
│       │   ├── annotation/          # 自定义注解（@SysLog、@DataFilter）
│       │   ├── aspect/              # AOP 切面（日志、数据过滤）
│       │   ├── exception/           # 统一异常处理
│       │   ├── utils/               # 工具类
│       │   └── validator/           # 参数校验
│       ├── config/                  # 配置类（Shiro、Redis、Swagger等）
│       ├── datasource/              # 多数据源动态切换
│       └── modules/                 # 业务模块
│           ├── sys/                 # 系统管理（用户、角色、菜单、日志）
│           ├── biz/                 # 业务核心（升降柱、策略、转换）
│           ├── job/                 # 定时任务
│           ├── oss/                 # 文件存储
│           ├── app/                 # APP 端接口（JWT 认证）
│           └── qyweixin/            # 企业微信集成
│
└── inteink-generator/               # 代码生成器模块
```

---

## 三、企业级开发亮点

| 亮点 | 实现方式 | 技术价值 |
|------|---------|--------|
| **AOP 日志系统** | `@SysLog` 注解 + 切面，记录操作人、IP、耗时、参数、返回值 | AOP 原理、切面设计、日志规范 |
| **多数据源动态切换** | `@DataSource` 注解 + AOP + AbstractRoutingDataSource | 数据源隔离、读写分离实现 |
| **MongoDB 存日志** | 日志量大，用文档库存储，减轻 MySQL 压力 | 存储选型：为什么选 MongoDB |
| **JWT + Shiro 双认证** | 后台用 Shiro Session，APP 端用 JWT Token | 两种认证方式的区别和适用场景 |
| **统一异常处理** | 全局异常捕获，返回统一格式 | 异常处理规范、用户体验 |
| **数据权限过滤** | `@DataFilter` 注解 + AOP，自动拼接 SQL 条件 | 数据权限设计、SQL 注入风险 |
| **接口文档自动生成** | Knife4j 注解，开发时自动维护文档 | 接口规范、文档驱动开发 |
| **多云存储适配** | 工厂模式，支持七牛/阿里/腾讯切换 | 设计模式（工厂模式、策略模式） |

---

## 四、可以借鉴的设计模式

### 1. 注解 + AOP 的横切关注点处理

```java
// 定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SysLog {
    String module() default "";  // 模块
    String func() default "";    // 功能
    String value() default "";   // 操作描述
}

// 使用注解
@SysLog(module = "登录登出", func = "登录", value = "系统登录")
@PostMapping("/sys/login")
public Result login(...) { ... }

// AOP 切面统一处理
@Aspect
@Component
public class SysLogAspect {
    @Around("@annotation(com.inteink.common.annotation.SysLog)")
    public Object around(ProceedingJoinPoint point) {
        // 记录开始时间、执行方法、保存日志...
    }
}
```

### 2. 工厂模式 - 多云存储适配

```java
// 云存储工厂
public class CloudStorageFactory {
    public static CloudStorageService build(CloudType type) {
        switch(type) {
            case QINIU: return new QiniuCloudStorageService();
            case ALIYUN: return new AliyunCloudStorageService();
            case QCLOUD: return new QcloudCloudStorageService();
        }
    }
}
```

### 3. 多数据源动态切换

```java
// 注解标记使用哪个数据源
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {
    String value() default "master";
}

// AOP 切面在方法执行前切换数据源
@Aspect
@Component
public class DataSourceAspect {
    @Before("@annotation(dataSource)")
    public void before(DataSource dataSource) {
        DynamicContextHolder.push(dataSource.value());
    }
    @After("@annotation(dataSource)")
    public void after() {
        DynamicContextHolder.poll();
    }
}

// 使用
@DataSource("slave")
public List<User> getUsers() { ... }
```

---

## 五、可复用能力与待补充能力

| 已有能力 | 待补充能力 | 对应维度 |
|---------|-----------|---------|
| AOP 日志 | - | 可观测性 |
| 多数据源切换 | - | 查询效率（读写分离） |
| Shiro + JWT | 升级为 Spring Security + JWT | 安全鉴权 |
| MongoDB 存日志 | - | 存储选型 |
| 统一异常处理 | - | 可扩展性 |
| - | 限流、熔断 | 系统可用性 |
| - | 分布式锁、Redis 缓存 | 并发处理 |
| - | 消息队列 | 消息异步 |


