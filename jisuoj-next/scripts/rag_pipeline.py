"""Prepare traceable RAG chunks, embed through configured Qwen MCP, and search."""

import argparse
import asyncio
import hashlib
import json
import os
import re
import shutil
import statistics
import sys
import time
import tomllib
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
COLLECTION = PROJECT / "output" / "RAG资料集"
SOURCE = COLLECTION / "embedding筛选" / "可embedding" / "embedding输入.jsonl"
DEFAULT_OUTPUT = COLLECTION / "向量化" / "qwen3-embedding-0.6b-v1"
CONFIG = Path.home() / ".codex" / "config.toml"
TARGET_TOKENS = 384
MAX_TOKENS = 640
OVERLAP_TOKENS = 64
DIMENSIONS = 1024
BOUNDARY = re.compile(r'[。！？!?；;](?:[”’」』）)]*)\s*|\n\s*\n')
LABELS = {"concept_explanation": "概念说明", "solution_analysis": "解题分析", "learning_guidance": "学习指导"}


def now():
    return datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds")


def sha_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def read_jsonl(path):
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8-sig").splitlines() if line.strip()]


def write_json(path, data):
    target = Path(path)
    temporary = target.with_name(target.name + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, target)


def write_jsonl(path, rows):
    with Path(path).open("w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")


def service_config():
    config = tomllib.loads(CONFIG.read_text(encoding="utf-8-sig"))["mcp_servers"]["qwen3-embedding"]
    if not config.get("enabled", True):
        raise RuntimeError("The configured qwen3-embedding MCP server is disabled")
    return config


def header(row):
    labels = [("书名", row["book_title"]), ("章节", row.get("chapter")),
              ("知识点", row.get("section")), ("题目", row.get("problem_title")),
              ("内容类型", LABELS.get(row["content_type"], row["content_type"]))]
    return "\n".join(f"{label}：{value.strip()}" for label, value in labels if value) + "\n\n"


