# 架构说明

## 设计目标

重构不是简单地把 PHP 模板换成另一套语法，而是重新划分边界：浏览器只处理交互，Spring Boot 负责业务与权限，MySQL 保存业务状态，判题器作为受限的 Linux 工作负载独立运行。迁移期间，新旧系统共享经过确认的旧表和题目数据，因此不需要一次性停机切换。

```text
Browser
   │ HTTPS / JSON
   ▼
Vue 3 + Nginx ──────► Spring Boot modular monolith
                           │            │
                           │ JDBC       │ UDP notification
                           ▼            ▼
                       MySQL 8.4     Linux judge-agent
                           ▲            │
                           └────────────┘
                              solution / result
```

## 为什么先采用模块化单体

毕业设计需要体现清晰的架构工作量，但 OJ 当前规模没有必要引入微服务的网络、事务和运维复杂度。后端按业务域拆分为 auth、problem、submission、contest、ranking、discussion、user、mail、news、problem-import 与 admin；它们现在共同部署，后续若有真实负载需要，可沿现有边界拆分。

## 数据兼容策略

1. 继续使用现有 `jol` 表，使旧站和新站可以灰度共存。
2. Flyway 仅增加 `jisu_` 前缀的认证升级、题目来源和审计表，不重写旧数据。
3. 旧 `users.password` 仍可登录；登录成功后同时写入 BCrypt 凭据，实现无感升级。
4. 题面和提交继续写入 `problem`、`solution`、`source_code` 等旧表，判题器无需感知前端重构。
5. 删除优先使用旧表的 `defunct='Y'` 软删除语义。

## 安全边界

- JWT 仅存于 HttpOnly、SameSite Cookie，写请求启用 CSRF Token。
- 权限使用 Spring Security 的 ADMIN、PROBLEM_EDITOR 等角色检查。
- 拉题只允许已知来源适配器，不接受任意内网 URL；题面 HTML 先清理再入库。
- AI 只生成候选草稿，不能直接发布，远端题面中的文字不能改变系统规则。
- 测试数据 ZIP 限制压缩包大小、解压总量和文件数，并检查路径穿越及 `.in/.out` 配对。
- 判题器必须在 Linux 隔离环境运行；容器的 `privileged` 权限只授予判题服务，不授予 Web 服务。

## 跨平台范围

- 前端、后端与数据库：可在 Windows、WSL 和 Linux 开发。
- 正式部署：Linux。
- 判题器：Linux / WSL2，不提供原生 Windows 版本。

不做原生 Windows 判题适配显著降低了工作量和风险。Windows 与 Linux 在用户、信号、`ptrace`、`setrlimit`、chroot、编译器路径和进程统计方面差异很大；强行抽象会消耗大量时间，却不增加 OJ 的核心业务价值。

## UI 原则

沿用原站深蓝色导航和数据表格风格，同时提高字号、对比度和响应式布局。评测结果不再使用“彩色背景 + 蓝色文字”，而是无背景的标准短码：AC 为绿色，WA/TLE/MLE/RE/OLE 为红色，CE 为紫色，等待与评测中为黄色；不再显示“得分 0”。
