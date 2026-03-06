# Gateway统一鉴权改造与学习笔记

## 1. 改动目标
完成统一鉴权架构下的微服务鉴权过滤器改造。在原架构中，前端请求直接到达微服务，各微服务的 `JwtAuthenticationTokenFilter` 负责解析请求头中的 JWT 并且验签。在新的统一鉴权架构下，由 Gateway 统一解析并验签 JWT，随后将解析出的合法用户名通过 HTTP Header `X-User-Name` 传递给下游微服务。因此，微服务的 `JwtAuthenticationTokenFilter` 不再需要执行完整的 JWT 解析和验签逻辑，只需要信任网关处理结果，直接从请求头中读取 `X-User-Name` 以完成 Spring Security 的上下文填充。

## 2. 具体改动文件与详情

### 2.1 `mall-security/src/main/java/com/macro/mall/security/component/JwtAuthenticationTokenFilter.java`
去除了对 `JwtTokenUtil` 的强耦合依赖，主要逻辑切换为从 `HttpServletRequest` 读取 `X-User-Name`。

**改动细节**：
1. **精简依赖**：删除了未在当前类中发挥新作用的 `JwtTokenUtil` 组件的属性注入，删除了配置项中关于 JWT 前缀和 Header 名的 `@Value` 参数绑定。
   - 移除 `@Autowired private JwtTokenUtil jwtTokenUtil;`
   - 移除 `@Value("${jwt.tokenHeader}") private String tokenHeader;`
   - 移除 `@Value("${jwt.tokenHead}") private String tokenHead;`

2. **重构过滤核心逻辑 (`doFilterInternal` 方法)**：
   - **旧逻辑**：取得名为 `Authorization` 的请求头内容，截取掉 `Bearer ` 前缀，通过 `jwtTokenUtil.getUserNameFromToken` 提取出当前载荷中的用户名，并在后续进行有效期及签名的再次校验 (`jwtTokenUtil.validateToken`)。
   - **新逻辑**：调用 `request.getHeader("X-User-Name")` 提取合法用户名。如果用户名不为空且线程本地 `SecurityContext` 中尚未完成登录认证，则触发 `UserDetailsService.loadUserByUsername(username)` 取回全部权限细节对象 (`UserDetails`)，最后构造内置的 `UsernamePasswordAuthenticationToken` 直接填充至上下文中，放行至后续过滤链。

### 2.2 SecurityConfig 现状确认
此前的改造指引中曾提及需要执行 “各业务服务 SecurityConfig 改造（按 SecurityFilterChain Bean 模式）”。经过对代码库的详尽调查：
1. `mall-security/src/main/java/com/macro/mall/security/config/SecurityConfig.java` 早前已平滑升级为 `@Bean SecurityFilterChain filterChain(HttpSecurity httpSecurity)` 的无状态声明方式，早已废弃 `WebSecurityConfigurerAdapter`；因此配置端不需要针对 Bean 模式做二次翻新。
2. 我们修改完成的 `JwtAuthenticationTokenFilter` 依然平级并作为 `@Autowired` 通过原有代码加载进了基于该 FilterChain 过滤树当中的 `UsernamePasswordAuthenticationFilter` 之前。由此直接惠及所有引入 `mall-security` 的微服务，如 `mall-admin` 与 `mall-portal` 等。无需干预业务服务的安全环境基础配置。

## 3. 补充安全防范：网关 Header 伪造防御

### 3.1 `mall-gateway/src/main/java/com/macro/mall/gateway/filter/AuthGlobalFilter.java`
为了防止恶意的攻击者在跨过网关时，直接在原生的 HTTP 请求头里携带伪造的 `X-User-Name` 以骗过下游微服务的鉴权信任，我们为全局过滤器补充了清理污染 Header 的防御机制。

**改动细节**：
- **白名单路径分支**：在遇到无需鉴权的白名单路径放行之前，显式通过 `exchange.getRequest().mutate().headers(headers -> headers.remove(AuthConstant.USER_TOKEN_HEADER)).build()` 来清空原始请求带来的同名 Header。这确保了下游即便是开放接口，也不会误读到外来注入的虚假身份。*（注：早期版本可能使用 `.header("X-User-Name", "")` 将此处置空，这会导致微服务的 SpringSecurity 拦截器误读到 "" 空字符串并以之查询数据库引发 `UsernameNotFoundException`，正确的做法是在 header 层将其 remove 彻底根绝该参数。）*
- **正常鉴权分支**：当前代码机制在第 5 步向 `mutate` 生成的 newRequest 中通过 `.header(AuthConstant.USER_TOKEN_HEADER, username)` 插入解析后的合法身份。Spring Cloud Gateway 的 `ServerHttpRequest.Builder#header()` 方法在遇到现存同名 Header 时会自动实施覆盖（或将其值置换为你指定的值），因此实际上此处自带了覆盖恶意传值的效果。不需要额外删除。

