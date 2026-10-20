"""Persistent, local Qwen3 query encoding and retrieval for JISUOJ AI teaching."""

import argparse
import hashlib
import hmac
import json
import logging
import os
import threading
from functools import lru_cache
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

PROJECT = Path(__file__).resolve().parents[1]
DEFAULT_INDEX = PROJECT / "output/RAG资料集/向量化/qwen3-embedding-0.6b-v1"
DEFAULT_MODEL = PROJECT / "models/Qwen3-Embedding-0.6B"
log = logging.getLogger("jisuoj.rag")


def sha_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


class KnowledgeIndex:
    def __init__(self, directory, encoder):
        import numpy as np

        self.np = np
        self.directory = Path(directory)
        manifest = json.loads((self.directory / "manifest.json").read_text(encoding="utf-8"))
        if manifest["embedding"]["status"] != "complete":
            raise ValueError("Knowledge base vector generation is incomplete")
        for filename, expected in [("chunks.jsonl", manifest["chunks_sha256"]),
                                   ("embeddings.npy", manifest["embedding"]["sha256"])]:
            if sha_file(self.directory / filename) != expected:
                raise ValueError(f"Knowledge base checksum mismatch: {filename}")
        self.chunks = [json.loads(line) for line in (self.directory / "chunks.jsonl").read_text(encoding="utf-8").splitlines()]
        self.vectors = np.load(self.directory / "embeddings.npy", mmap_mode="r", allow_pickle=False)
        if self.vectors.shape != (len(self.chunks), 1024) or self.vectors.dtype != np.float32:
            raise ValueError("Invalid knowledge base vector shape or dtype")
        if [chunk["vector_row"] for chunk in self.chunks] != list(range(len(self.chunks))):
            raise ValueError("Knowledge base vector row alignment is invalid")
        if not np.isfinite(self.vectors).all() or np.max(np.abs(np.linalg.norm(self.vectors, axis=1) - 1)) > 1e-4:
            raise ValueError("Knowledge base vectors must be finite and normalized")
        self.encoder = encoder
        self.model_name = manifest["model"]["name"]
        self.dimensions = manifest["model"]["dimensions"]
        self.lock = threading.Lock()

    def health(self):
        return {"status": "ready", "model": self.model_name, "dimensions": self.dimensions,
                "chunks": len(self.chunks), "quality": "automatically_screened_development_material"}

    @lru_cache(maxsize=256)
    def query_vector(self, query):
        if not self.lock.acquire(timeout=10):
            raise TimeoutError("Query encoder is busy")
        try:
            vector = self.np.asarray(self.encoder(query), dtype=self.np.float32).reshape(-1)
            if vector.shape != (1024,) or not self.np.isfinite(vector).all():
                raise ValueError("Invalid query embedding")
            norm = self.np.linalg.norm(vector)
            if norm < 1e-12:
                raise ValueError("Empty query embedding")
            return vector / norm
        finally:
            self.lock.release()

    def search(self, query, top_k=3, min_score=0.45, exclude_solutions=True):
        if not isinstance(query, str) or not query.strip() or len(query) > 4000:
            raise ValueError("query must contain between 1 and 4000 characters")
        if type(top_k) is not int or not 1 <= top_k <= 5:
            raise ValueError("topK must be between 1 and 5")
        if type(min_score) not in (int, float) or not 0 <= min_score <= 1:
            raise ValueError("minScore must be between 0 and 1")
        if type(exclude_solutions) is not bool:
            raise ValueError("excludeSolutions must be boolean")
        scores = self.vectors @ self.query_vector(query.strip())
        results, seen_parents = [], set()
        for index in self.np.argsort(scores)[::-1]:
            chunk = self.chunks[int(index)]
            score = float(scores[index])
            if score < min_score:
                break
            if exclude_solutions and chunk["contains_solution"]:
                continue
            if chunk["parent_id"] in seen_parents:
                continue
            seen_parents.add(chunk["parent_id"])
            results.append({
                "chunkId": chunk["id"], "bookTitle": chunk["book_title"],
                "chapter": chunk.get("chapter") or "", "section": chunk.get("section") or "",
                "pageStart": chunk["pdf_page_start"], "pageEnd": chunk["pdf_page_end"],
                "text": chunk["text"], "score": score, "containsSolution": chunk["contains_solution"],
            })
            if len(results) == top_k:
                break
        return {"results": results}


