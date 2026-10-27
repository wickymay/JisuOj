# 国内模型协作生成测试数据

管理中心的“智能体配置”统一保存 DeepSeek、Qwen、GLM、Kimi 的接口地址、模型和 API Key。密钥沿用原有 AES-GCM 加密及 `AI_CONFIG_SECRET`；状态响应没有密钥。原 DeepSeek 仍使用 `jisu_ai_config.id=1`，新增三家分别使用 2、3、4，无须修改既有 Flyway 迁移或重新填写 DeepSeek。

只有超级管理员能保存或删除配置；管理员可查看状态。留空密钥保留对应服务商原密钥，更换接口地址必须重新填写密钥。模型默认值只是可编辑的起点，请按实际账户可用模型填写。

## 使用方式

1. 在“智能体配置”填写 Qwen、DeepSeek、GLM；需要最终复核时再配置 Kimi。
2. 启用下文的隔离执行器。未启用、不可达或密钥不匹配时，任务会在模型调用前拒绝启动。
3. 打开“题目管理 → 生成测试数据”，填写题面、输入输出、所有数据范围和样例；也可输入站内题号载入题面。数据范围需要人工补全。
4. 选择生成组数和随机种子，按需勾选 Kimi。等待任务完成，审阅输入输出预览、程序和提示，再下载 ZIP。
5. “新增题目”页面尾部也提供生成区：补全数据范围，生成后点击“采用测试数据，随题目保存”，再点击“保存题目与测试数据”，题面、程序及 ZIP 通过同一发布接口一起保存。
6. 题目拉取在完成解析后，第 2–4 步的页面尾部持续提供同一组件。“采用测试数据并进入导入”会附加 ZIP 并带入标准解、生成器、校验器。最后仍需人工确认题目导入。

题目拉取会自动暂存来源、题面、程序、当前步骤和已选 ZIP；数据按登录账号保存在当前浏览器，保留 24 小时。切换页面或刷新后自动恢复，也可点击“暂存草稿”或“清除暂存”。导入成功后清除草稿；恢复后需要重新确认导入。浏览器禁止持久存储或容量不足时，页面会提示仅能保留本次打开网站期间的内容。

采用生成数据后修改题面、输入输出、样例、约束、评测类型或参考程序，会自动取消采用旧 ZIP；修改来源或是否隐藏不影响生成数据。新增题目、题目拉取和独立生成页分别恢复各自的任务查询，避免串用其他页面的任务。

生成包包含编号 `.in/.out`、`reference.cpp`、`independent.cpp`、`generator.cpp`、`validator.cpp` 和 `manifest.json`。现有 ZIP 导入器只读取数据文件，其他文件用于复查。不会自动覆盖已有题目的测试数据，也不会自动发布题目。

## 工作流程

- Qwen：覆盖计划与 C++17 生成器，通过 `argv[1]=编号（从0开始）`、`argv[2]=种子` 生成一组输入。
- DeepSeek：标准解；管理员已有参考代码作为辅助资料。
- GLM：在看不到 DeepSeek 标准解的情况下独立编写完整解法和输入校验器，提供最多 3 组反例及可选典型错误解。
- Kimi（可选）：审阅两份解法、生成器和校验器；明确否决会终止任务。
- 执行器：隔离编译，每次程序运行使用新的容器；检查生成器第一组的种子复现、输入合法性、题面样例、全部数据的双解对拍，以及标准解第一组重复运行的一致性。典型错误解用于统计检出组数。

任一必需程序编译或运行失败、输入被校验器拒绝、答案不一致、样例不通过，整个任务失败且不提供 ZIP。典型错误解未提供、编译失败或未被检出会在结果中明确提示。模型复核与双解一致不能证明题目数据完全正确，正式使用前仍需人工审查。

### GLM 请求兼容与错误排查

GLM-5.3 始终启用思考；发送 `thinking.type=disabled` 会被拒绝，返回 HTTP 400、智谱业务码 1210。适配器不再强制关闭 GLM 思考，对 GLM-5.2/5.3 使用 `reasoning_effort=high`。

