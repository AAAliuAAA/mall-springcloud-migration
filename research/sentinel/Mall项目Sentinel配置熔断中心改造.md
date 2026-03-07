# Sentinel 熔断限流改造大纲

> 目标：为 mall 微服务体系引入 Sentinel，解决服务雪崩问题，实现熔断降级、流量控制与系统保护。

---

## 一、为什么需要 Sentinel

### 没有 Sentinel 时的问题

你在改造过程中已经遇到了典型场景：

```
mall-admin 未启动
    ↓
mall-portal 通过 OpenFeign 调用 mall-admin
    ↓
LoadBalancer 找不到实例 → 等待超时
    ↓
请求线程被占用，无法释放
    ↓
并发请求堆积 → portal 线程池耗尽 → portal 也崩了
    ↓
gateway 调 portal 也开始超时 → 整个系统雪崩
```

这就是微服务架构中著名的**雪崩效应**，一个服务不可用，沿着调用链扩散，最终压垮整个系统。

### Sentinel 解决的核心问题

| 问题 | Sentinel 的解法 |
|------|---------------|
| 下游服务不可用，调用方无限等待 | 熔断：检测到失败率过高，直接快速失败 |
| 突发流量压垮服务 | 限流：超过阈值的请求直接拒绝 |
| 熔断后用户看到 500 | 降级：返回兜底数据，用户体验友好 |
| 系统负载过高 | 系统保护：CPU/内存超阈值时自动限流 |

---

## 二、核心概念

### 资源（Resource）

Sentinel 保护的对象，可以是一个接口、一个方法、一段代码。每个资源有唯一名称，规则都是针对资源名配置的。

```java
// 接口路径自动成为资源名
// /brand/recommendList 就是一个资源
```

### 规则类型

**流量控制规则（Flow Rule）**：限制资源的访问频率

```
资源：/portal/brand/recommendList
阈值类型：QPS（每秒请求数）
阈值：100
超出后行为：直接拒绝 / 排队等待
```

**熔断规则（Circuit Breaker Rule）**：检测异常自动熔断

```
资源：AdminBrandFeign#recommendList
熔断策略：异常比例超过 50%
熔断时长：10 秒
恢复后先放一个请求探测，成功则关闭熔断
```

**降级（Fallback）**：熔断触发后执行的兜底逻辑

```java
// 正常：调用 mall-admin 返回品牌列表
// 熔断：返回空列表，前端显示"暂无推荐品牌"
```

### 熔断器的三种状态

```
正常状态（Closed）
    → 失败率超过阈值
        ↓
熔断状态（Open）← 所有请求直接走 Fallback，不调用下游
    → 熔断时长结束
        ↓
半开状态（Half-Open）← 放一个探测请求
    → 成功 → 回到 Closed
    → 失败 → 继续 Open
```

---

## 三、技术选型

### Sentinel vs Hystrix

mall 原项目使用的是 Netflix Hystrix，但 Hystrix 已于 2018 年停止维护。Spring Cloud Alibaba 推荐使用 Sentinel 替代，两者对比：

| 维度 | Hystrix | Sentinel |
|------|---------|---------|
| 维护状态 | 已停止维护 | 活跃维护（阿里巴巴） |
| 控制台 | 无官方控制台 | 有实时监控控制台 |
| 规则配置 | 代码配置 | 控制台动态配置 |
| 限流支持 | 不支持 | 支持 |
| 接入复杂度 | 高 | 低 |
| 与 Spring Cloud 集成 | 官方支持 | Spring Cloud Alibaba 支持 |

---

## 四、改造步骤

### Step 1：部署 Sentinel 控制台

Sentinel 控制台是一个独立的 Spring Boot 应用，提供实时监控和动态规则配置。

```bash
# 下载 sentinel-dashboard jar 包
# 版本需要和 spring-cloud-alibaba 版本对应
# 2021.0.5.0 对应 Sentinel 1.8.6

java -Dserver.port=8858 \
     -Dcsp.sentinel.dashboard.server=localhost:8858 \
     -Dproject.name=sentinel-dashboard \
     -jar sentinel-dashboard-1.8.6.jar
```

访问 `http://localhost:8858`，默认用户名密码：sentinel / sentinel

### Step 2：各服务引入 Sentinel 依赖

**涉及模块**：mall-gateway、mall-portal、mall-admin、mall-search

```xml
<!-- Sentinel 核心 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>

<!-- OpenFeign 整合 Sentinel（mall-portal 需要） -->
<!-- 已包含在 spring-cloud-starter-alibaba-sentinel 中 -->
```

### Step 3：各服务连接 Sentinel 控制台

在各服务的 `application.yml`（或迁移到 Nacos Config）添加：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8858   # Sentinel 控制台地址
        port: 8719                  # 本地与控制台通信端口，每个服务不同
      eager: true                   # 服务启动时主动连接控制台，不等第一次请求
```

**注意**：多个服务的 `transport.port` 不能相同：
- mall-admin：8719
- mall-portal：8720
- mall-gateway：8721
- mall-search：8722

### Step 4：OpenFeign 开启 Sentinel 支持

在 mall-portal 的 `application.yml` 添加：

```yaml
feign:
  sentinel:
    enabled: true   # 开启 Feign 的 Sentinel 整合