def prepare(output):
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    from transformers import AutoTokenizer

    output.mkdir(parents=True, exist_ok=True)
    if (output / "manifest.json").exists():
        raise RuntimeError("This output is already prepared; use embed, or choose a new --output directory")
    config = service_config()
    model_path = Path(config["env"]["QWEN_EMBEDDING_MODEL_PATH"])
    tokenizer = AutoTokenizer.from_pretrained(str(model_path), local_files_only=True, trust_remote_code=False)
    parents = read_jsonl(SOURCE)
    assert len({row["id"] for row in parents}) == len(parents)
    chunks = []
    forced_splits = 0
    source_lines = {}

    for parent in parents:
        assert parent["embedding_eligible"] is True
        body = parent["body"]
        segments = parent["source_segments"]
        assert "\n\n".join(segment["text"] for segment in segments) == body
        prefix = header(parent)

        def count(text):
            return len(tokenizer.encode(text, add_special_tokens=True))

        def size(start, end):
            return count(prefix + body[start:end])

        spans = []
        cursor = 0
        for segment in segments:
            start = body.index(segment["text"], cursor)
            end = start + len(segment["text"])
            spans.append((start, end, segment))
            cursor = end

        def clipped_source(start, end):
            clipped = []
            for left, right, segment in spans:
                a, b = max(left, start), min(right, end)
                if a < b:
                    clipped.append({
                        **segment, "text": body[a:b],
                        "parent_body_char_start": a, "parent_body_char_end": b,
                        "segment_char_start": a - left, "segment_char_end": b - left,
                    })
            return clipped

        # Verify all selected source segments against their retained source file.
        primary_ref = next(ref for ref in parent["source_refs"] if ref["book_id"] == parent["book_id"])
        source_path = COLLECTION / primary_ref["source_file"]
        if source_path not in source_lines:
            assert sha_file(source_path) == primary_ref["source_text_sha256"]
            source_lines[source_path] = source_path.read_text(encoding="utf-8-sig").splitlines()
        for segment in segments:
            raw = "\n".join(source_lines[source_path][segment["line_start"] - 1:segment["line_end"]])
            assert segment["text"] in raw, parent["id"]

        # Paragraphs are atomic unless a paragraph alone exceeds the hard limit.
        atoms = []
        for start, end, _ in spans:
            if size(start, end) <= MAX_TOKENS:
                atoms.append((start, end))
                continue
            current = start
            boundaries = [start + match.end() for match in BOUNDARY.finditer(body[start:end])]
            while size(current, end) > MAX_TOKENS:
                low, high = current + 1, end
                while low < high:
                    middle = (low + high + 1) // 2
                    if size(current, middle) <= TARGET_TOKENS:
                        low = middle
                    else:
                        high = middle - 1
                valid = [value for value in boundaries if current < value <= low]
                cut = valid[-1] if valid else low
                if not valid:
                    forced_splits += 1
                assert current < cut and size(current, cut) <= MAX_TOKENS
                atoms.append((current, cut))
                current = cut
            if body[current:end].strip():
                atoms.append((current, end))

        packed = []
        for start, end in atoms:
            if packed:
                previous_start, previous_end = packed[-1]
                if size(previous_start, end) <= TARGET_TOKENS or (
                    size(previous_start, previous_end) < 128 and size(previous_start, end) <= MAX_TOKENS
                ):
                    packed[-1] = (previous_start, end)
                    continue
            packed.append((start, end))
        if len(packed) > 1 and size(*packed[-1]) < 96 and size(packed[-2][0], packed[-1][1]) <= MAX_TOKENS:
            packed[-2:] = [(packed[-2][0], packed[-1][1])]

        covered = bytearray(len(body))
        for index, (start, end) in enumerate(packed):
            core = body[start:end]
            for position in range(start, end):
                covered[position] = 1
            context = ""
            context_start = start
            if index:
                previous_start, previous_end = packed[index - 1]
                # Overlap only complete trailing sentences from the same parent.
                candidates = [previous_start] + [
                    previous_start + match.end()
                    for match in BOUNDARY.finditer(body[previous_start:previous_end])
                ]
                for candidate in candidates:
                    suffix = body[candidate:previous_end].strip()
                    if suffix and count(suffix) <= OVERLAP_TOKENS and count(prefix + suffix + "\n\n" + core) <= MAX_TOKENS:
                        context = suffix
                        context_start = candidate
                        break
            text = prefix + (context + "\n\n" if context else "") + core
            tokens = count(text)
            assert tokens <= MAX_TOKENS
            basis = next((field for field in ("section", "problem_title", "chapter") if parent.get(field)), None)
            chunks.append({
                "id": f"{parent['id']}-c{index + 1:02d}-{sha_text(text)[:10]}",
                "vector_row": len(chunks), "parent_id": parent["id"],
                "chunk_index": index, "parent_chunk_count": len(packed),
                "knowledge_path": [parent[field] for field in ("book_title", "chapter", "section", "problem_title") if parent.get(field)],
                "knowledge_point": parent[basis] if basis else "未标注小节",
                "knowledge_point_basis": f"source_{basis}" if basis else "unavailable",
                **{field: parent.get(field) for field in (
                    "book_id", "book_title", "chapter", "section", "problem_title", "topics",
                    "content_type", "contains_solution", "pdf_page_start", "pdf_page_end",
                    "human_reviewed", "approved_for_rag", "accuracy_status", "selection_status",
                )},
                "text": text, "body": core, "context_before": context,
                "core_body_char_start": start, "core_body_char_end": end,
                "context_body_char_start": context_start if context else None,
                "token_count": tokens, "text_sha256": sha_text(text),
                "source_refs": parent["source_refs"],
                "source_segments": clipped_source(start, end),
                "context_source_segments": clipped_source(context_start, packed[index - 1][1]) if context else [],
                "embedding_eligible": True,
            })
        assert all(covered[i] or character.isspace() for i, character in enumerate(body)), parent["id"]

    assert {chunk["parent_id"] for chunk in chunks} == {parent["id"] for parent in parents}
    shutil.copy2(SOURCE, output / "parents.jsonl")
    write_jsonl(output / "chunks.jsonl", chunks)
    lengths = sorted(chunk["token_count"] for chunk in chunks)
    counts = Counter(chunk["book_id"] for chunk in chunks)
    manifest = {
        "created_at": now(), "status": "chunked_pending_embedding",
        "source_file": str(SOURCE), "source_sha256": sha_file(SOURCE),
        "chunks_file": "chunks.jsonl", "chunks_sha256": sha_file(output / "chunks.jsonl"),
        "source_books": len(counts), "source_parents": len(parents),
        "source_body_chars": sum(len(parent["body"]) for parent in parents),
        "source_pages": len({(ref["book_id"], ref["pdf_page"]) for parent in parents for ref in parent["source_refs"]}),
        "chunks": len(chunks), "chunks_per_book": dict(sorted(counts.items())),
        "strategy": {
            "name": "section_paragraph_sentence_v1", "target_tokens": TARGET_TOKENS,
            "hard_max_tokens_including_context": MAX_TOKENS, "overlap_max_tokens": OVERLAP_TOKENS,
            "overlap_unit": "complete trailing sentences within the same parent only",
            "cross_page_merge": False, "merge_across_filtered_gaps": False,
            "forced_unicode_boundary_splits": forced_splits,
        },
        "token_counts": {"min": lengths[0], "median": statistics.median(lengths),
                         "p95": lengths[int((len(lengths) - 1) * .95)], "max": lengths[-1], "sum": sum(lengths)},
        "model": {"name": "Qwen3-Embedding-0.6B", "path": str(model_path),
                  "dimensions": DIMENSIONS, "document_role": "document", "query_role": "query",
                  "normalized": True, "similarity": "cosine / normalized inner product",
                  "config_sha256": sha_file(model_path / "config.json"),
                  "prompt_config": json.loads((model_path / "config_sentence_transformers.json").read_text(encoding="utf-8"))},
        "embedding": {"status": "pending", "rows_completed": 0},
        "source_quality": {"human_reviewed": False, "approved_for_production_rag": False,
                           "note": "Inherits automatic screening flags; vectorization is not an OCR accuracy approval."},
        "preparation_checks": {"all_parents_covered": True, "all_nonwhitespace_body_characters_preserved": True,
                               "all_source_segments_verified": True, "source_hashes_verified": len(source_lines),
                               "all_chunks_within_token_limit": True,
                               "duplicate_embedding_texts": len(chunks) - len({chunk["text_sha256"] for chunk in chunks})},
    }
    write_json(output / "manifest.json", manifest)
    print(json.dumps({"output": str(output), "parents": len(parents), "chunks": len(chunks),
                      "token_counts": manifest["token_counts"], "checks": manifest["preparation_checks"]}, ensure_ascii=False), flush=True)


