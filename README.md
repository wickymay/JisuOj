# JISUOJ

在线评测与 AI 编程教学平台。基于 HUSTOJ 二次开发，包含 Vue 3 + Spring Boot 的新版站点、保留的 PHP 兼容站点，以及共用的 Linux C++ 判题核心。

本仓库为 2026-10-20 整理的 **v0.1.0 源码基线**。功能和验证范围见下文及 [发布记录](docs/RELEASE.md)。

## 功能

- 用户注册、登录、个人主页、排名、关注、留言及站内信。
- 题库、题面、代码提交、评测状态、编译与运行诊断。
- 竞赛列表、私有竞赛加入、题目编排和 ACM 排名。
- 后台题目、竞赛、公告、用户和管理员管理。
- Codeforces / PTA / 手工题面导入，支持人工审核、测试数据 ZIP 与导入审计。
- AI 编程教学：题目和代码附件、分级提示、调试建议及 12 个专题教学方法。
- 可选的本地 Qwen3 Embedding 检索服务；模型和知识库需要另行配置。

打印、气球、代码分享、远程判题管理等模块仍由旧站提供。详见 [功能迁移矩阵](jisuoj-next/docs/MIGRATION_MATRIX.md)。

## 目录

```text
JisuOj/
├── jisuoj-next/
│   ├── frontend/       Vue 3、TypeScript、Vite
│   ├── backend/        Spring Boot 3.5.7、Java 17
│   ├── judge-agent/    Linux 判题容器
│   ├── rag/            可选的本地知识检索服务
│   ├── scripts/        开发与教学资源校验工具
│   ├── docs/           架构、部署和迁移说明
│   ├── .env.example    环境变量模板
│   └── docker-compose.yml
├── trunk/
│   ├── core/           HUSTOJ C++ 判题源码及 SIM 源码
│   ├── install/        数据库结构、判题配置模板与旧版安装工具
│   └── web/            PHP 兼容站点及静态资源
├── docs/               发布记录和上游资料
└── LICENSE             原有 GPL v2 许可证
```

## 快速启动新版

需要 Linux 或 WSL2、Docker Engine 和 Docker Compose v2。判题器依赖 Linux；Windows 可用于前端开发。

```bash
git clone https://github.com/wickymay/JisuOj.git
cd JisuOj/jisuoj-next
cp .env.example .env
```

编辑 `.env`，为 `DB_PASSWORD`、`DB_ROOT_PASSWORD`、`AUTH_SECRET`、`AI_CONFIG_SECRET` 设置独立的随机值。认证和 AI 加密密钥至少 32 个字符；请保存好 `AI_CONFIG_SECRET`。

```bash
docker compose up --build -d
docker compose ps
```

访问 **http://localhost:8088**。首次使用创建空数据库，仅导入表结构及上游示例公告；不会包含现有站点的账号、题库、提交或测试数据。

基础服务正常后，启动判题器：

```bash
docker compose --profile judge up --build -d judge-agent
```

判题容器当前需要特权能力，部署环境及隔离要求见 [部署说明](jisuoj-next/docs/DEPLOYMENT.md)。数据库和判题数据分别保存在命名卷 `mysql-data`、`judge-data`，后续启动不要删除数据卷。

### 首个超级管理员

先在页面注册自己的账号，再进入数据库：

```bash
docker compose exec db mysql -uroot -p jol
```

输入 `.env` 中的数据库 root 密码，执行以下 SQL，将用户名替换成已注册的账号：

```sql
INSERT INTO privilege(user_id, rightstr, valuestr, defunct)
VALUES ('你的用户名', 'super_administrator', 'true', 'N');
```

退出数据库并重新登录网站。之后可在“其他 → 管理员列表”维护普通管理员。

### AI 教学与检索

超级管理员在 AI 配置页面设置兼容接口地址、模型和 API Key。API Key 通过运行时配置保存，不应写入代码。

12 个专题的教学资源随源码提供；[本地 RAG](jisuoj-next/docs/RAG.md) 是可选功能，仓库不包含书籍全文、扫描件、OCR 资料、模型权重或私人知识库。未启用 RAG 时可以使用已配置的聊天模型。

## 本地开发与检查

后端需要 Java 17 和 Maven 3.9；前端推荐 Node.js 24 LTS，使用提交的 lockfile 安装依赖。

```bash
cd jisuoj-next/backend
mvn test
# 配置本机数据库、认证密钥及 JUDGE_DATA_DIR 后运行：
mvn spring-boot:run
```

另开终端：

```bash
cd jisuoj-next/frontend
npm ci
npm test
npm run build
npm run dev
```

Vite 默认将 `/api` 转发至 `http://localhost:8080`；可通过 `VITE_DEV_API_TARGET` 修改。原生开发不会自动加载 Compose 的 `.env`，需要在后端进程环境中设置相应变量。

## 文档

- [架构](jisuoj-next/docs/ARCHITECTURE.md) · [部署与迁移](jisuoj-next/docs/DEPLOYMENT.md)
- [AI 教学资源](jisuoj-next/docs/TEACHING_SKILLS.md) · [可选知识检索](jisuoj-next/docs/RAG.md)
- [发布内容与验证范围](docs/RELEASE.md) · [旧站使用说明](docs/LEGACY.md)
- [上游及第三方说明](THIRD_PARTY_NOTICES.md)

## 来源与许可

本项目基于 [HUSTOJ](https://github.com/zhblue/hustoj)，保留原作者版权声明和 [GPL v2 许可证](LICENSE)。随附的第三方库、主题、字体及 SIM 工具保留各自的许可证和归属。项目名称及学校标识不表示上游或学校对本仓库作出认证。
