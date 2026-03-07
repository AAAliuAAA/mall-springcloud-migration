# Mall项目 Nacos-Config 配置中心改造记录

## 1. 改造目标与背景
将各服务中分散的配置文件集中迁移到 Nacos Config 统一管理，实现：
- 公共配置（JWT secret、Redis、数据库等）一处修改，全部服务生效。
- 敏感配置不再硬编码在代码仓库中。
- Gateway 白名单支持动态刷新，无需重启服务即可生效。

## 2. 改造步骤与计划
通过交互式逐步推进，总体分为以下步骤：

### 阶段一：依赖与引导配置准备
- [x] **Step 1：引入 Nacos Config 依赖**
  - 涉及模块：`mall-admin`, `mall-portal`, `mall-search`, `mall-gateway`
  - 需引入 `spring-cloud-starter-alibaba-nacos-config` 并在 Spring Boot 2.4+ 环境下引入 `spring-cloud-starter-bootstrap` 支持。
- [x] **Step 2：各服务新建 `bootstrap.yml`**
  - 设定基础信息、Nacos 服务地址（`127.0.0.1:8848`）、`namespace` 和 `group`。

### 阶段二：配置抽取与创建 (Nacos 控制台)
- [x] **Step 3：在 Nacos 控制台创建配置文件**
  - 创建名为 `dev` 的命名空间，并记录系统分配的 Namespace ID。
  - 创建公共配置 `mall-common-dev.yaml` （含 JWT, Redis 等共享参数）。
  - 创建各微服务独立配置，如 `mall-admin-dev.yaml`, `mall-gateway-dev.yaml` 等（含数据源、独立白名单等）。

### 阶段三：服务读取与冗余清理
- [x] **Step 4：配置共享挂载**
  - 在 `bootstrap.yml` 中使用 `shared-configs` 挂载 `mall-common-dev.yaml`。
- [x] **Step 5：清理本地冗余配置**
  - 从各模块的 `application.yml` 中移除已迁移到 Nacos 的配置，仅保留端口号（`server.port`）、Nacos Discovery 注册中心等本地优先配置。

### 阶段四：动态刷新与最终验证
- [x] **Step 6：改造 Gateway 白名单以支持动态刷新**
  - 为 `IgnoreUrlsConfig.java` 标注 `@RefreshScope` 与 `@Component`，支持修改 Nacos 实时生效。
- [ ] **Step 7：全面验证配置加载与共享机制**
  - 启动所有涉及服务，观察启动日志是否打印从 Nacos 拉取配置。
  - 测试 Gateway 接口联通性与白名单热更新能力。

## 3. 遇到的问题与解决方案记录
- （在改造过程中如遇报错、依赖冲突或坑点，将记录在此以备后查）

## 4. 总结与最佳实践
- （待改造完成后总结整体配置架构及心得）
