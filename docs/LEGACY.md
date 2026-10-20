# PHP 兼容站点

`trunk/web` 保留现有 PHP 站点和页面改动，供迁移期间使用；新版推荐入口是 `jisuoj-next`。Compose 的基础启动只部署新版，不会自动公开 PHP 站点。

旧站使用独立的 Nginx/PHP-FPM 配置，以及同样的 `jol` 表结构和 Linux 判题数据。安装工具保留于 `trunk/install`，历史安装资料见 [HUSTOJ 说明](upstream/HUSTOJ_README.md)。这些历史脚本可能从上游下载文件；维护本项目时请先检查脚本内容，避免覆盖本仓库的定制代码。

## 配置

- 部署前修改 `trunk/web/include/db_info.inc.php` 中的数据库账号和密码；发布副本仅提供占位值。
- 如需邮件或第三方登录，另行填写 SMTP / 应用配置；发布副本不包含这些凭据。
- 旧版 AI 拉题配置可从 `trunk/web/include/problem_pull.local.php.example` 复制为 `problem_pull.local.php`，在服务器填写密钥；实际配置已被 Git 忽略。
- 旧判题配置 `trunk/install/judge.conf` 仅为模板。新版 judge-agent 通过环境变量生成运行配置。

数据库备份、测试数据、题库、现有账户及用户提交需要从原站另行迁移；本仓库不携带这类运行数据。应先在副本上验证后再切换，参见 [部署说明](../jisuoj-next/docs/DEPLOYMENT.md)。
