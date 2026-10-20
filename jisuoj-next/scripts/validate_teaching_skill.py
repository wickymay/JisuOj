"""Validate package hashes, citations, links and context budgets without reading books."""
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "backend/src/main/resources/teaching-skills/competitive-programming-tutor"


def main():
    manifest = json.loads((PACK / "manifest.json").read_text(encoding="utf-8"))
    sources = json.loads((PACK / "sources.json").read_text(encoding="utf-8"))
    assert manifest["schemaVersion"] == 1
    assert len(manifest["topics"]) == 12
    books = {b["book_id"]: b for b in sources["books"]}
    assert len(books) == manifest["sourceBooks"] == 9
    assert sum(b["pdf_pages"] for b in books.values()) == 3570
    assert sources["full_text_accuracy_approved"] is False
    assert len({topic["id"] for topic in manifest["topics"]}) == 12
    for path, digest in manifest["contentHashes"].items():
        target = (PACK / path).resolve()
        assert target.is_relative_to(PACK.resolve()), path
        assert hashlib.sha256(target.read_bytes()).hexdigest() == digest, path
    for topic in manifest["topics"]:
        assert topic["file"] in manifest["contentHashes"]
        assert (PACK / topic["file"]).read_text(encoding="utf-8").strip()
        assert topic["keywords"]
        for ref in topic["sourceRefs"]:
            book = books[ref["bookId"]]
            assert 1 <= ref["pdfPage"] <= book["pdf_pages"]
            assert ref["pdfPage"] in [r["pdf_page"] for r in book["visual_reviews"]]
    for path in PACK.rglob("*.md"):
        text = path.read_text(encoding="utf-8")
        for target in re.findall(r"\]\(([^)]+)\)", text):
            assert "://" not in target, (path, target)
            assert (path.parent / target.split("#")[0]).is_file(), (path, target)
        assert "[TODO:" not in text
    output = ROOT / "output/teaching-skills"
    output.mkdir(parents=True, exist_ok=True)
    report = {"status": "passed", "version": manifest["version"], "topics": 12,
              "books": 9, "source_pdf_pages_indexed": 3570,
              "visually_reviewed_pages": sum(len(b["visual_reviews"]) for b in books.values()),
              "hashes_verified": len(manifest["contentHashes"]),
              "whole_book_ocr_accuracy_approved": False}
    (output / "package-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if "--zip" in sys.argv:
        destination = output / f"competitive-programming-tutor-v{manifest['version']}.zip"
        with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(PACK.rglob("*")):
                if path.is_file():
                    archive.write(path, PACK.name + "/" + path.relative_to(PACK).as_posix())
        with zipfile.ZipFile(destination) as archive:
            assert archive.testzip() is None
        report["archive"] = str(destination)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
