# Mall 项目 Docker Compose 统一部署指南

> 三个 compose 文件分离管理，共享同一个 Docker 网络，Sentinel Dashboard 完全容器化。

---

## 一、整体架构

```
docker-compose-base.yml       基础设施 + 监控（MySQL、Redis、Nacos、RabbitMQ、MongoDB、MinIO、Sentinel、Zipkin）
docker-compose-elk.yml        日志体系（Elasticsearch、Logstash、Kibana）
docker-compose-business.yml   业务服务（mall-admin、mall-portal、mall-search、mall-gateway）

三个文件共享同一个 Docker 网络：mall-network
容器间通过服务名互相访问，不使用 localhost
```

---

## 二、前置准备

### 2.1 创建共享网络

所有 compose 文件启动前，手动创建一次外部网络：

```bash
docker network create mall-network
```

### 2.2 修改配置中的 localhost（最关键的一步）

容器内不能用 `localhost` / `127.0.0.1` 访问其他容器，必须改成 Docker Compose 中的**服务名**。

#### 配置全景图

本项目的配置分散在两个层面，容器化时**两边都有 localhost 需要替换**：

```
┌─────────────────────────────────────────────────────────────┐
│  本地配置文件（打包进 jar）                                    │
│  ├── bootstrap.yml    → Nacos 地址、Sentinel Dashboard 地址   │
│  └── application.yml  → Nacos Discovery 地址、Zipkin 地址     │
├─────────────────────────────────────────────────────────────┤
│  Nacos 远程配置文件（运行时从 Nacos 拉取，优先级高于本地）        │
│  ├── mall-common-dev.yaml   → 公共配置（jwt、mybatis）         │
│  ├── mall-admin-dev.yaml    → MySQL、Redis、MinIO、Logstash   │
│  ├── mall-portal-dev.yaml   → MySQL、Redis、MongoDB、RabbitMQ │
│  ├── mall-search-dev.yaml   → MySQL、Elasticsearch、Logstash  │
│  └── mall-gateway-dev.yaml  → 路由规则（无 localhost）          │
└─────────────────────────────────────────────────────────────┘
```

#### 迁移思路

1. **先合并**：将本地 `application-dev.yml` 中的基础设施配置（datasource、redis 等）合并到 Nacos 对应的 `-dev.yaml` 中，实现配置统一管理
2. **再替换**：将两个层面中所有的 `localhost` 替换为 Docker 服务名
3. **删除冗余**：合并到 Nacos 后，本地的 `application-dev.yml` 可以删除

> **配置优先级**：`bootstrap.yml` > Nacos 远程配置 > `application.yml` > `application-dev.yml`。同名配置项 Nacos 会覆盖本地。

---

#### A. 本地配置文件修改（需要改 bootstrap.yml 和 application.yml）

**① mall-gateway/bootstrap.yml**（改 sentinel dashboard + nacos server-addr）
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: sentinel:8858         # localhost:8858 → sentinel:8858
        port: 8721
      datasource:
        gateway-flow-rules:
          nacos:
            server-addr: nacos:8848      # 127.0.0.1:8848 → nacos:8848
    nacos:
      config:
        server-addr: nacos:8848          # 127.0.0.1:8848 → nacos:8848
```

**② mall-gateway/application.yml**（改 nacos discovery + zipkin）
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848          # 127.0.0.1:8848 → nacos:8848
  zipkin:
    base-url: http://zipkin:9411         # localhost:9411 → zipkin:9411
```

**③ mall-admin/bootstrap.yml**
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: sentinel:8858         # localhost:8858 → sentinel:8858
        port: 8719
      datasource:
        flow-rules:
          nacos:
            server-addr: nacos:8848      # 127.0.0.1:8848 → nacos:8848
        degrade-rules:
          nacos:
            server-addr: nacos:8848      # 127.0.0.1:8848 → nacos:8848
    nacos:
      config:
        server-addr: nacos:8848          # 127.0.0.1:8848 → nacos:8848
```

**④ mall-admin/application.yml**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
  zipkin:
    base-url: http://zipkin:9411
```

**⑤ mall-portal/bootstrap.yml**
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: sentinel:8858
        port: 8720
      datasource:
        flow-rules:
          nacos:
            server-addr: nacos:8848
        degrade-rules:
          nacos:
            server-addr: nacos:8848
    nacos:
      config:
        server-addr: nacos:8848
```

**⑥ mall-portal/application.yml**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
  zipkin:
    base-url: http://zipkin:9411
```

**⑦ mall-search/bootstrap.yml**
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: sentinel:8858
        port: 8722
      datasource:
        search-flow-rules:
          nacos:
            server-addr: nacos:8848
    nacos:
      config:
        server-addr: nacos:8848
```

**⑧ mall-search/application.yml**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
  zipkin:
    base-url: http://zipkin:9411
```

---

#### B. Nacos 配置文件中的 localhost 替换

合并后的 Nacos 配置文件中包含基础设施连接地址，容器化时需要替换为服务名。
以下只列出**需要改动的行**，完整配置文件见 `research/docker/` 目录。

