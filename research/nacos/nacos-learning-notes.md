# Nacos 注册中心集成与学习笔记

## 1. 概述
为构建微服务架构，项目中引入了 **Alibaba Nacos** 作为统一的服务发现与注册中心。以下是对 Nacos 服务端部署以及在现有业务服务（`mall-admin`、`mall-portal`、`mall-search`）中集成 Nacos 过程的整理记录。

## 2. Nacos 服务端部署

为了快速起服以及不污染物理环境，服务端采用 Docker Compose 形式运行。我们在项目的 `my-config/docker/nacos/docker-compose.yml` 中建立了启动脚本：

```yaml
services:
  nacos:
    image: nacos/nacos-server:v2.1.2
    container_name: nacos
    environment:
      # 单机模式启动，不开启集群
      - MODE=standalone
      # 开启持久化挂载至业务的 MySQL
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=host.docker.internal
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_PORT=3306
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=123
      # 设置较低的内存占用
      - JVM_XMS=512m
      - JVM_XMX=512m
      # 本地开发测试关闭权限控制
      - NACOS_AUTH_ENABLE=false
    ports:
      - "8848:8848" # 控制台及服务注册主端口
      - "9848:9848" # Nacos 2.x 新增的 gRPC 端口
    restart: always
```
*注：上述配置复用了宿主机中的 `nacos_config` 数据库作为持久化存放处。*

## 3. 微服务改造与接入策略

要让普通的 Spring Boot 模块挂载到 Nacos Server 上，需要经历三个标准化步骤：

### 3.1 顶层依赖池声明
在聚合工程的主 `pom.xml` 的 `dependencyManagement` 中，必须提前框定好 Spring Cloud 以及 Spring Cloud Alibaba 的大版本号：
```xml
<properties>
    <spring-cloud.version>2021.0.5</spring-cloud.version>
    <spring-cloud-alibaba.version>2021.0.5.0</spring-cloud-alibaba.version>
</properties>
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3.2 子模块注入依赖
在需要注册至网关及被别处调用的子模块（如 `mall-admin`、`mall-portal`、`mall-search`）独立 `pom.xml` 中，引入 Nacos 探索客户端：
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

### 3.3 补全配置文件
进入每个微服务的 `application.yml` 进行通讯连接配置配置。指明 Nacos Registry 的地址：
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```
*这告诉了当前应用往哪个 8848 端口去宣告自己的存活及路由表。*

### 3.4 启动类增加注解支持
为微服务的入口类（例如 `MallSearchApplication.java` ）打上专属注解开启客户端发现能力：
```java
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MallSearchApplication {
    // ...
}
```

## 4. 总结
经过以上步骤，基于 Spring Cloud Alibaba 的基础微服务治理格局就建立了起来。每个带着 `@EnableDiscoveryClient` 启动的业务进程都会定时向 Nacos (8848) 汇报心跳，同时 Nacos 能够在各服务间作软负载均衡。这为后来网关（mall-gateway）能够根据服务名统一分发流量打下了基石。
