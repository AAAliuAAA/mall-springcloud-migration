# Sleuth + Zipkin 链路追踪改造大纲

> 目标：为 mall 微服务体系引入链路追踪，实现请求全链路可视化，结合已有 ELK 体系形成完整可观测性闭环。

---

## 一、改造目标

```
改造前：
    排查问题需要分别打开 Gateway、Portal、Admin 三个服务日志
    靠时间戳手动对齐，无法直观看到各服务耗时分布

改造后：
    每个请求有唯一 TraceId，贯穿所有服务
    Zipkin 界面一张瀑布图看完整条调用链
    日志里自动带 TraceId，Kibana 可直接过滤
```

---

## 二、技术栈说明

| 组件 | 职责 |
|------|------|
| Spring Cloud Sleuth | 自动埋点，生成 TraceId/SpanId，透传到下游服务，注入日志 MDC |
| Zipkin Server | 独立部署，接收各服务上报的 Span 数据，提供查询 UI |
| ELK（已有） | 收集完整日志，配合 TraceId 做深入分析 |

**Sleuth 和 Zipkin 的分工：**

```
Sleuth（埋点，在业务服务里）
    生成 TraceId + SpanId
    服务间调用时自动透传（HTTP Header、Feign 都支持）
    把 TraceId 注入日志 MDC，每行日志自动带追踪信息
        ↓ 上报 Span 数据
Zipkin Server（独立服务）
    接收并存储 Span 数据
    提供 Web UI，支持按 TraceId 搜索调用链瀑布图
```

---

## 三、改造步骤

### Step 1：部署 Zipkin Server

本地直接用 jar 包启动，无需额外配置：

```bash
# 下载 Zipkin Server
curl -sSL https://zipkin.io/quickstart.sh | bash -s

# 启动（默认端口 9411）
java -jar zipkin.jar
```

或者等后续 Docker Compose 统一部署阶段再容器化，目前本地 jar 启动即可。

访问 `http://localhost:9411` 确认 Zipkin UI 正常。

**数据存储说明：**
默认数据存在内存里，重启丢失，开发环境够用。生产环境需要配置持久化存储：

```bash
# 使用 MySQL 持久化
STORAGE_TYPE=mysql \
MYSQL_HOST=localhost \
MYSQL_TCP_PORT=3306 \
MYSQL_DB=zipkin \
MYSQL_USER=root \
MYSQL_PASS=root \
java -jar zipkin.jar
```

---

### Step 2：各服务引入 Sleuth + Zipkin 依赖

**涉及模块**：mall-gateway、mall-portal、mall-admin、mall-search

```xml
<!-- Spring Cloud Sleuth（自动埋点） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>

<!-- Sleuth 与 Zipkin 集成（上报数据到 Zipkin Server） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

**注意**：不需要写版本号，版本由父 pom 的 Spring Cloud BOM 统一管理。

---

### Step 3：各服务配置 Zipkin 上报地址

在 Nacos 的各服务配置文件（或 mall-common-dev.yaml）里添加：

```yaml
spring:
  zipkin:
    base-url: http://localhost:9411    # Zipkin Server 地址
    sender:
      type: web                        # 通过 HTTP 上报（默认），也可用 kafka/rabbit
  sleuth:
    sampler:
      probability: 1.0                 # 采样率：1.0 = 100% 全采样（开发环境用）
                                       # 生产环境建议改为 0.1（采样 10%）
```

**采样率说明：**

```
probability: 1.0   开发/测试环境，全部采样，方便调试
probability: 0.1   生产环境，采样 10%，降低性能开销和存储压力
probability: 0.01  超高并发生产环境，采样 1%
```

---

### Step 4：确认日志格式包含 TraceId

Sleuth 引入后会自动修改日志格式，每行日志自动带上追踪信息：

```
# 引入 Sleuth 之前
INFO c.m.m.common.log.WebLogAspect : {"uri":"/brand/recommendList"...}