def tool_payload(result):
    if result.isError:
        raise RuntimeError(str(result.content))
    return result.structuredContent if result.structuredContent is not None else json.loads(result.content[0].text)


def verify_inputs(output):
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    assert sha_file(output / "chunks.jsonl") == manifest["chunks_sha256"], "Chunk file changed"
    assert sha_file(output / "parents.jsonl") == manifest["source_sha256"], "Parent snapshot changed"
    assert sha_file(manifest["source_file"]) == manifest["source_sha256"], "Selected source file changed"
    return manifest, read_jsonl(output / "chunks.jsonl")


async def with_session(output, callback):
    from mcp import ClientSession, StdioServerParameters
    from mcp.client.stdio import stdio_client

    config = service_config()
    parameters = StdioServerParameters(command=config["command"], args=config.get("args", []),
                                      env={**os.environ, **config.get("env", {})})
    with (output / "embedding-runtime.log").open("a", encoding="utf-8") as log:
        async with stdio_client(parameters, errlog=log) as streams:
            async with ClientSession(*streams, read_timeout_seconds=timedelta(seconds=360)) as session:
                await session.initialize()
                try:
                    return await callback(session)
                finally:
                    await session.call_tool("release_model", {})


PROBES = [
    ("并查集的路径压缩和按秩合并有什么作用？", r"并查集|路径压缩"),
    ("怎样用树状数组维护区间和，支持修改和查询？", r"树状数组|Fenwick"),
    ("动态规划的状态应该如何定义，状态转移如何确定？", r"动态规划|状态转移"),
    ("优先队列与普通队列有何区别，适合处理什么问题？", r"优先队列|priority.queue"),
    ("KMP字符串匹配的前缀或失配信息有什么作用？", r"KMP|失配|模式串"),
    ("Dijkstra算法适用于什么边权条件，如何求最短路径？", r"Dijkstra|Dijsktra|非负权|最短路"),
    ("编程竞赛怎样提高读题能力，向队友清楚转述题意？", r"读题|转述"),
    ("调试程序时怎样设计测试、定位程序中的错误？", r"调试|Debug|debug"),
]


