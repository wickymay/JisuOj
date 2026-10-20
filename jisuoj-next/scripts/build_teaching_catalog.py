"""Rebuild package metadata after an intentional teaching-content edit.

PDF review assertions below are a curated record of the 2026-10-18 visual review,
not automatically inferred from the presence of a PNG or an OCR quality flag.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "backend/src/main/resources/teaching-skills/competitive-programming-tutor"
WORK = ROOT / "output/book-to-skill-work/algorithm-teaching"
TOPICS = [
    ("modeling", "建模与复杂度", "ch01-modeling.md", ["建模", "复杂度", "数据范围", "时间限制", "状态设计", "怎么入手", "如何入手"], [("B07", 168), ("B08", 86)]),
    ("foundations", "语言与实现基础", "ch02-foundations.md", ["数组", "循环", "递归", "函数", "指针", "输入输出", "STL", "vector", "溢出", "下标"], [("B01", 249), ("B02", 173)]),
    ("search", "搜索与回溯", "ch03-search.md", ["搜索", "回溯", "剪枝", "BFS", "DFS", "广度优先", "深度优先", "迷宫", "枚举", "去重"], [("B04", 41)]),
    ("greedy-binary", "贪心、二分与分治", "ch04-greedy-binary.md", ["贪心", "二分", "分治", "单调性", "lower_bound", "upper_bound", "交换论证", "区间调度"], [("B01", 249), ("B05", 137)]),
    ("dp", "动态规划", "ch05-dp.md", ["动态规划", "DP", "背包", "状态转移", "滚动数组", "记忆化", "最优子结构", "子序列", "一维压缩"], [("B02", 341)]),
    ("graphs", "图论与路径", "ch06-graphs.md", ["图论", "最短路", "Dijkstra", "Bellman", "SPFA", "负权", "负环", "拓扑", "生成树", "网络流", "松弛"], [("B01", 380), ("B08", 86)]),
    ("structures", "数据结构与集合关系", "ch07-structures.md", ["并查集", "路径压缩", "树状数组", "线段树", "前缀和", "堆", "队列", "栈", "集合合并", "连通性"], [("B02", 173)]),
    ("strings", "字符串与匹配状态", "ch08-strings.md", ["字符串", "KMP", "前缀函数", "模式匹配", "模式串", "回文", "Trie", "AC自动机", "后缀", "重叠匹配"], [("B03", 202)]),
    ("math", "数学、计数与模运算", "ch09-math.md", ["数学", "计数", "组合数", "欧几里得", "gcd", "公约数", "快速幂", "取模", "模运算", "逆元", "质数", "筛法"], [("B06", 20)]),
    ("geometry", "几何与精度", "ch10-geometry.md", ["几何", "叉积", "点积", "凸包", "精度", "浮点", "共线", "线段相交", "向量", "epsilon"], [("B06", 125), ("B07", 168)]),
    ("debugging", "调试与验证", "ch11-debugging.md", ["调试", "报错", "错误", "错了", "不对", "过不了", "超时", "WA", "RE", "TLE", "CE", "崩溃", "越界", "反例", "测试", "对拍", "初始化"], [("B07", 168)]),
    ("training", "学习路线、提示与复盘", "ch12-training.md", ["学习", "训练", "计划", "复盘", "入门", "提示", "思路", "不会", "练习", "怎么学", "不要代码"], [("B09", 16)]),
]
REVIEWS = {
    "B01": {249: "下界二分更新、尾后返回值与区间约定", 380: "松弛操作、邻接表与稀疏图成本"},
    "B02": {173: "迭代路径压缩保存旧父节点后更新", 341: "0/1背包转移及一维倒序容量循环"},
    "B03": {202: "模式前后缀与Next回退；本包另用零基pi定义"},
    "B04": {41: "回溯时减少计数并在失败分支后还原"},
    "B05": {137: "最优子结构和贪心选择性质；不采用适用范围泛化措辞"},
    "B06": {20: "gcd取余恒等式、零余数终止", 125: "二维叉积与点积公式"},
    "B07": {168: "并列答案比较方向、区间端点和边界枚举；未认证整道题解"},
    "B08": {86: "时间与地点联合状态、阈值判定建模；未认证整道流题解"},
    "B09": {16: "算法、读题、练习、交流的入门框架"},
}


def main():
    index = json.loads((WORK / "source-index.json").read_text(encoding="utf-8"))
    metadata = json.loads((WORK / "metadata.json").read_text(encoding="utf-8"))
    assert index["source_count"] == metadata["total_sources"] == 9
    books = []
    for entry in index["books"]:
        books.append({
            k: entry[k] for k in ("book_id", "title", "author", "text_file", "text_sha256", "pdf_pages", "pdf_sha256")
        } | {"all_pages_indexed": True, "independent_full_text_review": False,
             "visual_reviews": [{"pdf_page": page, "scope": scope, "reviewer": "Codex visual comparison", "date": "2026-10-18"}
                                for page, scope in REVIEWS[entry["book_id"]].items()]})
    sources = {"generator": "book-to-skill", "converter_commit": "a24e62f4d8caf2ee433f6e48d9073800f56f906e",
               "mode": "technical", "depth": "study", "organization": "cross-book teaching topics",
               "extraction_estimated_tokens": metadata["estimated_tokens"],
               "all_sources_hash_matched": True, "all_pdf_page_markers_continuous": True,
               "full_text_accuracy_approved": False,
               "scope": "Nine complete transcriptions indexed; selected core passages probed and visually reviewed. Not an exhaustive per-page or per-exercise conversion.",
               "books": books}
    (PACK / "sources.json").write_text(json.dumps(sources, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    topics = [{"id": identifier, "title": title, "file": "chapters/" + filename, "keywords": keys,
               "sourceRefs": [{"bookId": book, "pdfPage": page} for book, page in refs]}
              for identifier, title, filename, keys, refs in TOPICS]
    hashes = {p.relative_to(PACK).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted(PACK.rglob("*")) if p.is_file() and p.name != "manifest.json"}
    manifest = {"schemaVersion": 1, "id": "competitive-programming-tutor", "version": "1.0.0",
                "title": "算法竞赛 AI 教学", "generatedAt": "2026-10-18", "sourceBooks": len(books),
                "sourcePdfPages": sum(b["pdf_pages"] for b in books), "coreFile": "SKILL.md",
                "topics": topics, "contentHashes": hashes}
    (PACK / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"id": manifest["id"], "topics": len(topics), "sourceBooks": len(books),
                      "visualReviewPages": sum(len(b["visual_reviews"]) for b in books),
                      "indexedPdfPages": manifest["sourcePdfPages"], "hashedFiles": len(hashes)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
