### 1. mall-admin 流控规则（Sentinel）：mall-admin-flow-rules
- **Data ID**: `mall-admin-flow-rules`
- **Group**: `SENTINEL_GROUP`
- **Format**: `JSON`
- **Configuration Content**:
```json
[
  {
    "resource": "/brand/recommendList",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

---

### 2. mall-admin 降级规则（Sentinel）：mall-admin-degrade-rules
- **Data ID**: `mall-admin-degrade-rules`
- **Group**: `SENTINEL_GROUP`
- **Format**: `JSON`
- **Configuration Content**:
```json
[
  {
    "resource": "com.macro.mall.portal.feign.AdminBrandFeign#recommendList",
    "limitApp": "default",
    "grade": 2,
    "count": 50,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 10000,
    "slowRatioThreshold": 0.5
  }
]
```

---

### 3. mall-gateway 网关流控规则（Sentinel）：mall-gateway-flow-rules
- **Data ID**: `mall-gateway-flow-rules`
- **Group**: `SENTINEL_GROUP`
- **Format**: `JSON`
- **Configuration Content**:
```json
[
  {
    "resource": "mall-portal",
    "limitApp": "default",
    "grade": 1,
    "count": 200,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  },
  {
    "resource": "mall-admin",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

---

### 4. mall-portal 流控规则（Sentinel）：mall-portal-flow-rules
- **Data ID**: `mall-portal-flow-rules`
- **Group**: `SENTINEL_GROUP`
- **Format**: `JSON`
- **Configuration Content**:
```json
[
  {
    "resource": "com.macro.mall.portal.feign.AdminBrandFeign#recommendList",
    "limitApp": "default",
    "grade": 1,
    "count": 50,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

---

### 5. mall-portal 降级规则（Sentinel）：mall-portal-degrade-rules
- **Data ID**: `mall-portal-degrade-rules`
- **Group**: `SENTINEL_GROUP`
- **Format**: `JSON`
- **Configuration Content**:
```json
[
  {
    "resource": "com.macro.mall.portal.feign.AdminBrandFeign#recommendList",
    "limitApp": "default",
    "grade": 2,
    "count": 20,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 10000,
    "slowRatioThreshold": 0.5
  }
]
```

---

### 6. mall-search 流控规则（Sentinel）：mall-search-flow-rules
- **Data ID**: `mall-search-flow-rules`
- **Group**: `SENTINEL_GROUP`
- **Format**: `JSON`
- **Configuration Content**:
```json
[
  {
    "resource": "/search/product/search",
    "limitApp": "default",
    "grade": 1,
    "count": 200,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```
