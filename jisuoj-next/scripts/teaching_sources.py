"""Index full book transcriptions and probe traceable evidence for book-to-skill.

Raw book text and rendered pages stay in ignored output/, outside the skill package.
This tool does not generate teaching claims or mark OCR as approved.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "output" / "RAG资料集"
WORK = ROOT / "output" / "book-to-skill-work" / "algorithm-teaching"
PAGE = re.compile(r"^=+\s*PDF\s*第\s*(\d+)\s*页[^\n]*$", re.MULTILINE)
TOPICS = {
    "modeling": ["复杂度", "时间限制", "建模", "数据范围", "抽象"],
    "foundations": ["数组", "字符串", "函数", "递归", "STL", "溢出"],
    "search": ["深度优先", "广度优先", "回溯", "剪枝", "搜索"],
    "greedy": ["贪心", "二分", "分治", "排序", "单调"],
    "dp": ["动态规划", "状态转移", "背包", "最优子结构", "记忆化"],
    "graphs": ["最短路", "Dijkstra", "拓扑排序", "最小生成树", "图论"],
    "structures": ["并查集", "路径压缩", "树状数组", "线段树", "单调队列"],
    "strings": ["KMP", "前缀函数", "字典树", "字符串匹配", "Trie"],
    "math": ["欧几里得", "最大公约数", "快速幂", "取模", "组合数"],
    "geometry": ["叉积", "精度", "凸包", "计算几何", "浮点"],
    "debugging": ["调试", "边界", "测试", "错误", "对拍"],
    "training": ["训练", "学习方法", "交流", "提示", "题解", "思考"],
}


def read_books():
    manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
    books = manifest["books"]
    for book in books:
        book["path"] = DATA / book["file"]
    return books


def pages(book):
    text = book["path"].read_text(encoding="utf-8-sig")
    markers = list(PAGE.finditer(text))
    result = []
    for i, marker in enumerate(markers):
        end = markers[i + 1].start() if i + 1 < len(markers) else len(text)
        result.append((int(marker[1]), text[marker.end():end].strip(), marker.start(), end))
    return result


def index():
    extraction = json.loads((WORK / "metadata.json").read_text(encoding="utf-8"))
    hashes = {s["filename"]: s["sha256"] for s in extraction["sources"]}
    entries = []
    for book in read_books():
        content = book["path"].read_bytes()
        digest = hashlib.sha256(content).hexdigest()
        assert digest == book["source_text_sha256"] == hashes[book["path"].name]
        source_pages = pages(book)
        assert [p[0] for p in source_pages] == list(range(1, book["pdf_pages"] + 1)), book["book_id"]
        entries.append({
            "book_id": book["book_id"], "title": book["title"], "author": book["author"],
            "text_file": book["path"].relative_to(ROOT).as_posix(), "text_sha256": digest,
            "pdf_pages": book["pdf_pages"], "pdf_sha256": book["source_pdf_sha256"],
            "all_pages_indexed": True, "independent_full_text_review": False,
            "topics": {topic: [p for p, body, _, _ in source_pages if any(k.casefold() in body.casefold() for k in keys)]
                       for topic, keys in TOPICS.items()},
        })
    result = {"converter": "book-to-skill", "source_count": len(entries),
              "total_pdf_pages": sum(b["pdf_pages"] for b in entries),
              "extraction_estimated_tokens": extraction["estimated_tokens"], "books": entries}
    (WORK / "source-index.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"sources": len(entries), "pages": result["total_pdf_pages"],
                      "topics": {topic: sum(len(b["topics"][topic]) for b in entries) for topic in TOPICS}},
                     ensure_ascii=False, indent=2))


def probe(args):
    keys = TOPICS[args.topic] if args.topic in TOPICS else [args.topic]
    candidates = []
    for book in read_books():
        if args.book and args.book != book["book_id"]:
            continue
        for number, body, start, end in pages(book):
            if number <= 15 or (book["book_id"] == "B07" and number >= 409):
                continue
            score = sum(body.casefold().count(k.casefold()) for k in keys)
            if score:
                candidates.append((score, book, number, body, start, end))
    candidates.sort(key=lambda item: -item[0])
    for score, book, number, body, start, end in candidates[:args.limit]:
        offset = min((body.casefold().find(k.casefold()) for k in keys if k.casefold() in body.casefold()), default=0)
        snippet = body[max(0, offset - 100):offset + args.chars]
        print(f"\n[{book['book_id']} PDF {number}; score={score}; chars={start}:{end}] {book['title']}\n{snippet}")


def render(args):
    import pypdfium2 as pdfium
    books = {b["book_id"]: b for b in read_books()}
    target = WORK / "page-review"
    target.mkdir(parents=True, exist_ok=True)
    for locator in args.pages:
        identifier, number = locator.split(":")
        number = int(number)
        with pdfium.PdfDocument(books[identifier]["source_pdf"]) as pdf:
            page = pdf[number - 1]
            bitmap = page.render(scale=max(1.7, 1300 / page.get_width()))
            destination = target / f"{identifier}-p{number:04}.png"
            bitmap.to_pil().save(destination)
            print(destination)
            bitmap.close()
            page.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("index")
    query = commands.add_parser("probe")
    query.add_argument("topic")
    query.add_argument("--book")
    query.add_argument("--limit", type=int, default=2)
    query.add_argument("--chars", type=int, default=1600)
    page = commands.add_parser("page")
    page.add_argument("book")
    page.add_argument("numbers", nargs="+", type=int)
    images = commands.add_parser("render")
    images.add_argument("pages", nargs="+")
    args = parser.parse_args()
    if args.command == "index":
        index()
    elif args.command == "probe":
        probe(args)
    elif args.command == "page":
        book = next(b for b in read_books() if b["book_id"] == args.book)
        for number, body, _, _ in pages(book):
            if number in args.numbers:
                print(f"\n[{args.book} PDF {number}] {book['title']}\n{body}")
    else:
        render(args)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
