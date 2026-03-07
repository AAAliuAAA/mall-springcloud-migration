# Nacos Config 配置中心改造大纲

## 改造目标

将各服务中分散的配置文件集中迁移到 Nacos Config 统一管理，实现：
- 公共配置（JWT secret、Redis、数据库）一处修改，全部服务生效
- 敏感配置不再硬编码在代码仓库里
- Gateway 白名单支持动态刷新，无需重启服务

---

## 核心概念（改造前必读）

### Nacos Config 的三层结构
```
Namespace（命名空间）
    └── Group（分组）
            └── DataId（配置文件）
```
- **Namespace**：区分环境，如 dev / test / prod，完全隔离
- **Group**：区分业务线，默认 `DEFAULT_GROUP`
- **DataId**：对应一个配置文件，命名规则为 `${spring.application.name}-${profile}.yaml`

### bootstrap.yml 的作用
Nacos Config 必须使用 `bootstrap.yml` 而非 `application.yml` 来配置 Nacos 地址，原因是：
- Spring Boot 启动时 `bootstrap.yml` 先于 `application.yml` 加载
- 服务需要先连上 Nacos、拉取远程配置，再用这些配置启动应用
- 如果写在 `application.yml` 里，Nacos 地址还没读到，应用已经开始初始化了

### 配置优先级（从高到低）
```
Nacos 远程配置 > bootstrap.yml 本地配置 > application.yml 本地配置
```

---

## Step 1：添加 Nacos Config 依赖

**涉及模块**：mall-admin、mall-portal、mall-search、mall-gateway

每个模块的 `pom.xml` 添加：
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

同时添加 bootstrap 支持（Spring Boot 2.4+ 默认禁用了 bootstrap）：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

---

## Step 2：各服务新建 bootstrap.yml

**涉及模块**：mall-admin、mall-portal、mall-search、mall-gateway

每个服务新建 `src/main/resources/bootstrap.yml`，内容示例（以 mall-admin 为例）：

```yaml
spring:
  application:
    name: mall-admin
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        file-extension: yaml
        namespace: dev          # 对应 Nacos 里创建的命名空间 ID
        group: DEFAULT_GROUP
  profiles:
    active: dev
```

DataId 规则：`mall-admin-dev.yaml`（Spring 自动拼接，无需手动指定）

---

## Step 3：在 Nacos 控制台创建配置文件

**登录 Nacos 控制台** → `http://localhost:8848/nacos`

### 3.1 创建命名空间
配置管理 → 命名空间 → 新建命名空间：
- 命名空间名称：`dev`
- 记录生成的命名空间 ID，填入各服务 `bootstrap.yml` 的 `namespace` 字段

### 3.2 创建公共配置（shared config）
DataId：`mall-common-dev.yaml`，Group：`DEFAULT_GROUP`

内容（所有服务共享的配置）：
```yaml
# JWT 配置（所有服务共用同一个 secret）
jwt:
  secret: mall-admin-secret
  expiration: 604800

# Redis 配置
spring:
  redis:
    host: localhost
    port: 6379
    password:
```

### 3.3 创建各服务独立配置

**mall-admin-dev.yaml**
```yaml
# 数据库配置（admin 独有）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mall?...
    username: root
    password: root

# admin 自己的白名单
secure:
  ignored:
    urls:
      - /admin/login
      - /brand/recommendList
```

**mall-gateway-dev.yaml**
```yaml
# Gateway 路由白名单（支持动态刷新）
secure:
  ignored:
    urls:
      - /admin/admin/login
      - /portal/sso/login
      - /portal/brand/recommendList
      - /admin/swagger-ui/**
      - /*/actuator/**
```

**mall-portal-dev.yaml** 和 **mall-search-dev.yaml** 按同样方式创建。

---

## Step 4：配置共享（shared-configs）

各服务引入公共配置，避免 JWT secret 等重复配置，在 `bootstrap.yml` 里添加：

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        file-extension: yaml
        namespace: dev
        # 引入公共配置
        shared-configs:
          - data-id: mall-common-dev.yaml
            group: DEFAULT_GROUP
            refresh: true    # 支持动态刷新
```

---

## Step 5：清理本地 application.yml

把已经迁移到 Nacos 的配置从本地 `application.yml` 中**删除**，只保留：
- 服务端口 `server.port`
- Nacos 注册中心地址（注册中心不走 Nacos Config）
- 本地开发调试需要的临时配置

**注意**：这一步容易漏删，建议逐个配置项核对。

---

## Step 6：Gateway 白名单动态刷新

这是 Nacos Config 最实用的功能之一，白名单改了不需要重启 Gateway。

在 `IgnoreUrlsConfig.java` 加上 `@RefreshScope`：

```java
@Data
@Component
@RefreshScope                                    // 加这个注解
@ConfigurationProperties(prefix = "secure.ignored")
public class IgnoreUrlsConfig {
    private List<String> urls;
}
```

验证方式：
1. 启动 Gateway
2. 在 Nacos 控制台修改 `mall-gateway-dev.yaml` 的白名单，新增一个路径
3. 不重启 Gateway，直接访问新增的路径，确认能正常放行

---

## Step 7：验证

### 7.1 验证配置加载
启动各服务，观察日志是否出现：
```
Located property source: [BootstrapPropertySource (nacos:mall-admin-dev.yaml)]
```
说明 Nacos Config 配置加载成功。

### 7.2 验证公共配置共享
修改 Nacos 里 `mall-common-dev.yaml` 的 JWT secret，重启服务，确认新 secret 生效。

### 7.3 验证动态刷新
在 Nacos 控制台修改 Gateway 白名单配置，不重启服务，确认新白名单立即生效。

---

## 关键注意事项

| 坑点 | 说明 |
|------|------|
| bootstrap.yml 里的 namespace 填的是 ID 不是名称 | Nacos 创建命名空间后会生成一个 UUID 格式的 ID，填这个 |
| DataId 大小写敏感 | `mall-admin-dev.yaml` 和 `Mall-Admin-dev.yaml` 是两个不同的配置 |
| 本地配置未删干净 | Nacos 配置和本地配置同时存在时，Nacos 优先级更高，但容易造成混乱 |
| @RefreshScope 和 @Configuration 不能同时用 | 动态刷新的配置类用 @Component + @ConfigurationProperties，不用 @Configuration |
| mall-gateway 引入 mall-common 的 MVC 冲突 | 之前已处理，确认 `spring.main.web-application-type: reactive` 还在 |

---

## 改造完成后的配置架构

```
Nacos Config（配置中心）
    ├── mall-common-dev.yaml     ← JWT secret、Redis（所有服务共享）
    ├── mall-admin-dev.yaml      ← 数据库、admin 白名单
    ├── mall-portal-dev.yaml     ← 数据库、portal 配置
    ├── mall-search-dev.yaml     ← ES 地址、search 配置
    └── mall-gateway-dev.yaml    ← 路由白名单（支持动态刷新）

各服务本地只保留
    └── bootstrap.yml            ← Nacos 地址、命名空间（启动引导配置）
    └── application.yml          ← 端口等极少量本地配置
```