def ranked_results(vectors, query_vector, chunks, top_k=5, exclude_solutions=False):
    import numpy as np
    scores = np.asarray(vectors @ query_vector, dtype=np.float32)
    allowed = [i for i, chunk in enumerate(chunks) if not exclude_solutions or not chunk["contains_solution"]]
    indices = sorted(allowed, key=lambda i: float(scores[i]), reverse=True)[:top_k]
    return [{"rank": rank + 1, "chunk_id": chunks[i]["id"], "score": float(scores[i]),
             "book_title": chunks[i]["book_title"], "knowledge_path": chunks[i]["knowledge_path"],
             "pdf_page_start": chunks[i]["pdf_page_start"], "pdf_page_end": chunks[i]["pdf_page_end"],
             "contains_solution": chunks[i]["contains_solution"], "approved_for_rag": chunks[i]["approved_for_rag"],
             "source_refs": chunks[i]["source_refs"], "text": chunks[i]["text"]}
            for rank, i in enumerate(indices)]


async def embed(output, np):
    manifest, chunks = verify_inputs(output)
    shape = (len(chunks), DIMENSIONS)
    final_path, partial_path = output / "embeddings.npy", output / "embeddings.partial.npy"
    checkpoint = output / "checkpoint.json"
    if final_path.exists():
        validate(output, np)
        print("Vectors already complete and validated", flush=True)
        return
    if checkpoint.exists():
        state = json.loads(checkpoint.read_text(encoding="utf-8"))
        assert state["chunks_sha256"] == manifest["chunks_sha256"]
        vectors = np.lib.format.open_memmap(partial_path, mode="r+")
        assert vectors.shape == shape
        completed = state["rows_completed"]
    else:
        vectors = np.lib.format.open_memmap(partial_path, mode="w+", dtype=np.float32, shape=shape)
        vectors[:] = np.nan
        vectors.flush()
        completed = 0
    manifest["status"] = "embedding_running"
    manifest["embedding"].update(status="running", rows_completed=completed)
    write_json(output / "manifest.json", manifest)
    print(f"Starting configured Qwen MCP; {completed}/{len(chunks)} rows already complete", flush=True)
    started = time.perf_counter()

    async def run(session):
        nonlocal completed
        info = tool_payload(await session.call_tool("model_info", {}))
        assert Path(info["model_path"]).resolve() == Path(manifest["model"]["path"]).resolve()
        assert info["max_tokens_per_text"] >= MAX_TOKENS
        for start in range(completed, len(chunks), 16):
            end = min(start + 16, len(chunks))
            result = tool_payload(await session.call_tool("embed_texts", {
                "texts": [chunk["text"] for chunk in chunks[start:end]],
                "role": "document", "dimensions": DIMENSIONS,
            }))
            batch = np.asarray(result["embeddings"], dtype=np.float32)
            assert batch.shape == (end - start, DIMENSIONS) and np.isfinite(batch).all()
            assert np.max(np.abs(np.linalg.norm(batch, axis=1) - 1)) < 1e-4
            vectors[start:end] = batch
            vectors.flush()
            completed = end
            state = {"updated_at": now(), "chunks_sha256": manifest["chunks_sha256"],
                     "rows_completed": completed, "rows_total": len(chunks), "device": result["device"]}
            write_json(checkpoint, state)
            manifest["embedding"].update(rows_completed=completed, device=result["device"])
            write_json(output / "manifest.json", manifest)
            print(f"Embedded {completed}/{len(chunks)}; device={result['device']}; elapsed={time.perf_counter()-started:.1f}s", flush=True)
        queries = [query for query, _ in PROBES]
        query_result = tool_payload(await session.call_tool("embed_texts", {
            "texts": queries, "role": "query", "dimensions": DIMENSIONS,
        }))
        query_vectors = np.asarray(query_result["embeddings"], dtype=np.float32)
        reports = []
        for index, (query, expected) in enumerate(PROBES):
            results = ranked_results(vectors, query_vectors[index], chunks)
            matches = [entry["rank"] for entry in results if re.search(expected, entry["text"], re.IGNORECASE)]
            reports.append({"query": query, "expected_topic_pattern": expected,
                            "first_topic_match_rank_in_top5": matches[0] if matches else None,
                            "results": results})
        write_json(output / "retrieval-smoke-tests.json", {
            "created_at": now(), "purpose": "Operational retrieval examples, not a measured relevance benchmark",
            "query_role": "query", "document_role": "document", "tests": reports,
        })
        manifest["embedding"]["model_info"] = tool_payload(await session.call_tool("model_info", {}))

    await with_session(output, run)
    assert completed == len(chunks)
    assert sha_file(manifest["source_file"]) == manifest["source_sha256"]
    vectors.flush()
    del vectors
    os.replace(partial_path, final_path)
    manifest["status"] = "vectorized_development_index"
    manifest["embedding"].update(status="complete", completed_at=now(), rows_completed=completed,
                                  file="embeddings.npy", dtype="float32", shape=list(shape),
                                  sha256=sha_file(final_path), transport="configured MCP stdio embed_texts",
                                  model_weights_sha256=sha_file(Path(manifest["model"]["path"]) / "model.safetensors"),
                                  elapsed_seconds=round(time.perf_counter() - started, 2))
    write_json(output / "manifest.json", manifest)
    validate(output, np)


