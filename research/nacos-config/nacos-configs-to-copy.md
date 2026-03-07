您可以直接复制以下文本，在 Nacos 控制台中分别创建对应的配置。

---

### 1. 公共配置：mall-common-dev.yaml
- **Data ID**: `mall-common-dev.yaml`
- **Group**: `DEFAULT_GROUP`
- **Format**: `YAML`
- **Configuration Content**:
```yaml
jwt:
  tokenHeader: Authorization
  secret: mall-admin-secret # 目前admin和gateway用这个
  expiration: 604800
  tokenHead: 'Bearer '

redis:
  database: mall
  expire:
    common: 86400 # 24小时

mybatis:
  mapper-locations:
    - classpath:dao/*.xml
    - classpath*:com/**/mapper/*.xml
```

---

### 2. mall-admin 的独立配置：mall-admin-dev.yaml
- **Data ID**: `mall-admin-dev.yaml`
- **Group**: `DEFAULT_GROUP`
- **Format**: `YAML`
- **Configuration Content**:
```yaml
redis:
  key:
    admin: 'ums:admin'
    resourceList: 'ums:resourceList'

secure:
  ignored:
    urls: 
      - /swagger-ui/
      - /swagger-resources/**
      - /**/v2/api-docs
      - /**/*.html
      - /**/*.js
      - /**/*.css
      - /**/*.png
      - /**/*.map
      - /favicon.ico
      - /actuator/**
      - /druid/**
      - /admin/login
      - /admin/register
      - /admin/info
      - /admin/logout
      - /minio/upload
      - /brand/recommendList

aliyun:
  oss:
    endpoint: oss-cn-shenzhen.aliyuncs.com
    accessKeyId: test
    accessKeySecret: test
    bucketName: macro-oss
    policy:
      expire: 300
    maxSize: 10
    callback: http://39.98.190.128:8080/aliyun/oss/callback
    dir:
      prefix: mall/images/
```

---

### 3. mall-portal 的独立配置：mall-portal-dev.yaml
- **Data ID**: `mall-portal-dev.yaml`
- **Group**: `DEFAULT_GROUP`
- **Format**: `YAML`
- **Configuration Content**:
```yaml
jwt:
  secret: mall-portal-secret # 注意 portal 有自己的 secret

secure:
  ignored:
    urls:
      - /swagger-ui/
      - /swagger-resources/**
      - /**/v2/api-docs
      - /**/*.html
      - /**/*.js
      - /**/*.css
      - /**/*.png
      - /**/*.map
      - /favicon.ico
      - /druid/**
      - /actuator/**
      - /sso/**
      - /home/**
      - /product/**
      - /brand/**
      - /alipay/**

redis:
  key:
    authCode: 'ums:authCode'
    orderId: 'oms:orderId'
    member: 'ums:member'
  expire:
    authCode: 90

mongo:
  insert:
    sqlEnable: true

rabbitmq:
  queue:
    name:
      cancelOrder: cancelOrderQueue
```

---

### 4. mall-search 的独立配置：mall-search-dev.yaml
- **Data ID**: `mall-search-dev.yaml`
- **Group**: `DEFAULT_GROUP`
- **Format**: `YAML`
- **Configuration Content**:
```yaml
# search 暂无特殊的独立配置，除了端口等基础信息保留在本地外，若后续有加入可在此配置。
# 可以先创建文件，留空或者随意写点注释
```

---

### 5. mall-gateway 的独立配置：mall-gateway-dev.yaml
- **Data ID**: `mall-gateway-dev.yaml`
- **Group**: `DEFAULT_GROUP`
- **Format**: `YAML`
- **Configuration Content**:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: mall-admin
          uri: lb://mall-admin
          predicates:
            - Path=/admin/**
          filters:
            - StripPrefix=1
        - id: mall-portal
          uri: lb://mall-portal
          predicates:
            - Path=/portal/**
          filters:
            - StripPrefix=1
        - id: mall-search
          uri: lb://mall-search
          predicates:
            - Path=/search/**
          filters:
            - StripPrefix=1

secure:
  ignored:
    urls:
      - /admin/admin/login
      - /portal/sso/**
      - /portal/home/**
      - /portal/product/**
      - /portal/brand/**
      - /portal/alipay/**
      - /admin/swagger-ui/**
      - /admin/swagger-resources/**
      - /admin/v2/api-docs
      - /portal/swagger-ui/**
      - /portal/swagger-resources/**
      - /portal/v2/api-docs
      - /search/swagger-ui/**
      - /*/actuator/**
```