GLM 请求显式传入 `response_format={"type":"json_object"}`，使模型使用官方 JSON 模式，而不只依赖提示词。解析器也接受单个 JSON 对象外的 Markdown 或说明文字，按字符串边界处理 C++ 代码里的引号、转义和花括号，不改写程序内容；多个对象、非对象、非法或不完整 JSON 会拒绝。开发环境已验证小型三程序请求可返回 HTTP 200 和可解析的 JSON；实际可用模型以部署账户授权为准。参考：[智谱结构化输出](https://docs.bigmodel.cn/cn/guide/capabilities/struct-output)。

网络连接失败、TLS 失败、240 秒请求超时、接口返回非 JSON、最终内容为空、只有思考过程、输出截断和最终 JSON 无效分别提示。后端 `AI call failed` 日志仅记录服务商、处理阶段、HTTP 状态、字节数、结束原因、耗时和异常类型，不记录题面、生成内容、异常正文或 API Key。历史任务保存的是失败当时的提示；更新后需要新建生成任务验证。

界面错误会显示归一化原因和智谱业务码，不回显上游原始错误正文或密钥。400/1210 表示参数不兼容，400/1211 表示模型名称不被识别，429/1113 才表示账户欠费；其他 429 可能表示调用频率、套餐或用量限制。排查时以业务码为准，不应把所有 HTTP 400 都理解为余额问题。参考：[智谱错误码](https://docs.bigmodel.cn/cn/api/api-code)。

## rootless Podman 执行器

### Linux / WSL 开发服务安装

安装前先准备下文所列的 rootless Podman 环境和一个运行后端的普通 Linux 账户。安装脚本必须显式指定该账户，不依赖原开发者的用户名：

```bash
cd /path/to/JisuOj/jisuoj-next
sudo python3 scripts/install-testdata-runner-wsl.py --backend-user "$USER"
```

脚本建立禁止交互登录的 `jisuoj-runner` 账户，将源码安装到 `/opt/jisuoj-testdata`，并写入 `jisuoj-testdata-runner.service` 用户服务。通信密钥分别保存在执行账户的 `~/.config/jisuoj/runner.env` 和后端账户的 `~/.config/jisuoj/testdata.env`，文件权限为 `0600`；真实密钥不在仓库中。服务默认只监听 `127.0.0.1:8766`。

随后以执行器账户构建工具链镜像，再启动或重启用户服务。用户 UID 由系统分配，请动态读取：

```bash
runner_uid=$(id -u jisuoj-runner)
sudo -u jisuoj-runner env XDG_RUNTIME_DIR=/run/user/$runner_uid \
  DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$runner_uid/bus \
  systemctl --user enable --now jisuoj-testdata-runner.service
sudo -u jisuoj-runner env XDG_RUNTIME_DIR=/run/user/$runner_uid \
  DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$runner_uid/bus \
  systemctl --user status jisuoj-testdata-runner.service
```

Spring Boot 和开发启动脚本会读取当前后端用户的 `~/.config/jisuoj/testdata.env`。开发环境已有后端进程时，`scripts/connect-testdata-backend-wsl.py` 可由该后端账户显式执行：先验证执行器健康，再保留环境并重启当前项目的开发后端，不打印数据库密码或模型密钥。该脚本不适用于容器化或生产服务管理。

更新执行器源码后需重建工具链镜像并重启对应用户服务。开发环境已通过固定 A+B 的容器与认证 HTTP 验收；发布副本的单元测试不替代实际部署环境中的 rootless 隔离验收。

### 新环境部署

执行器独立于 Web 进程，以**专用普通 Linux 用户**运行，只接受服务密钥认证的内部请求。它不使用 Docker 控制套接字，不执行主机 shell 命令，也不把生成程序或宿主机目录挂载进容器。不能作为 root 运行。

环境要求：Linux/WSL2、Python 3.10+、rootless Podman、cgroup v2 及该普通用户可用的 CPU/内存/PID 控制器。需按发行版的 Podman 文档安装并配置 `/etc/subuid`、`/etc/subgid`。生产环境使用独立执行主机及专用账户。

在执行器账户下构建工具链镜像：

```bash
cd /path/to/jisuoj-next/testdata-runner
podman info --format '{{.Host.Security.Rootless}}'  # 必须为 true
podman info --format '{{.Host.CgroupsVersion}}'    # 必须为 v2
podman build -f Dockerfile.sandbox -t jisuoj-testdata-sandbox:local .
# 默认 Debian 软件包源较慢时，可选用国内镜像，APT 签名校验保持开启：
podman build --build-arg DEBIAN_MIRROR=http://mirrors.tuna.tsinghua.edu.cn -f Dockerfile.sandbox -t jisuoj-testdata-sandbox:local .
```

镜像必须由管理员预先构建；运行程序时禁止拉取镜像。每个编译/运行容器使用无网络、只读根目录、非 root UID、移除全部 capabilities、禁止提权、1 CPU、512 MiB 内存、64 个 PID、受限 tmpfs。编译最多 35 秒，单程序运行最多 5 秒；每次执行位于独立容器，程序之间不共享工作目录。Podman 外层还限制总时间与输出大小；失败时清理容器。

生成一个至少 32 字符的随机 `TEST_DATA_RUNNER_TOKEN`，在后端和执行器中设置为同一值。不要把真实密钥写进仓库。

```bash
# 执行器进程
export TEST_DATA_RUNNER_TOKEN='替换为独立随机密钥，至少32字符'
export TEST_DATA_BIND=127.0.0.1
python3 server.py

# 另一个终端：同机 WSL 开发后端
export TEST_DATA_RUNNER_URL=http://127.0.0.1:8766
export TEST_DATA_RUNNER_TOKEN='与执行器相同的随机密钥'
cd ../backend
mvn spring-boot:run
```

执行器默认只监听本机。在容器化后端或跨主机部署中，显式配置后端可达的内部地址及执行器监听地址，并在可信内网或受保护隧道中通信；后端容器的 `127.0.0.1` 不是宿主机。Compose 已透传这两个环境变量，但不会启动持有宿主机控制权限的执行器容器。缺少必要 cgroup 权限时任务会失败，不降级为无限制主机执行。

### 接口与限制

- `GET /api/admin/test-data/capabilities`：服务商配置状态、执行器是否配置。
- `POST /api/admin/test-data/jobs`：`{draft,caseCount,seed,useKimi,extraPrompt}`，返回 202 和任务编号。
- `GET /api/admin/test-data/jobs/{id}`：阶段、进度、错误及成功结果预览。
- `GET /api/admin/test-data/jobs/{id}/zip`：仅成功且属于当前用户的任务可以下载。
- `GET /api/admin/ai-config/providers`：四家状态；`PUT/DELETE /api/admin/ai-config/{deepseek|qwen|glm|kimi}` 修改单家配置。原无后缀 DeepSeek 接口保留。

生成接口限管理员与题目编辑者；每人同时最多一个活动任务，全局最多两个工作线程、八个保留任务。成功/失败结果保留 30 分钟，后端重启会丢失内存任务。前端切页或刷新可恢复当前账户的任务查询及生成参数，包括仍在保留期的完成结果；已采用的 ZIP 随题目拉取草稿单独暂存，不受任务过期影响。生成 1–20 组，另外加入样例及最多 3 组反例并去重；单个输入或输出最多 256 KiB，总数据最多 16 MiB。当前仅支持确定性普通题，特殊判题、浮点多解和交互题需要专门 Checker 与人工数据流程。

### 验证

```bash
cd backend && mvn test
cd ../frontend && npm test && npm run build
cd ../testdata-runner && python3 -m unittest -v test_server.py test_execute.py
# 安装 rootless Podman 并构建镜像后验证真实隔离链路：
python3 smoke_test.py
```

Java 测试使用本地 HTTP 模拟模型接口；Python 流程测试使用确定性执行器替身。`test_execute.py` 只在 Linux 上编译运行固定的可信 C++ 测试程序，验证入口程序的编译、输出计算和限额；它不验证容器隔离。以上均不调用付费 API、不读取生产密钥。`smoke_test.py` 通过真实 rootless 容器执行固定 A+B 任务，可用于部署验收。

协议参考：[Qwen 兼容接口](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-chat-completions)、[DeepSeek 思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)、[Kimi 参数与接入](https://platform.kimi.ai/docs/get-your-api-key)、[Podman 容器参数](https://docs.podman.io/en/latest/markdown/podman-run.1.html)。
