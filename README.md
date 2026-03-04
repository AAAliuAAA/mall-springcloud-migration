# mall 项目的SpringCloud分布式集成实践

## 原mall项目文档

文档地址：[https://www.macrozheng.com](https://www.macrozheng.com)

## 前言
- `mall`项目致力于打造一个完整的电商系统，采用现阶段主流技术实现。本项目mall-springcloud-migration在mall项目的基础上集成当下主流分布式微服务项目常用组件并进行二次开发，供学习交流使用。感谢原作者Macro Zheng开源
- 在接触本项目前推荐先了解mall项目并已经将mall项目部署到本地。

## 原mall项目仓库地址

https://github.com/macrozheng/mall

## 迁移方案
- 注册中心： Nacos（服务注册和发现）
- 内层网关：Spring Cloud Gateway（动态路由，业务鉴权，协议转换，搭配Sentinel进行熔断）
- 外层网关：Nginx （负责初级并发，SSL卸载，静态资源转发）
- 配置中心：Nacos Config
- 远程调用：OpenFeign
- 熔断器：Sentinel
- 定时任务调度：XXLJob

# 迁移效果展示
请关注分支my-config


## 说明
本项目基于 [macrozheng/mall](https://github.com/macrozheng/mall) 进行二次配置和修改，
用于学习和展示目的。原项目遵循 Apache License 2.0。