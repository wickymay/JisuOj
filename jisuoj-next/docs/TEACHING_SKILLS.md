# AI 教学技能包

问 AI 在原聊天模型和本地 RAG 之外，加载项目自带的 `competitive-programming-tutor` v1.0.0。技能提供教学步骤、算法适用条件、分级提示、反例和调试方法；RAG 继续检索具体书页。技能正文作为项目维护的系统教学配置，检索摘录仍放在当前用户提问的参考资料中。

## 位置和结构

可维护的原始技能包位于：

```text
backend/src/main/resources/teaching-skills/competitive-programming-tutor
```

`SKILL.md` 定义共用教学流程；`chapters/` 包含建模、基础、搜索、贪心与二分、DP、图论、数据结构、字符串、数学、几何、调试、训练 12 个专题；`glossary.md`、`patterns.md`、`cheatsheet.md` 提供术语和方法；`examples/verified_algorithms.hpp` 是独立重写的 C++17 示例。`manifest.json` 管理专题关键词及内容哈希，`sources.json` 记录九本书的作者、原稿哈希、PDF 页数和选用内容的图像回核范围。

打包副本位于 `output/teaching-skills/competitive-programming-tutor-v1.0.0.zip`。包内顶层目录含标准 `SKILL.md`，也可解压到兼容代理的技能目录。项目运行时直接使用后端资源中的原始技能包，不读取 ZIP，也不依赖本机 Codex 的全局安装。

## 实际调用

`TeachingSkillService` 启动时验证共用正文和 12 个专题的 SHA-256，并缓存正文。按当前问题、最近一个有内容的问题、题面摘要和代码附件选择最多两个专题；连续的“继续”会回溯到原主题，当前问题权重更高。没有命中时加载建模专题，有代码时提高调试专题的优先级。关键词选择便于审查和维护，隐含主题或多跳问题仍可能需要改善关键词或显式补充上下文。

每次系统教学上下文最多 12000 个字符，按完整专题装入，不在公式或代码中间截断；不会把九本全文反复发送给聊天模型。原对话顺序、题目权限检查和当前学生提交记录权限保持现有流程。

默认逐级提示。明确请求完整解法时允许完整讲解，并允许 RAG 召回题解类段落；“不要完整代码，只要提示”等否定请求保持引导模式。继续追问时保留上一轮的主题和明确的完整解法请求。意图识别是保守的规则匹配，不是对任意自然语言的完美分类。

登录后的 `GET /api/ai/teach/status` 返回 `teachingSkill`，包含 `ready/disabled`、版本、专题数和来源书数。`POST /api/ai/teach` 的回答返回本次使用的 `teachingSkill.topics` 和 `mode`。问 AI 页面显示“教学方法已启用 · 12 个专题”，每条回答标出“逐步引导/完整讲解”及本次专题；该标记表示实际装入模型请求的内容，不表示回答已被人工评分。

## 资料处理与边界

本次使用 book-to-skill 的 technical/study 流程处理 `output/RAG资料集/正文资料` 中的九份完整 TXT，合计 3570 个连续 PDF 页码段。提取器处理约 383 万字符，估算约 159 万 token；这是语料规模估算，不是本次模型实际用量或账单。TXT 是已有转写稿，`technical` 模式不会恢复已经丢失的表格、图像或版式。

采用 book-to-skill 的大型文档按需查询方式建立全文主题索引，核对全部输入哈希和页码连续性，按主题阅读相关段落。对实际选用的核心内容视觉回核 12 个 PDF 页，覆盖全部九本来源。二分、背包倒序、路径压缩、KMP 回退、gcd 和叉积等概念的核对范围逐项写入 `sources.json`。实现和教学用例经过重写，补充条件或纠正泛化措辞时明确说明。

这不是逐页、逐题完整转换；未对 3570 页全文进行独立准确率验收，也未宣称所有原书代码均可编译或通过评测。转写标记仍待复核的其他题解、公式和图片不得自动升级为权威结论。分级提示和路由规则是针对项目的教学设计，不冒称九本作者共有的原始术语。代码、公式和例题来源可回溯，但只引用当前实际提供的 `[资料N]` RAG 编号。

完整提取中间数据、原始书籍正文、扫描页、OCR 转写稿和 RAG 知识分片均不在本仓库中。随源码提供的是经提炼的方法说明、来源元数据和重写的算法示例；这些资源随 JAR 打包。需要增量维护来源时，应另外准备有使用权限的原始资料和中间数据。

## 配置与维护

默认 `AI_TEACHING_SKILLS_ENABLED=true`。设为 `false` 后重启后端，可关闭技能提示而继续使用原问答和 RAG。Docker 构建会把 `src/main/resources` 中的包一起放入 JAR，无需额外挂载路径。

修改技能正文后更新版本和来源记录，然后重建清单、校验并导出：

```bash
python3 scripts/build_teaching_catalog.py
python3 scripts/validate_teaching_skill.py --zip
cd backend && mvn test
cd ../frontend && npm run build
```

清单重建依赖本地提取出的 `metadata.json` 与 `source-index.json`；部署和普通校验不依赖这些中间文件。完整稿变更时先用 book-to-skill 再提取，并运行 `python3 scripts/teaching_sources.py index`；该命令会重新校验文本哈希和连续页码。确认新的来源版本后再维护原稿清单，不能绕过哈希检查。

重新生成后还应运行已安装的 book-to-skill `tools/scan_generated_skill.py` 和 Codex `skill-creator/scripts/quick_validate.py`。新增资料不得直接变成系统指令，需完成提炼和回核后再纳入包。

核心示例可在项目根目录用 GCC/Clang 运行独立验证：

```bash
mkdir -p output/teaching-skills
g++ -std=c++17 -O2 -Wall -Wextra \
  -Ibackend/src/main/resources/teaching-skills/competitive-programming-tutor/examples \
  backend/src/test/cpp/teaching_algorithms_test.cpp \
  -o output/teaching-skills/teaching_algorithms_test
output/teaching-skills/teaching_algorithms_test
```

验证覆盖穷举背包、标准库二分与 gcd、朴素重叠匹配、Floyd-Warshall 对照最短路、显式集合对照并查集，以及模乘和方向计算的大数边界。这些检查验证技能内示例，不替代学生提交或整道原书例题的评测。
