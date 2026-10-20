import json
import hashlib
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np

from service import KnowledgeIndex, create_server


class RetrievalTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="jisuoj-rag-test-")
        cls.directory = Path(cls.temporary.name)
        rows = [{"id": f"sample-{i}", "parent_id": f"parent-{i}", "vector_row": i,
                 "book_title": "Synthetic test material", "text": f"Example passage {i}",
                 "contains_solution": i == 0, "pdf_page_start": i + 1, "pdf_page_end": i + 1}
                for i in range(4)]
        cls.solution = 0
        cls.vectors = np.zeros((4, 1024), dtype=np.float32)
        cls.vectors[:, :2] = [[1, 0], [.8, .6], [.6, .8], [0, 1]]
        chunks = cls.directory / "chunks.jsonl"
        chunks.write_text("\n".join(json.dumps(row) for row in rows), encoding="utf-8")
        vectors = cls.directory / "embeddings.npy"
        np.save(vectors, cls.vectors)
        manifest = {"chunks_sha256": hashlib.sha256(chunks.read_bytes()).hexdigest(),
                    "model": {"name": "synthetic-test-model", "dimensions": 1024},
                    "embedding": {"status": "complete", "sha256": hashlib.sha256(vectors.read_bytes()).hexdigest()}}
        (cls.directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def index(self):
        return KnowledgeIndex(self.directory, lambda query: self.vectors[self.solution])

    def test_vector_alignment_solution_filter_and_source_metadata(self):
        index = self.index()
        result = index.search("查询", exclude_solutions=False)
        self.assertEqual(result["results"][0]["chunkId"], index.chunks[self.solution]["id"])
        filtered = index.search("查询", min_score=0, exclude_solutions=True)
        self.assertTrue(filtered["results"])
        self.assertTrue(all(not item["containsSolution"] for item in filtered["results"]))
        self.assertTrue(all(item["pageStart"] > 0 and item["bookTitle"] for item in filtered["results"]))
        self.assertTrue(all("source_refs" not in item for item in filtered["results"]))

    def test_http_auth_validation_and_health(self):
        server = create_server("127.0.0.1", 0, self.index(), "test-secret")
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        try:
            with urllib.request.urlopen(base + "/health") as response:
                self.assertEqual(json.load(response)["chunks"], 4)
            body = json.dumps({"query": "查询", "excludeSolutions": False}).encode()
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(urllib.request.Request(base + "/search", data=body))
            self.assertEqual(error.exception.code, 401)
            request = urllib.request.Request(base + "/search", data=body,
                                             headers={"Authorization": "Bearer test-secret"})
            with urllib.request.urlopen(request) as response:
                self.assertTrue(json.load(response)["results"])
            invalid = urllib.request.Request(base + "/search", data=b'{"query":""}',
                                             headers={"Authorization": "Bearer test-secret"})
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(invalid)
            self.assertEqual(error.exception.code, 400)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
