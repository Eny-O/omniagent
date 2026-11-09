"""知识库语料批量入库入口。

用法：python scripts/ingest_kb.py <语料目录>
"""
from __future__ import annotations

import asyncio
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.core.config import settings
from app.core.database import close_pool, init_pool
from app.kb.ingestion import KBDocument, ingest_document

DOC_SUFFIXES = {".md", ".markdown", ".txt"}


def _title_of(path: Path, content: str) -> str:
    """取一级标题作为展示标题，无标题时用文件名。"""
    for line in content.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return path.stem


def collect_docs(base: Path) -> list[KBDocument]:
    """扫描目录下的 Markdown / txt 语料。"""
    docs: list[KBDocument] = []
    for path in sorted(base.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in DOC_SUFFIXES:
            continue
        content = path.read_text(encoding="utf-8-sig")
        if not content.strip():
            continue
        rel = path.relative_to(base).as_posix()
        docs.append(KBDocument(
            title=_title_of(path, content),
            content=content,
            source=f"kb_corpus/{rel}",
        ))
    return docs


async def main() -> None:
    base = Path(sys.argv[1] if len(sys.argv) > 1 else "kb_corpus").resolve()
    if not base.is_dir():
        raise SystemExit(f"语料目录不存在：{base}")
    if not settings.dashscope_api_key:
        raise SystemExit("DASHSCOPE_API_KEY 未注入，请先执行 docker compose up -d --force-recreate agent")

    docs = collect_docs(base)
    print(f"[kb] 目录 {base}，待入库 {len(docs)} 篇", flush=True)

    await init_pool()
    upserted = skipped = failed = chunks = 0
    try:
        for i, doc in enumerate(docs, 1):
            try:
                result = await ingest_document(doc)
                chunks += result["chunks"]
                if result["status"] == "upserted":
                    upserted += 1
                else:
                    skipped += 1
                print(
                    f"[kb] [{i}/{len(docs)}] {doc.title} -> {result['status']} chunks={result['chunks']}",
                    flush=True,
                )
            except Exception as e:
                failed += 1
                print(f"[kb] [{i}/{len(docs)}] {doc.title} -> FAILED: {e}", flush=True)
    finally:
        await close_pool()

    print(f"[kb] 完成 upserted={upserted} skipped={skipped} failed={failed} chunks={chunks}", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