def validate(output, np):
    manifest, chunks = verify_inputs(output)
    vectors = np.load(output / "embeddings.npy", mmap_mode="r", allow_pickle=False)
    assert vectors.shape == (len(chunks), DIMENSIONS) and vectors.dtype == np.float32
    assert np.isfinite(vectors).all()
    norms = np.linalg.norm(vectors, axis=1)
    assert np.max(np.abs(norms - 1)) < 1e-4
    assert sha_file(output / "embeddings.npy") == manifest["embedding"]["sha256"]
    assert [chunk["vector_row"] for chunk in chunks] == list(range(len(chunks)))
    parents = {parent["id"]: parent for parent in read_jsonl(output / "parents.jsonl")}
    assert set(parents) == {chunk["parent_id"] for chunk in chunks}
    for chunk in chunks:
        parent = parents[chunk["parent_id"]]
        assert chunk["body"] == parent["body"][chunk["core_body_char_start"]:chunk["core_body_char_end"]]
        assert sha_text(chunk["text"]) == chunk["text_sha256"]
        assert chunk["pdf_page_start"] == chunk["pdf_page_end"]
        assert chunk["human_reviewed"] == parent["human_reviewed"]
        assert chunk["approved_for_rag"] == parent["approved_for_rag"]
    probes = json.loads((output / "retrieval-smoke-tests.json").read_text(encoding="utf-8"))["tests"]
    report = {"checked_at": now(), "status": "passed", "vector_rows": len(chunks), "dimensions": DIMENSIONS,
              "dtype": "float32", "finite_values": True, "norm_min": float(norms.min()),
              "norm_max": float(norms.max()), "all_parents_represented": len(parents),
              "source_and_chunk_hashes_unchanged": True, "vector_checksum_verified": True,
              "vector_row_alignment_verified": True, "source_quality_flags_preserved": True,
              "retrieval_smoke_queries": len(probes),
              "smoke_queries_with_topic_match_in_top5": sum(test["first_topic_match_rank_in_top5"] is not None for test in probes),
              "note": "Technical validation and topic smoke tests do not constitute an OCR or retrieval accuracy audit."}
    write_json(output / "validation.json", report)
    print(json.dumps(report, ensure_ascii=False), flush=True)


async def search(output, query, top_k, exclude_solutions, np):
    manifest, chunks = verify_inputs(output)
    if manifest["embedding"]["status"] != "complete":
        raise RuntimeError("Embedding is not complete")
    vectors = np.load(output / "embeddings.npy", mmap_mode="r", allow_pickle=False)

    async def run(session):
        result = tool_payload(await session.call_tool("embed_texts", {
            "texts": [query], "role": "query", "dimensions": DIMENSIONS,
        }))
        query_vector = np.asarray(result["embeddings"][0], dtype=np.float32)
        print(json.dumps({"query": query, "results": ranked_results(
            vectors, query_vector, chunks, top_k, exclude_solutions)}, ensure_ascii=False, indent=2), flush=True)

    await with_session(output, run)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "embed", "validate", "search"])
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--query")
    parser.add_argument("--top-k", type=int, default=5, choices=range(1, 21))
    parser.add_argument("--exclude-solutions", action="store_true")
    args = parser.parse_args()
    output = args.output.resolve()
    if args.command == "prepare":
        prepare(output)
        return
    # Import native NumPy before starting MCP's Windows input-reader threads.
    import numpy as np
    if args.command == "embed":
        asyncio.run(embed(output, np))
    elif args.command == "validate":
        validate(output, np)
    elif args.command == "search":
        if not args.query:
            parser.error("search requires --query")
        asyncio.run(search(output, args.query, args.top_k, args.exclude_solutions, np))


if __name__ == "__main__":
    main()