```

### Step 5：为 OpenFeign 接口编写 Fallback

这是核心改造，解决 Admin 不可用时 Portal 返回 500 的问题。

**方式一：FallbackFactory（推荐，可以拿到异常信息）**

```java
// mall-portal/src/main/java/com/macro/mall/portal/feign/AdminBrandFeignFallbackFactory.java
@Component
public class AdminBrandFeignFallbackFactory implements FallbackFactory<AdminBrandFeign> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBrandFeignFallbackFactory.class);

    @Override
    public AdminBrandFeign create(Throwable cause) {
        return new AdminBrandFeign() {
            @Override
            public CommonResult<List<PmsBrand>> recommendList(Integer pageNum, Integer pageSize) {
                LOGGER.error("调用 mall-admin recommendList 失败，执行降级，原因：{}", cause.getMessage());
                // 返回空列表，前端正常渲染，不影响页面其他内容
                return CommonResult.success(Collections.emptyList());
            }
        };
    }
}
```

**在 FeignClient 上指定 FallbackFactory**

```java
@FeignClient(
    name = "mall-admin",
    fallbackFactory = AdminBrandFeignFallbackFactory.class  // 指定降级工厂
)
public interface AdminBrandFeign {
    @GetMapping("/brand/recommendList")
    CommonResult<List<PmsBrand>> recommendList(
        @RequestParam Integer pageNum,
        @RequestParam Integer pageSize
    );
}
```

### Step 6：Gateway 整合 Sentinel

Gateway 有专门的 Sentinel 适配模块，支持对路由和 API 分组进行限流：

```xml
<!-- mall-gateway pom.xml -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
```

Gateway 限流规则配置（在 Sentinel 控制台配置，或代码初始化）：

```java
@Configuration
public class GatewaySentinelConfig {

    @PostConstruct
    public void initRules() {
        // 针对路由 mall-portal 限流：每秒最多 200 个请求
        GatewayFlowRule portalRule = new GatewayFlowRule("mall-portal")
                .setCount(200)
                .setIntervalSec(1);

        // 针对路由 mall-admin 限流：每秒最多 100 个请求
        GatewayFlowRule adminRule = new GatewayFlowRule("mall-admin")
                .setCount(100)
                .setIntervalSec(1);

        GatewayRuleManager.loadRules(Sets.newHashSet(portalRule, adminRule));
    }
}
```

### Step 7：Sentinel 规则持久化到 Nacos

Sentinel 默认把规则存在内存里，服务重启后规则丢失。需要把规则持久化到 Nacos，重启后自动加载：

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

在 `application.yml` 配置数据源：

```yaml
spring:
  cloud:
    sentinel:
      datasource:
        flow-rules:                          # 流控规则
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: dev
            data-id: mall-admin-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade-rules:                       # 熔断规则
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: dev
            data-id: mall-admin-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade
```

在 Nacos 控制台创建对应的配置文件（JSON 格式）：

```json
// mall-admin-flow-rules（流控规则示例）
[
  {
    "resource": "/brand/recommendList",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

---

## 五、验证步骤

### 5.1 验证 Sentinel 控制台连接

启动各服务后，访问 `http://localhost:8858`，左侧菜单应出现：
- mall-admin
- mall-portal
- mall-gateway

**注意**：Sentinel 是懒加载的，服务启动后需要至少发起一次请求，才会在控制台出现。如果配置了 `eager: true` 则启动即注册。

### 5.2 验证 Fallback 降级

```bash
# 停止 mall-admin
# 访问 portal 的 recommendList 接口
curl http://localhost:8088/portal/brand/recommendList?pageNum=1&pageSize=6

# 预期结果：返回 200 + 空列表，而不是 500
# {"code":200,"message":"操作成功","data":[]}
```

### 5.3 验证流控规则

在 Sentinel 控制台对 `/brand/recommendList` 配置 QPS=1 的限流规则，然后快速连续发两次请求，第二次应返回：

```json
{"code":429,"message":"Blocked by Sentinel (flow limiting)"}
```

### 5.4 验证规则持久化

配置流控规则后重启服务，规则应自动从 Nacos 加载，无需重新配置。

---

## 六、关键注意事项

| 坑点 | 说明 |
|------|------|
| Sentinel 控制台版本和依赖版本必须一致 | 2021.0.5.0 对应 Sentinel 1.8.6 |
| 多服务 transport.port 不能重复 | 每个服务配不同的端口 |
| Feign Fallback 的 Bean 必须加 @Component | 否则 Spring 无法注入 |
| FallbackFactory 优于 Fallback | FallbackFactory 可以拿到异常信息，便于日志记录 |
| Gateway 使用专属的 sentinel-gateway 模块 | 不能用普通的 sentinel starter |
| 规则不持久化则重启丢失 | 生产环境必须接入 Nacos 持久化 |
| @SentinelResource 注解是可选的 | OpenFeign 整合后不需要手动加，自动保护 Feign 调用 |

---

## 七、改造后的架构

```
外部请求
    ↓
mall-gateway（Sentinel Gateway 限流）
    → 超过阈值：返回 429 Too Many Requests
    → 正常：转发到业务服务
        ↓
mall-portal（Sentinel + OpenFeign）
    → 调用 mall-admin
        → 正常：返回品牌列表
        → mall-admin 不可用/超时：
            → 熔断器打开
            → 执行 Fallback：返回空列表
            → 用户看到正常页面（无推荐品牌）而不是 500
```

---

## 八、与已完成改造的关联

| 已完成模块 | Sentinel 的增强 |
|-----------|---------------|
| Gateway 路由 | 在路由层加限流，保护下游服务 |
| OpenFeign 调用 | Feign 调用自动受 Sentinel 保护，配合 Fallback 降级 |
| Nacos Config | Sentinel 规则持久化到 Nacos，动态修改规则无需重启 |