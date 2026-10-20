# 可选的本地知识检索

AI 教学可以检索本地 Qwen3 Embedding 知识库，再将相关片段和来源页码提供给已配置的聊天模型。仓库提供检索服务源码，不提供书籍、转写稿、知识分片、向量或模型权重。默认 AI_RAG_ENABLED=false，普通问答和内置教学方法可独立使用。

## 准备资料

只有在已准备有使用权限的知识库和匹配的模型后才启用此功能。现有服务使用以下格式：

- chunks.jsonl：每行一个分片，包含 id、parent_id、vector_row、book_title、text、contains_solution、pdf_page_start、pdf_page_end，可选 chapter、section。
- embeddings.npy：与分片顺序一致的 N × 1024、float32、L2 归一化向量。
- manifest.json：分片、向量、模型权重和配置的哈希，以及编码提示配置。完整字段以 rag/service.py 的 KnowledgeIndex、load_encoder 为准。

默认索引目录为项目下 output/RAG资料集/向量化/qwen3-embedding-0.6b-v1，可设置 RAG_INDEX_DIR。默认模型目录为 models/Qwen3-Embedding-0.6B，可设置 RAG_MODEL_DIR。资料和模型目录不应提交到代码仓库；模型必须与生成向量时的权重和配置完全匹配。

scripts/rag_pipeline.py 等工具保留原有资料处理流程，输入清单面向原开发资料集；使用其他资料需维护相应输入和 manifest，不能仅复制任意文本后直接启动。

## 本机运行

在 Linux/WSL 中创建独立 Python 虚拟环境，安装匹配的 PyTorch CPU 版本和 rag/requirements.txt 中的依赖，然后设置：

```bash
export RAG_INDEX_DIR=/absolute/path/to/index
export RAG_MODEL_DIR=/absolute/path/to/Qwen3-Embedding-0.6B
export RAG_BIND_HOST=127.0.0.1
export RAG_SERVICE_TOKEN=your-local-service-token
python3 rag/service.py
```

默认端口为 8765。后端配置 AI_RAG_ENABLED=true、AI_RAG_BASE_URL=http://127.0.0.1:8765 和相同的 AI_RAG_TOKEN。scripts/dev-rag-wsl.sh 可使用 .venv-rag 或 RAG_PYTHON 指定的 Python 运行。

## Docker Compose

把知识库放到 Compose 声明的索引目录，在 .env 中设置 Linux 可读的 RAG_MODEL_HOST_DIR、AI_RAG_ENABLED=true 和 AI_RAG_TOKEN，然后执行：

```bash
docker compose --profile rag up --build -d
```

服务在容器网络中使用 http://rag:8765，模型与索引只读挂载。通过 docker compose logs rag 检查加载情况。

默认返回最多 3 个不同原片段，相似度阈值 0.45。检索失败时继续调用聊天模型，并标明没有引用本地资料。其他配置包括 AI_RAG_TOP_K、AI_RAG_MIN_SCORE、AI_RAG_TIMEOUT、RAG_DEVICE。

## 测试

检索服务单元测试使用临时生成的合成向量和示例分片，不依赖私人资料、联网下载或模型推理。安装 NumPy 后运行：

```bash
python3 -m unittest discover -s rag -p 'test_*.py' -v
```

真实模型和自建语料需另行检查 /health 和 POST /search；单元测试通过不表示真实检索资料质量已经验收。