def load_encoder(model_path, manifest):
    import torch
    from sentence_transformers import SentenceTransformer

    if sha_file(model_path / "model.safetensors") != manifest["embedding"]["model_weights_sha256"]:
        raise ValueError("Query model weights differ from the indexed document model")
    if sha_file(model_path / "config.json") != manifest["model"]["config_sha256"]:
        raise ValueError("Query model configuration differs from the indexed document model")
    current_prompts = json.loads((model_path / "config_sentence_transformers.json").read_text(encoding="utf-8"))
    if current_prompts != manifest["model"]["prompt_config"]:
        raise ValueError("Query prompt configuration differs from the indexed document model")
    device = os.environ.get("RAG_DEVICE", "cpu")
    if device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cpu":
        torch.set_num_threads(min(8, os.cpu_count() or 1))
    model = SentenceTransformer(str(model_path), device=device, local_files_only=True, trust_remote_code=False,
                                model_kwargs={"torch_dtype": torch.float32 if device == "cpu" else torch.float16,
                                              "attn_implementation": "sdpa"},
                                tokenizer_kwargs={"padding_side": "left"})
    model.max_seq_length = 4096

    def encode(query):
        if len(model.tokenizer.encode(query)) > 4000:
            raise ValueError("Query exceeds the token limit")
        return model.encode([query], prompt_name="query", normalize_embeddings=True,
                            show_progress_bar=False, batch_size=1, convert_to_numpy=True)[0]

    return encode


def create_server(host, port, index, token=""):
    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, data):
            content = json.dumps(data, ensure_ascii=False, allow_nan=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        def do_GET(self):
            self.respond(200, index.health()) if self.path == "/health" else self.respond(404, {"error": "not_found"})

        def do_POST(self):
            if self.path != "/search":
                return self.respond(404, {"error": "not_found"})
            if token and not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token):
                return self.respond(401, {"error": "unauthorized"})
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 32_768:
                    return self.respond(413, {"error": "request_too_large"})
                body = json.loads(self.rfile.read(size))
                if not isinstance(body, dict):
                    raise ValueError("A JSON object is required")
                result = index.search(body.get("query"), body.get("topK", 3),
                                      body.get("minScore", 0.45), body.get("excludeSolutions", True))
                self.respond(200, result)
            except (ValueError, UnicodeError) as error:
                self.respond(400, {"error": str(error)})
            except TimeoutError:
                self.respond(503, {"error": "retrieval_busy"})
            except Exception:
                log.exception("Retrieval failed")
                self.respond(500, {"error": "retrieval_failed"})

        def log_message(self, pattern, *args):
            log.info(pattern, *args)

    return ThreadingHTTPServer((host, port), Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.environ.get("RAG_BIND_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("RAG_PORT", "8765")))
    parser.add_argument("--index", type=Path, default=Path(os.environ.get("RAG_INDEX_DIR", str(DEFAULT_INDEX))))
    parser.add_argument("--model", type=Path, default=Path(os.environ.get("RAG_MODEL_DIR", str(DEFAULT_MODEL))))
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    manifest = json.loads((args.index / "manifest.json").read_text(encoding="utf-8"))
    encoder = load_encoder(args.model, manifest)
    index = KnowledgeIndex(args.index, encoder)
    server = create_server(args.host, args.port, index, os.environ.get("RAG_SERVICE_TOKEN", ""))
    log.info("Knowledge retriever ready: %s:%s, %s", args.host, server.server_port, index.health())
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
