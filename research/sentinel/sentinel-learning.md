# Mall项目 Sentinel 熔断限流改造记录

## 1. 改造目标与背景
为 mall 微服务体系引入 Sentinel，解决服务故障引发的系统雪崩问题，实现熔断降级、流量控制与系统保护。

## 2. 改造步骤与计划
通过交互式逐步推进，总体分为以下步骤：

### 阶段一：控制台与基础接入
- [x] **Step 1：部署 Sentinel 控制台**
  - 下载并启动 Sentinel 1.8.6 Dashboard 面板。
- [x] **Step 2：各服务引入 Sentinel 依赖**
  - 涉及模块：`mall-admin`, `mall-portal`, `mall-gateway`, `mall-search`
  - 需引入 `spring-cloud-starter-alibaba-sentinel`。
- [x] **Step 3：各服务连接 Sentinel 控制台**
  - 在 `application.yml` 或 `bootstrap.yml` 中配置 dashboard 地址与独立通信端口（`8719` / `8720` / `8721` / `8722`等）。

### 阶段二：OpenFeign 熔断降级 (Portal -> Admin 链路)
- [x] **Step 4：OpenFeign 开启 Sentinel 支持**
  - 在 `mall-portal` 的配置中激活 `feign.sentinel.enabled=true`。
- [x] **Step 5：为 OpenFeign 接口编写 Fallback**
  - 创建 `AdminBrandFeignFallbackFactory`。
  - 在 `AdminBrandFeign` 注解中指定 `fallbackFactory`，实现服务降级并返回兜底空数据。

### 阶段三：网关层限流设计
- [x] **Step 6：Gateway 整合 Sentinel**
  - 在 `mall-gateway` 引入网关专属适配模块 `spring-cloud-alibaba-sentinel-gateway`。
  - 通过代码或 Nacos 配置对 Portal 和 Admin 路由增加限流规则。

### 阶段四：生产环境架构完善
- [x] **Step 7：Sentinel 规则持久化到 Nacos**
  - 在 `mall-admin` （等核心模块）引入 `sentinel-datasource-nacos`。
  - 在配置中添加 `spring.cloud.sentinel.datasource` 节点，绑定 Nacos。
  - 在 Nacos 的 `dev` 命名空间中创建流控、熔断规则 JSON 配置。

### 阶段五：测试与验证
- [ ] **Test 1：Feign 熔断降级**
  - **前置**：启动 Nacos, Sentinel, Redis, RabbitMQ。启动 Gateway, Portal。（强制不要启动 Admin）
  - **操作**：通过浏览器或 Postman 访问 `/portal/brand/recommendList?pageNum=1&pageSize=6`。
  - **预期**：快速返回 HTTP 200 和空数组 `[]`，无 500 报错和大量超时等待。
- [ ] **Test 2：Nacos 规则持久化拉取**
  - **前置**：在 Nacos 控制台建好 `mall-admin-flow-rules`、`mall-admin-degrade-rules` 和 `mall-gateway-flow-rules` 配置（可从 `sentinel-nacos-configs.md` 复制）。
  - **操作**：重启 `mall-admin` 和 `mall-gateway` 服务。
  - **预期**：刷新 Sentinel Dashboard，能直接在页面的流控/降级规则里看到 Nacos 中配置的规则，无需再在 Dashboard 手动加。

## 3. 验证计划
- 启动服务后，验证控制台连接情况（触发一次 API 请求以懒加载注册）。
- 断开 `mall-admin` 这个底层服务，从网关打向 `mall-portal` 的 recommendList 接口，验证能否成功实现 Feign Fallback 返回空列表而非 500 错误。

## 4. 遇到的问题与解决方案记录
- 

## 5. 总结与最佳实践
- 