### 3.2 业务微服务过滤器健壮性加固
考虑到网关端可能漏删或者以后的升级意外带入空字符串的情况：在下游 `mall-security` 的 `JwtAuthenticationTokenFilter` 中，读取 `X-User-Name` 后进行数据库查询时的前提条件从单纯的 `username != null` 加固判定为了 `cn.hutool.core.util.StrUtil.isNotEmpty(username)`，借此将空指针和“空白身份”双双掐断。

## 4. Step 8: 统一定义与应用 AuthConstant 

为了摒除整个微服务生态中散发着对于网关传递 Header `"X-User-Name"` 的硬编码耦合，我们在公共模块 `mall-common` 建立了统一身份与权限相关常量类。

### 4.1 新增常量类 `mall-common/src/main/java/com/macro/mall/common/constant/AuthConstant.java`
定义了一系列包含后台服务所需的用户标识及权限相关常量，例如：
```java
public interface AuthConstant {
    // ...
    /**
     * 用户信息Http请求头
     */
    String USER_TOKEN_HEADER = "X-User-Name";
}
```
通过常量定义收拢，所有需向网关索取、甚至之后服务内部通过 OpenFeign 转发时需用到的该 Header 键名都统一引用此处的 `AuthConstant.USER_TOKEN_HEADER`。

### 4.2 AuthConstant 的依赖替换
- `mall-gateway/src/main/java/com/macro/mall/gateway/filter/AuthGlobalFilter.java` 中原本涉及手动置空白名单头部以及写回解析头部的 `"X-User-Name"` 改为调用 `AuthConstant.USER_TOKEN_HEADER`。
- `mall-security/src/main/java/com/macro/mall/security/component/JwtAuthenticationTokenFilter.java` 中接收网关传值的逻辑也已变更为 `request.getHeader(AuthConstant.USER_TOKEN_HEADER)` 获取用户名。
- **注意的采坑点：** `mall-common` 模块的 POM 当中包含了许多该项目所需的公共组件。这其中需要注意依赖引发的影响并在 `mall-gateway/pom.xml` 的 `<dependency>` 引入处做两点防坑处理：
  1. **Spring MVC 冲突报错修复：** `mall-common` 引入了阻塞式的 `<artifactId>spring-boot-starter-web</artifactId>`。由于网关建立在响应式 Netty 容器 (`spring-boot-starter-webflux`) 之上，若直接引入两者混在一个类加载器下会导致 MVC 控制反客为主，破坏网关必需的专属对象（触发 `ServerCodecConfigurer that could not be found`）。虽然可通过在 POM 给 `mall-common` 打上 exclusions 排除解决，但现更推荐的做法是在 `application.yml` 添加 `spring.main.web-application-type: reactive` 来直接强制定死引擎模式。
  2. **卸载网关用不上的 Redis 自动装配：** `mall-common` 顺带着将 `<artifactId>spring-boot-starter-data-redis</artifactId>` 传染进来了。这会导致 Spring 误以为需要用 Redis 自动开启后台维持与本机的 TCP/6379 连接，在控制台大量打印底层探测心跳流。为了精简直属职责，必须在引用 `<artifactId>mall-common</artifactId>` 时追加 `<exclusions>` 将其干净利落的屏蔽：
  ```xml
  <dependency>
      <groupId>com.macro.mall</groupId>
      <artifactId>mall-common</artifactId>
      <exclusions>
          <exclusion>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-data-redis</artifactId>
          </exclusion>
      </exclusions>
  </dependency>
  ```

## 5. 新增：微服务统一下发 CORS 跨域问题解决

在前后端分离架构中，前端首次去网关请求 API 数据必然受同源策略限制发出 OPTIONS 预检请求。如果我们在之前架构中让每个微服务单独配置跨域（目前 `mall-admin`和 `mall-portal` 都存在自己的 `GlobalCorsConfig`），因为现在所有外部入口都变成统一经过 `mall-gateway`，这会导致两遍跨域设置重叠。为了让浏览器正确放行请求，我们需要在网关集中接管跨域：

### 5.1 移除原有业务服务跨域机制
找到下游微服务原本独立的 Servlet 框架跨域放行器代码，执行物理移除。
- **已删除文件**：`mall-admin/src/main/java/com/macro/mall/config/GlobalCorsConfig.java`
- **已删除文件**：`mall-portal/src/main/java/com/macro/mall/portal/config/GlobalCorsConfig.java`

### 5.2 集中接管网关 WebFlux 的全局跨域
使用基于 Reactor WebFlux 原生的 `CorsWebFilter`，允许任意方法和请求头，为适配 Spring Boot 2.4 及携带 Cookie 等高级场景要求，使用 `addAllowedOriginPattern("*")` 替代老旧的 `addAllowedOrigin` 方法。
```java
// mall-gateway/src/main/java/com/macro/mall/gateway/config/GlobalCorsConfig.java
@Bean
public CorsWebFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedMethod("*");
    config.addAllowedOriginPattern("*");
    config.addAllowedHeader("*");
    config.setAllowCredentials(true);
    // ...
    return new CorsWebFilter(source);
}
```