# 引入 Sleuth 之后
INFO [mall-portal,3d9f2a1b4c8e5f6a,7a2b3c4d] c.m.m.common.log.WebLogAspect : {"uri":"/brand/recommendList"...}
      ↑服务名          ↑TraceId（全局）  ↑SpanId（当前服务）
```

如果日志格式没有自动变化，检查 `logback-spring.xml` 里是否硬编码了 pattern，需要加上 Sleuth 的占位符：

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} %5p [${spring.application.name},%X{traceId},%X{spanId}] %m%n</pattern>
```

---

### Step 5：ELK 集成 TraceId（让 Kibana 支持按 TraceId 过滤）

Sleuth 把 TraceId 注入到日志后，需要让 Logstash 把 TraceId 解析成独立的字段，这样在 Kibana 里才能直接用 TraceId 过滤，而不是靠全文搜索。

在 Logstash 的 filter 配置里添加 grok 解析规则：

```
filter {
  grok {
    match => {
      "message" => "\[%{DATA:service},%{DATA:traceId},%{DATA:spanId}\]"
    }
  }
}
```

解析后 Kibana 里每条日志都有独立的 `traceId` 字段，直接过滤即可。

---

### Step 6：全面验证

**验证一：日志格式**

启动任意服务，发一个请求，查看控制台日志是否带上了 TraceId：

```
INFO [mall-portal,3d9f2a1b4c8e5f6a,7a2b3c4d] ...
```

**验证二：Zipkin 调用链**

```
访问 http://localhost:9411
    ↓
发起一个跨服务请求（Gateway → Portal → Admin）
    ↓
在 Zipkin UI 搜索 mall-portal 服务
    ↓
找到对应的 Trace，点开查看瀑布图
    → 应该看到 Gateway、Portal、Admin 三段 Span
    → 每段显示服务名和耗时
```

**验证三：TraceId 跨服务一致**

```
在 Zipkin 找到一条 Trace，记录 TraceId
    ↓
去 Kibana 搜索这个 TraceId
    ↓
应该能找到 Gateway、Portal、Admin 三个服务各自的日志
    → 三条日志的 TraceId 完全相同
```

---

## 四、改造后的排查流程

```
用户反馈某个请求响应慢或报错

第一步：Zipkin（快速定位）
    按时间范围搜索，找到对应的 Trace
    瀑布图一眼看出哪个 Span 耗时异常
    确定问题在哪个服务

第二步：Kibana（深入分析）
    拿到 TraceId，在 Kibana 搜索
    查看该请求在问题服务里的完整日志
    分析具体报错堆栈和业务上下文

第三步：定位根因
    结合 Zipkin 的耗时 + Kibana 的日志内容
    快速得出结论
```

---

## 五、与已有体系的关系

```
可观测性三大支柱：

Logging（日志）
    → ELK 体系（已有）
    → 记录详细的业务日志和异常堆栈
    → Sleuth 改造后每行日志自动带 TraceId

Tracing（链路追踪）
    → Sleuth + Zipkin（本次改造）
    → 记录请求的完整调用链和各段耗时
    → 通过 TraceId 与 ELK 日志关联

Metrics（指标监控）
    → Prometheus + Grafana（后续可接入）
    → 记录系统整体健康状况（QPS、错误率、CPU等）
    → 与 Sentinel 的监控数据互补
```

三者互补，解决不同层面的可观测性问题，生产环境都需要。

---

## 六、关键注意事项

| 注意点 | 说明 |
|-------|------|
| 生产环境采样率不能设 1.0 | 全采样会带来明显性能开销，建议 0.1 |
| Zipkin 默认存内存 | 生产环境必须配置 MySQL 或 ES 持久化 |
| Gateway 也需要引入依赖 | 追踪必须从入口开始，否则链路不完整 |
| Feign 调用自动透传 TraceId | Sleuth 自动处理，无需手动传递 |
| logback-spring.xml 检查 | 如果有自定义 pattern 需要手动加 TraceId 占位符 |
| Spring Cloud 2022+ 已迁移到 Micrometer Tracing | 你的 2021.0.5 版本用 Sleuth，不受影响 |