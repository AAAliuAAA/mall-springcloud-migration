## 实施步骤

### Step 1：mall-gateway 添加 jjwt 依赖

修改 `mall-gateway/pom.xml`，添加：

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
</dependency>
```

版本由父 pom 的 `dependencyManagement` 统一管理。

---

### Step 2：添加 JWT 配置项

修改 `mall-gateway/src/main/resources/application.yml`，添加：

```yaml
jwt:
  secret: mall-secret          # 需与各业务服务的 jwt.secret 保持一致
  tokenHead: "Bearer "
```

具体值参考 `mall-admin/src/main/resources/application.yml` 中的 jwt 配置。

---

### Step 3：编写 Gateway 专用 JWT 工具类

创建 `mall-gateway/src/main/java/com/macro/mall/gateway/util/GatewayJwtTokenUtil.java`：

- `getUserNameFromToken(String token): String` — 从 token 解析用户名
- `isTokenExpired(String token): boolean` — 判断 token 是否过期

逻辑从 `mall-security` 的 `JwtTokenUtil` 中提取，仅保留解析相关方法。

---

### Step 4：配置白名单

**4.1** 创建 `mall-gateway/src/main/java/com/macro/mall/gateway/config/IgnoreUrlsConfig.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "secure.ignored")
public class IgnoreUrlsConfig {
    private List<String> urls = new ArrayList<>();
}
```

**4.2** 在 `application.yml` 中添加：

```yaml
secure:
  ignored:
    urls:
      - /admin/admin/login
      - /portal/sso/login
      - /portal/sso/register
      - /admin/swagger-ui/**
      - /admin/swagger-resources/**
      - /admin/v2/api-docs
      - /portal/swagger-ui/**
      - /portal/swagger-resources/**
      - /portal/v2/api-docs
      - /search/swagger-ui/**
      - /*/actuator/**
```

白名单路径为 Gateway 接收到的**原始路径**（含 `/admin/` 等前缀，StripPrefix 在路由转发时才生效）。

---

### Step 5：编写全局鉴权过滤器

创建 `mall-gateway/src/main/java/com/macro/mall/gateway/filter/AuthGlobalFilter.java`

实现 `GlobalFilter` 接口（响应式，返回 `Mono<Void>`），逻辑：

1. 路径匹配白名单 → 放行
2. 从 `Authorization` 请求头取 Token
3. 用 `GatewayJwtTokenUtil` 验证 Token
4. 验证通过 → 将用户名写入 `X-User-Name` 请求头，转发
5. 验证失败 → 返回 401 JSON 响应

---

### Step 6：验证（中间检查点）

启动依赖：Nacos、MySQL、Redis  
启动服务：mall-gateway、mall-admin（通过 IDEA）

验证项：

```bash
# 白名单路径，不需要 Token
curl http://localhost:8088/admin/admin/login

# 无 Token，返回 401
curl http://localhost:8088/admin/brand/list

# 带 Token，正常返回
curl -X POST http://localhost:8088/admin/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"macro123"}'
# 用返回的 token 访问
curl http://localhost:8088/admin/brand/list \
  -H "Authorization: Bearer {token}"
```

---

### Step 7 ~ 9（后续，Step 6 验证通过后再执行）

- Step 7：各业务服务 SecurityConfig 改造（按 SecurityFilterChain Bean 模式）
- Step 8：mall-common 添加 AuthConstant，定义内部 Token
- Step 9：OpenFeign 拦截器透传内部 Token

---

## 改动文件清单（Step 1 ~ 5）

| 文件 | 类型 | 说明 |
|------|------|------|
| `mall-gateway/pom.xml` | 修改 | 添加 jjwt 依赖 |
| `mall-gateway/src/main/resources/application.yml` | 修改 | 添加 jwt 配置和白名单 |
| `.../gateway/util/GatewayJwtTokenUtil.java` | 新增 | 轻量 JWT 工具类 |
| `.../gateway/config/IgnoreUrlsConfig.java` | 新增 | 白名单配置类 |
| `.../gateway/filter/AuthGlobalFilter.java` | 新增 | 全局鉴权过滤器 |
