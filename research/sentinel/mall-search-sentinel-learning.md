# mall-search Sentinel 接入改造记录

> mall-search 只做入口限流保护，不需要 Fallback 和熔断规则，因为它不通过 Feign 调用其他服务，只查询 Elasticsearch。

---

## 改动清单

| 改动位置 | 改动内容 |
|---------|---------|
| mall-search pom.xml | 引入 sentinel 依赖 |
| Nacos mall-search-dev.yaml | 添加 sentinel 连接配置和 datasource |
| Nacos SENTINEL_GROUP | 新建 mall-search-flow-rules 流控规则文件 |

---

## 一、pom.xml 引入依赖

文件路径：`mall-search/pom.xml`

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
```

---

## 二、Nacos 配置修改

文件：`mall-search-dev.yaml`（dev 命名空间，DEFAULT_GROUP）

新增以下内容：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8858
        port: 8722        # 各服务端口不能重复，search 用 8722
      eager: true         # 启动时主动连接控制台，不等第一次请求
      datasource:
        search-flow-rules:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: dev
            data-id: mall-search-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
```

---

## 三、Nacos 创建流控规则文件

| 配置项 | 值 |
|-------|---|
| Data ID | mall-search-flow-rules |
| Group | SENTINEL_GROUP |
| Namespace | dev |
| Format | JSON |

内容：

```json
[
  {
    "resource": "/search/product/search",
    "limitApp": "default",
    "grade": 1,
    "count": 200,
    "intervalSec": 1,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

字段说明：

| 字段 | 值 | 含义 |
|------|---|------|
| resource | /search/product/search | 受保护的搜索接口路径 |
| grade | 1 | 按 QPS 限流 |
| count | 200 | 每秒最多 200 个请求 |
| strategy | 0 | 直接限流 |
| controlBehavior | 0 | 超出后直接拒绝 |
| clusterMode | false | 单机限流 |

`count` 的值需要根据压测结果调整，200 是初始保守值。

---

## 四、各服务 Sentinel transport.port 汇总

多个服务同时接入 Sentinel 时，`transport.port` 必须不同，否则端口冲突：

| 服务 | transport.port |
|------|--------------|
| mall-admin | 8719 |
| mall-portal | 8720 |
| mall-gateway | 8721 |
| mall-search | 8722 |

---

## 五、验证步骤

**第一步**：重启 mall-search

**第二步**：在 Sentinel 控制台确认
```
左侧服务列表出现 mall-search
    ↓
点击 mall-search → 流控规则
    → 能看到 /search/product/search 的规则已加载
```

**第三步**：发一个搜索请求触发资源注册
```
GET http://localhost:8088/search/product/search?keyword=手机&pageNum=1&pageSize=10
```

**第四步**：在簇点链路确认资源出现
```
mall-search → 簇点链路
    → /search/product/search 出现在列表中
```

---

## 六、为什么 mall-search 不需要熔断规则和 Fallback

```
mall-portal（需要熔断 + Fallback）
    原因：通过 Feign 调用 mall-admin
    admin 不可用时，portal 线程会积压等待超时
    需要熔断快速失败，需要 Fallback 兜底

mall-search（只需要流控）
    原因：只查询 Elasticsearch，不调用其他微服务
    没有服务间调用，不存在下游不可用导致线程积压的问题
    只需要流控保护自身入口，防止被大流量压垮
```

如果后续 mall-search 需要调用其他服务（如调用 mall-admin 获取品类信息），再补充对应的熔断规则和 FallbackFactory。
