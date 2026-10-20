# 上游与第三方组件

JISUOJ 是在 HUSTOJ 基础上的二次开发及渐进重构。

- 上游项目：https://github.com/zhblue/hustoj
- 本地基础提交：`91b50c86aac788ce1ff70beac462ae5f35180e5a`
- 发布整理日期：2026-10-20
- 修改范围包括新版 Vue/Spring Boot 服务、题目导入、AI 教学、用户社交、旧站页面与管理后台，以及判题兼容调整。

根目录 `LICENSE` 保留 HUSTOJ 随附的 GNU GPL v2 许可证。源文件中的版权声明继续有效。上游说明保存在 `docs/upstream/HUSTOJ_README.md`。

`trunk/web` 中的第三方编辑器、主题、JavaScript 库、字体及图片保留原文件和许可声明；`trunk/core/sim/sim_3_01/LICENSE.txt` 记录 SIM 的许可证。新版通过 npm、Maven 和 pip 引入的依赖按各自许可证分发，依赖及版本见相应的 manifest/lockfile。

学校标识与社群图片来自现有项目资源，单独用于其他品牌或站点时应换成自己的素材。书籍原文、扫描页、OCR 转写、知识检索分片和模型权重均不包含在此源码发布中。AI 教学资源提供方法说明、来源信息和重写的示例，其范围见 `jisuoj-next/docs/TEACHING_SKILLS.md`。
