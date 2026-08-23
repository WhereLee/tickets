# 压测工具说明

本目录包含抢购系统的压测工具集。

## 文件清单

| 文件 | 用途 | 使用频率 |
|------|------|---------|
| `run_scale.ps1` | **一键压测**：自动重置库存 → 生成脚本 → 跑压测 → 输出分析 | 每次压测 |
| `scale_template.jmx` | JMeter 压测模板（run_scale.ps1 自动调用，无需手动编辑） | 自动 |
| `prepare_db.bat` | 一次性初始化：清空旧数据 + 批量造 3000 个测试用户 | 首次/重置 |
| `check_db.bat` | 压测后核对：订单数 / 库存数 / 抢购记录数（验证是否超卖） | 每次压测后 |

## 快速上手

```powershell
# 1.（首次）初始化测试数据：造 3000 用户，活动库存 5000
prepare_db.bat

# 2.（需要时）启动应用
# IDEA 运行 GrabApplication，或 java -jar target/grab-system-1.0.0.jar

# 3. 一键压测（自动重置库存 + 跑 + 分析）
powershell -ExecutionPolicy Bypass -File run_scale.ps1 -Concurrency 500

# 4. 核对数据库（超卖检查）
check_db.bat
```

## run_scale.ps1 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `-Concurrency` | 100 | 并发线程数 |
| `-UserPool` | 3000 | 随机用户池上限（需 ≥ 并发数） |
| `-ActivityId` | 1 | 压测的活动 ID |
| `-Stock` | 5000 | 压测前重置的库存量（需 > 并发数） |

## 输出格式

```
[1/3] stock reset to 5000 for activity 1
[2/3] jmx generated for 500 threads
[3/3] RESULT|500|500|203|Non HTTP...|1301|1296|2109|2277
```

RESULT 字段依次为：并发数 | 总请求 | HTTP错误数 | 错误类型 | 平均响应 | P50 | P90 | P99 | 最大

> ⚠️ 注意：JMeter 的 HTTP 成功 ≠ 业务成功（业务拒绝也返回 HTTP 200），
> 真实业务成功数必须以 `check_db.bat` 的订单数为准。
