# 部署与切换

## 推荐环境

- Ubuntu 24.04 LTS 或同等级 Linux。
- Docker Engine 与 Compose v2。
- 至少 4 核 CPU、8 GiB 内存；判题并发越高，资源要求越高。
- HTTPS 反向代理。生产环境设置 `AUTH_SECURE_COOKIE=true`。
- 生产环境为 `AI_CONFIG_SECRET` 设置独立的高强度随机值并持久保管；它用于加密数据库中的智能体 API Key，变更后需由超级管理员重新保存 API Key。

## 灰度迁移

1. 备份现有 MySQL 和 `/home/judge/data`。
2. 在测试数据库恢复备份，启动 backend；Flyway 只会创建 `jisu_` 扩展表。
3. 对登录、题面、提交、竞赛权限、拉题和判题结果做回归测试。
4. 新前端先使用独立域名或端口，旧站继续可用。
5. 观察一段时间后把主域名切到新前端；将旧站限制为低频兼容功能。
6. 完成迁移矩阵的剩余模块后，再移除 PHP Web 层。

## 数据库升级注意事项

现有库若来自 MariaDB 或较旧 MySQL，应先在副本上执行升级验证。Compose 中挂载的 `db.sql` 只用于全新空卷；对已有数据不要重新执行初始化脚本。

## 判题器

`judge-agent` 使用 Compose profile，默认不会启动：

```bash
docker compose --profile judge up --build -d
```

它需要 Linux 特权能力。生产环境应把 Web 服务和判题器放在不同主机或至少不同安全域，限制判题器数据库账号权限、网络出口和可用 CPU/内存。

## 回滚

切回旧 Nginx upstream 即可恢复旧 Web。新系统保留旧表语义，新增表使用 `jisu_` 前缀，因此无需在紧急回滚时删除数据库对象。任何正式迁移前仍必须保留可验证的完整备份。