**mall-admin-dev.yaml**（4 处替换）
```yaml
spring.datasource.url:       localhost:3306 → mysql:3306
spring.redis.host:           localhost → redis
minio.endpoint:              localhost:9000 → minio:9000
logstash.host:               localhost → logstash
```

**mall-portal-dev.yaml**（5 处替换）
```yaml
spring.datasource.url:       localhost:3306 → mysql:3306
spring.data.mongodb.host:    localhost → mongodb
spring.redis.host:           localhost → redis
spring.rabbitmq.host:        localhost → rabbitmq
logstash.host:               localhost → logstash
```

**mall-search-dev.yaml**（3 处替换）
```yaml
spring.datasource.url:       localhost:3306 → mysql:3306
spring.elasticsearch.uris:   localhost:9200 → elasticsearch:9200
logstash.host:               localhost → logstash
```

**mall-common-dev.yaml** 和 **mall-gateway-dev.yaml** 中没有 localhost，无需修改。

---

> **注意事项**：
> - MongoDB 配置在 `mall-portal` 中，**不是** `mall-admin`
> - Elasticsearch 写法是 `spring.elasticsearch.uris`，**不是** `spring.elasticsearch.rest.uris`
> - Sentinel 的 `datasource` 持久化配置在 `bootstrap.yml` 中，也需要替换 `nacos.server-addr`
> - 上面的服务名（mysql、redis、nacos 等）必须与 docker-compose 中的 service name 一致



### 2.3 打包业务服务

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

确认以下 jar 文件存在：

```
mall-admin/target/mall-admin.jar
mall-portal/target/mall-portal.jar
mall-search/target/mall-search.jar
mall-gateway/target/mall-gateway.jar
```

### 2.4 各业务模块新增 Dockerfile

**mall-admin/Dockerfile**
```dockerfile
FROM openjdk:8-jre-alpine
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' > /etc/timezone
COPY target/mall-admin.jar /mall-admin.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/mall-admin.jar"]
```

**mall-portal/Dockerfile**
```dockerfile
FROM openjdk:8-jre-alpine
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' > /etc/timezone
COPY target/mall-portal.jar /mall-portal.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/mall-portal.jar"]
```

**mall-search/Dockerfile**
```dockerfile
FROM openjdk:8-jre-alpine
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' > /etc/timezone
COPY target/mall-search.jar /mall-search.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/mall-search.jar"]
```

**mall-gateway/Dockerfile**
```dockerfile
FROM openjdk:8-jre-alpine
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' > /etc/timezone
COPY target/mall-gateway.jar /mall-gateway.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/mall-gateway.jar"]
```

---

## 三、docker-compose-base.yml

```yaml
version: '3.8'

services:

  mysql:
    image: mysql:5.7
    container_name: mall-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
    volumes:
      - ./document/sql:/docker-entrypoint-initdb.d
      - mysql-data:/var/lib/mysql
    networks:
      - mall-network
    restart: unless-stopped

  redis:
    image: redis:7
    container_name: mall-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - mall-network
    restart: unless-stopped

  nacos:
    image: nacos/nacos-server:v2.2.0
    container_name: mall-nacos
    ports:
      - "8848:8848"
      - "9848:9848"
    environment:
      MODE: standalone
    volumes:
      - nacos-data:/home/nacos/data
    networks:
      - mall-network
    restart: unless-stopped

  rabbitmq:
    image: rabbitmq:3.9-management
    container_name: mall-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    networks:
      - mall-network
    restart: unless-stopped

  mongo:
    image: mongo:4
    container_name: mall-mongo
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    networks:
      - mall-network
    restart: unless-stopped

  minio:
    image: minio/minio
    container_name: mall-minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio-data:/data
    command: server /data --console-address ":9001"
    networks:
      - mall-network
    restart: unless-stopped

volumes:
  mysql-data:
  redis-data:
  nacos-data:
  rabbitmq-data:
  mongo-data:
  minio-data:

networks:
  mall-network:
    external: true
```

---

## 四、docker-compose-elk.yml

```yaml
version: '3.8'

services:

  elasticsearch:
    image: elasticsearch:7.17.3
    container_name: mall-elasticsearch
    ports:
      - "9200:9200"
      - "9300:9300"
    environment:
      discovery.type: single-node
      ES_JAVA_OPTS: "-Xms512m -Xmx512m"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - mall-network
    restart: unless-stopped

  logstash:
    image: logstash:7.17.3
    container_name: mall-logstash
    ports:
      - "4560:4560"
    volumes:
      - ./document/elk/logstash.conf:/usr/share/logstash/pipeline/logstash.conf
    networks:
      - mall-network
    depends_on:
      - elasticsearch
    restart: unless-stopped

  kibana:
    image: kibana:7.17.3
    container_name: mall-kibana
    ports:
      - "5601:5601"
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    networks:
      - mall-network
    depends_on:
      - elasticsearch
    restart: unless-stopped

volumes:
  es-data:

networks:
  mall-network:
    external: true
```

---

## 五、docker-compose-monitor.yml

