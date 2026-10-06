#!/usr/bin/env python3
"""Fetch classical Chinese texts into app/src/main/assets/classics/.

Sources:
  - chinese-poetry GitHub JSON (论语 / 孟子 / 大学 / 中庸)
  - zh.wikisource.org MediaWiki API (荀子 / 管子)

Never embed book text in this file. The script fetches and writes locally.
Convert traditional to simplified with opencc t2s.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.parse
import urllib.request

try:
    import opencc
    CONVERTER = opencc.OpenCC("t2s")
except ImportError:
    os.system(f"{sys.executable} -m pip install -q opencc-python-reimplemented")
    import opencc
    CONVERTER = opencc.OpenCC("t2s")

BASE_URL = "https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master"
WIKI_API = "https://zh.wikisource.org/w/api.php"
USER_AGENT = "wechatvideo-classics-fetch/1.0 (personal study; +https://github.com/baiyx321/wechatvideo)"
OUTPUT_DIR = "app/src/main/assets/classics"

JSON_BOOKS = [
    {"id": "lunyu", "url": f"{BASE_URL}/%E8%AE%BA%E8%AF%AD/lunyu.json"},
    {"id": "mengzi", "url": f"{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/mengzi.json"},
    {"id": "daxue", "url": f"{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/daxue.json"},
    {"id": "zhongyong", "url": f"{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/zhongyong.json"},
]

XUNZI_PAGES = [
    "荀子/勸學篇", "荀子/修身篇", "荀子/不苟篇", "荀子/榮辱篇",
    "荀子/非相篇", "荀子/非十二子篇", "荀子/仲尼篇", "荀子/儒效篇",
    "荀子/王制篇", "荀子/富國篇", "荀子/王霸篇", "荀子/君道篇",
    "荀子/臣道篇", "荀子/致士篇", "荀子/議兵篇", "荀子/彊國篇",
    "荀子/天論篇", "荀子/正論篇", "荀子/禮論篇", "荀子/樂論篇",
    "荀子/解蔽篇", "荀子/正名篇", "荀子/性惡篇", "荀子/君子篇",
    "荀子/成相篇", "荀子/賦篇", "荀子/大略篇", "荀子/宥坐篇",
    "荀子/子道篇", "荀子/法行篇", "荀子/哀公篇", "荀子/堯問篇",
]


def hanzi_count(text: str) -> int:
    return sum(1 for c in text if "\u4e00" <= c <= "\u9fff")


def to_simplified(text: str) -> str:
    try:
        return CONVERTER.convert(text)
    except Exception:
        return text


def fetch_url(url: str, retries: int = 4) -> str | None:
    delay = 2.0
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=40) as response:
                return response.read().decode("utf-8")
        except Exception as exc:
            print(f"  fetch retry {attempt + 1}/{retries}: {exc}")
            time.sleep(delay)
            delay *= 2
    return None


def extract_text_from_json(data) -> str:
    texts = []
    if isinstance(data, dict) and "paragraphs" in data:
        chapter = data.get("chapter", "")
        if chapter:
            texts.append(f"## {chapter}\n\n")
        for para in data.get("paragraphs", []):
            if para and str(para).strip():
                texts.append(str(para).strip())
                texts.append("\n\n")
        return "".join(texts)

    if isinstance(data, list):
        for item in data:
            if not isinstance(item, dict):
                continue
            chapter = item.get("chapter", "")
            if chapter:
                texts.append(f"\n## {chapter}\n\n")
            content = item.get("paragraphs", item.get("content", []))
            if isinstance(content, list):
                for para in content:
                    if para and str(para).strip():
                        texts.append(str(para).strip())
                        texts.append("\n\n")
    return "".join(texts)


def clean_wikitext(raw: str) -> str:
    import re

    text = raw
    text = re.sub(r"\{\{[^}]*\}\}", "", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.DOTALL)
    text = re.sub(r"<ref[^>]*/>", "", text)
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    text = re.sub(r"=+\s*([^=]*)\s*=+", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    lines = []
    for line in text.splitlines():
        line = line.strip()
        if any("\u4e00" <= c <= "\u9fff" for c in line):
            lines.append(line)
    return "\n".join(lines)


def wiki_query(params: dict) -> dict | None:
    url = WIKI_API + "?" + urllib.parse.urlencode(params, quote_via=urllib.parse.quote)
    content = fetch_url(url)
    if not content:
        return None
    try:
        return json.loads(content)
    except json.JSONDecodeError as exc:
        print(f"  wiki json error: {exc}")
        return None


def fetch_wikisource_page(title: str) -> str:
    print(f"  wiki {title}", flush=True)
    data = wiki_query({
        "action": "query",
        "titles": title,
        "prop": "revisions",
        "rvprop": "content",
        "format": "json",
        "formatversion": "2",
    })
    if not data:
        return ""
    pages = data.get("query", {}).get("pages", [])
    if not pages or pages[0].get("missing"):
        print("    missing")
        return ""
    revisions = pages[0].get("revisions") or []
    if not revisions:
        print("    no revision")
        return ""
    cleaned = clean_wikitext(revisions[0].get("content", ""))
    print(f"    {hanzi_count(cleaned)} hanzi")
    return cleaned


def download_json_book(book: dict) -> tuple[str | None, int]:
    print(f"Downloading {book['id']} from GitHub JSON...")
    raw = fetch_url(book["url"])
    if not raw:
        return None, 0
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        print(f"  json error: {exc}")
        return None, 0
    text = to_simplified(extract_text_from_json(data))
    count = hanzi_count(text)
    print(f"  {count} hanzi")
    return text, count


def list_guanzi_pages() -> list[str]:
    data = wiki_query({
        "action": "query",
        "list": "allpages",
        "apprefix": "管子/第",
        "aplimit": "max",
        "format": "json",
    })
    if not data:
        return []
    titles = [p["title"] for p in data.get("query", {}).get("allpages", [])]
    # Prefer one title per chapter number (skip 權脩 / 匡君 duplicates).
    chosen = {}
    for title in titles:
        # 管子/第01篇牧民
        key = title.split("篇", 1)[0]
        prev = chosen.get(key)
        if prev is None or ("匡君" not in title and "脩" not in title and len(title) < len(prev)):
            chosen[key] = title
    return list(chosen.values())


def download_wikisource_book(book_id: str, pages: list[str]) -> tuple[str | None, int]:
    print(f"Downloading {book_id} from Wikisource ({len(pages)} pages)...")
    chunks = []
    ok = 0
    for title in pages:
        body = fetch_wikisource_page(title)
        time.sleep(0.8)
        if not body:
            continue
        chapter = title.split("/", 1)[-1]
        chunks.append(f"## {chapter}\n\n{body}\n\n")
        ok += 1
    if not chunks:
        return None, 0
    text = to_simplified("".join(chunks))
    count = hanzi_count(text)
    print(f"  {ok}/{len(pages)} pages, {count} hanzi")
    return text, count


def write_book(book_id: str, text: str) -> int:
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    path = os.path.join(OUTPUT_DIR, f"{book_id}.txt")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)
    return hanzi_count(text)


def existing_count(book_id: str) -> int:
    path = os.path.join(OUTPUT_DIR, f"{book_id}.txt")
    if not os.path.exists(path):
        return 0
    with open(path, encoding="utf-8") as handle:
        return hanzi_count(handle.read())


def main() -> int:
    print("=" * 60)
    print("Fetching Classical Chinese Texts")
    print("=" * 60)
    results = {}

    for book in JSON_BOOKS:
        text, count = download_json_book(book)
        if text and count > 100:
            results[book["id"]] = write_book(book["id"], text)
        else:
            print(f"  keep existing {book['id']}")
            results[book["id"]] = existing_count(book["id"])

    xunzi_text, xunzi_count = download_wikisource_book("xunzi", XUNZI_PAGES)
    if xunzi_text and xunzi_count > 100:
        results["xunzi"] = write_book("xunzi", xunzi_text)
    else:
        print("  keep existing xunzi")
        results["xunzi"] = existing_count("xunzi")

    guanzi_pages = list_guanzi_pages()
    print(f"Discovered {len(guanzi_pages)} Guanzi chapter pages")
    guanzi_text, guanzi_count = download_wikisource_book("guanzi", guanzi_pages)
    if guanzi_text and guanzi_count > 100:
        results["guanzi"] = write_book("guanzi", guanzi_text)
        results["guanzi_partial"] = guanzi_count < 40000
    else:
        print("  keep existing guanzi")
        results["guanzi"] = existing_count("guanzi")
        results["guanzi_partial"] = True

    print("=" * 60)
    print("Summary (hanzi):")
    total = 0
    for book_id in ["lunyu", "daxue", "zhongyong", "mengzi", "xunzi", "guanzi"]:
        count = results.get(book_id, 0)
        note = " (partial)" if book_id == "guanzi" and results.get("guanzi_partial") else ""
        print(f"  {book_id:12s}: {count:8,d}{note}")
        total += count
    print(f"  {'TOTAL':12s}: {total:8,d}")
    print("=" * 60)
    return 0


if __name__ == "__main__":
    sys.exit(main())
