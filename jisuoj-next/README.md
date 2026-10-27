# JISUOJ Next

JISUOJ Next 是对现有 HUSTOJ 二次开发项目的渐进式重构。新系统采用 Vue 3 + TypeScript、Spring Boot + Java 17、MySQL 8.4，并把 Linux 判题程序隔离为独立 `judge-agent`。旧 PHP 项目没有被覆盖，可在迁移期继续运行和回退。

完整仓库的启动入口和首个管理员设置见 [项目首页](../README.md)。本目录必须与 `../trunk` 一起保留，Compose 初始化与判题构建依赖其中的源码。

## 目录结构

```text
jisuoj-next/
├─ frontend/       Vue 3 单页应用与 Nginx 配置
├─ backend/        Spring Boot 模块化单体 API
├─ judge-agent/    HUSTOJ C++ 判题服务的 Linux 容器
├─ docs/           架构、迁移矩阵与部署说明
├─ scripts/        本地开发脚本
└─ docker-compose.yml
```

## 已迁移功能

- 登录、注册、JWT HttpOnly Cookie、CSRF 防护与旧 HUSTOJ 密码兼容升级。
- 题库、题目详情、代码提交、评测状态与诊断信息。
- 竞赛列表、私有竞赛加入、题目列表与 ACM 榜单。
- 用户排名、个人主页、关注、留言和站内信。
- 公告和讨论。
- 题目、竞赛、公告、用户状态与角色的管理 API。
- 自研拉题功能：Codeforces / PTA / 原始题面、确定性解析、可选 AI 辅助、人工审核、测试数据 ZIP、JudgeData 交接和审计记录。
- 国内模型协作生成测试数据：Qwen 生成器、DeepSeek 标准解、GLM 独立解与校验、可选 Kimi 复核，隔离验证后采用或下载 ZIP。四家 API 在“智能体配置”统一管理，部署见 [测试数据生成](docs/TEST_DATA_GENERATION.md)。
- 题目拉取草稿、参考程序、当前步骤和已选 ZIP 按账号在当前浏览器暂存 24 小时；导入成功或手动清除后移除暂存。
- 基础 AI 编程教学：登录后从顶部“问 AI”直接提问；可在任意对话附加站内题目，附加时后端读取题面文字并与当前提问一起发送给已配置的 DeepSeek 兼容接口。写题页“查看通过用户”旁可带当前题目和编辑器代码跳转问 AI；最近 5 次本题提交的评测摘要需用户主动勾选才发送，不含历史源码。对话与代码附件保存在当前浏览器，AI 回答仅供学习参考。
- 兼容现有 `jol` 数据表和 `/home/judge/data` 数据目录；判题仍复用经过验证的 HUSTOJ C++ 核心。

打印、气球、代码分享、远程判题管理及旧 AI 队列等低频模块尚未重新实现 UI，迁移状态见 [迁移矩阵](docs/MIGRATION_MATRIX.md)。迁移期可以从旧站访问这些功能。

## 快速启动

AI 问答支持可选的本地 Qwen3 Embedding 知识库，检索资料和模型权重不随仓库提供。启用方法及配置见 [AI 问答知识库](docs/RAG.md)。

AI 教学同时加载基于九本完整转写资料提炼的 12 个专题技能，按问题选择教学方法，支持逐级提示、完整讲解和调试。技能包位置、生成范围、启停配置及验证方法见 [AI 教学技能包](docs/TEACHING_SKILLS.md)。

推荐在 Linux 或 WSL2 中使用 Docker Compose。判题器依赖 Linux 的进程隔离、资源限制与系统调用控制，不做原生 Windows 判题适配。

```bash
cd JisuOj/jisuoj-next
cp .env.example .env
# 编辑 .env，至少更换数据库密码、AUTH_SECRET 和 AI_CONFIG_SECRET
docker compose up --build -d
```

基础站点默认位于 `http://localhost:8088`，MySQL 开发端口为 `3307`。基础服务验证通过后，再显式启动有特权要求的判题服务：

```bash
docker compose --profile judge up --build -d judge-agent
```

首次空数据库没有预置弱口令管理员。先通过页面注册账号，再执行：

```sql
INSERT INTO privilege(user_id, rightstr, valuestr, defunct)
VALUES ('你的用户名', 'super_administrator', 'true', 'N');
```

重新登录后角色生效。超级管理员可以在网站“其他 → 管理员列表”中添加普通管理员。

## 不使用 Docker 的开发方式

后端需要 Java 17 与 Maven 3.9，前端需要 Node.js 20.19+。

```bash
# WSL 终端 1
cd jisuoj-next/backend
DB_URL='jdbc:mysql://localhost:3306/jol?...' mvn spring-boot:run

# Windows PowerShell 或已安装 Node 的 WSL 终端 2
cd jisuoj-next/frontend
npm ci
npm run dev
```

Vite 会把 `/api` 代理到 `http://localhost:8080`。若后端地址不同，可设置 `VITE_DEV_API_TARGET`。
单独在 WSL 启动后端时，题目测试数据默认写入 `/home/judge/data`，需要确保运行后端的用户能穿过 `/home/judge` 并写入 `data` 目录，否则导入会失败。可通过 `JUDGE_DATA_DIR` 指定判题器实际使用的其他目录。Docker Compose 使用共享的 `/home/judge/data` 卷。

若本机沿用 HUSTOJ 的 `/home/judge/data` 且后端用户无权限，可安装 `acl`，并只对运行后端的用户授予所需权限（把 `wicky` 换成实际用户名）：

```bash
sudo setfacl -m u:wicky:--x /home/judge
sudo setfacl -m u:wicky:rwx /home/judge/data
```

## 验证

```bash
cd backend && mvn test
cd ../frontend && npm ci && npm test && npm run build
```

架构取舍见 [ARCHITECTURE.md](docs/ARCHITECTURE.md)，分阶段上线步骤见 [DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 技术版本依据

- Spring Boot 3.5.x，Java 17；选择 3.x 是为了稳定的 Jackson 2 与现有依赖生态兼容。
- Vue 3 + TypeScript + Vite，采用 Vue 官方推荐的单文件组件与类型检查工具链。
- MySQL 8.4 LTS。

参考：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Vue TypeScript 指南](https://vuejs.org/guide/typescript/overview)、[MySQL 8.4 发布模型](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)。