```yaml
version: '3.8'

services:

  zipkin:
    image: openzipkin/zipkin:2
    container_name: mall-zipkin
    ports:
      - "9411:9411"
    networks:
      - mall-network
    restart: unless-stopped

  sentinel:
    image: bladex/sentinel-dashboard:1.8.6
    container_name: mall-sentinel
    ports:
      - "8858:8858"
    environment:
      JAVA_OPTS: >-
        -Dserver.port=8858
        -Dcsp.sentinel.dashboard.server=localhost:8858
        -Dproject.name=sentinel-dashboard
        -Dsentinel.dashboard.auth.username=sentinel
        -Dsentinel.dashboard.auth.password=123456
    networks:
      - mall-network
    restart: unless-stopped

networks:
  mall-network:
    external: true
```

**Sentinel 控制台登录信息：**

| 项目 | 值 |
|------|---|
| 地址 | http://localhost:8858 |
| 用户名 | sentinel |
| 密码 | 123456 |

---

## 六、docker-compose-business.yml

```yaml
version: '3.8'

services:

  mall-admin:
    build:
      context: ./mall-admin
      dockerfile: Dockerfile
    container_name: mall-admin
    ports:
      - "8080:8080"
    networks:
      - mall-network
    restart: unless-stopped

  mall-portal:
    build:
      context: ./mall-portal
      dockerfile: Dockerfile
    container_name: mall-portal
    ports:
      - "8085:8085"
    networks:
      - mall-network
    restart: unless-stopped

  mall-search:
    build:
      context: ./mall-search
      dockerfile: Dockerfile
    container_name: mall-search
    ports:
      - "8081:8081"
    networks:
      - mall-network
    restart: unless-stopped

  mall-gateway:
    build:
      context: ./mall-gateway
      dockerfile: Dockerfile
    container_name: mall-gateway
    ports:
      - "8088:8088"
    networks:
      - mall-network
    restart: unless-stopped

networks:
  mall-network:
    external: true
```

> `depends_on` 跨 compose 文件不生效，启动顺序靠手动控制。

---

## 七、启动顺序

```bash
# Step 1：创建共享网络（只需执行一次）
docker network create mall-network

# Step 2：启动基础设施
docker-compose -f docker-compose-base.yml up -d
# 等待约 30 秒，确认 Nacos 可访问：http://localhost:8848/nacos

# Step 3：启动日志体系
docker-compose -f docker-compose-elk.yml up -d

# Step 4：启动监控体系
docker-compose -f docker-compose-monitor.yml up -d
# 确认 Zipkin：http://localhost:9411
# 确认 Sentinel：http://localhost:8858

# Step 5：打包业务服务（有代码更新时执行）
mvn clean package -DskipTests

# Step 6：构建并启动业务服务
docker-compose -f docker-compose-business.yml up -d --build
```

---

## 八、常用运维命令

```bash
# 查看某个 compose 的容器状态
docker-compose -f docker-compose-business.yml ps

# 查看容器日志
docker logs mall-admin -f --tail 100

# 代码更新后重新构建单个服务
docker-compose -f docker-compose-business.yml up -d --build mall-admin

# 重启某个服务
docker-compose -f docker-compose-business.yml restart mall-portal

# 进入容器内部排查
docker exec -it mall-admin /bin/sh

# 查看资源占用
docker stats mall-admin mall-portal mall-search mall-gateway

# 停止业务服务（不影响基础设施）
docker-compose -f docker-compose-business.yml down

# 停止所有服务（从业务层到基础层逆序停止）
docker-compose -f docker-compose-business.yml down
docker-compose -f docker-compose-monitor.yml down
docker-compose -f docker-compose-elk.yml down
docker-compose -f docker-compose-base.yml down
```

---

## 九、验证步骤

```
1. docker network ls
   → 确认 mall-network 存在

2. docker-compose -f docker-compose-base.yml ps
   → 确认基础设施全部 Up

3. http://localhost:8848/nacos → 服务列表里看到四个业务服务注册

4. http://localhost:8088/portal/brand/recommendList?pageNum=1&pageSize=6
   → 确认 Gateway → Portal → Admin 链路正常

5. http://localhost:9411 → Zipkin 能看到 Trace

6. http://localhost:8858 → Sentinel 能看到四个业务服务和规则

7. http://localhost:5601 → Kibana 日志正常收集
```

---

## 十、注意事项

| 注意点 | 说明 |
|-------|------|
| Nacos 配置里所有 localhost 必须改成服务名 | 最容易漏掉的坑，容器化前必须完成 |
| bootstrap.yml 里的 Nacos 地址也要改 | 不只是 Nacos 里的配置，本地 bootstrap.yml 里的 server-addr 也要改成 nacos:8848 |
| Sentinel client-ip 改为容器服务名 | 否则控制台无法回调，看不到监控数据 |
| depends_on 不保证服务就绪 | Nacos 启动慢，手动等待确认后再启业务服务 |
| mall-network 只创建一次 | 重复创建会报错，用 `docker network ls` 确认是否已存在 |
| 时区在 Dockerfile 里已处理 | 否则日志时间会差 8 小时 |
| 停止顺序与启动顺序相反 | 先停业务服务，最后停基础设施 |