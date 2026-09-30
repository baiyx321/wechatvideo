#!/usr/bin/env python3
"""
Fetch classical Chinese texts and save to assets.
Uses chinese-poetry GitHub repo JSON files.
"""
import json
import os
import sys
import urllib.request

try:
    import opencc
    converter = opencc.OpenCC('t2s')
except ImportError:
    print("Installing opencc-python-reimplemented...")
    os.system("pip install -q opencc-python-reimplemented")
    import opencc
    converter = opencc.OpenCC('t2s')

BASE_URL = "https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master"

BOOKS = [
    {
        'id': 'lunyu',
        'url': f'{BASE_URL}/%E8%AE%BA%E8%AF%AD/lunyu.json',
    },
    {
        'id': 'mengzi',
        'url': f'{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/mengzi.json',
    },
    {
        'id': 'daxue',
        'url': f'{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/daxue.json',
    },
    {
        'id': 'zhongyong',
        'url': f'{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/zhongyong.json',
    },
]

SAMPLE_TEXTS = {
    'xunzi': (
        "Xunzi - Encouraging Learning\n\n"
        "The gentleman says: Learning must never cease. Blue comes from the indigo plant, "
        "yet is bluer than indigo. Ice is made from water, yet is colder than water. "
        "Wood warped by a curve-former will conform to the compass; metal subjected to the "
        "whetstone will become sharp. The gentleman who studies broadly and examines himself "
        "daily will become wise and act without fault.\n\n"
        "[Content represents Xunzi philosophy - full text to be sourced]\n\n"
    ) * 15,
    'guanzi': (
        "Guanzi - Governing the People\n\n"
        "When the granaries are full, the people will know propriety and moderation. "
        "When their clothing and food are adequate, they will know honor and shame. "
        "[Content represents Guanzi statecraft - full text to be sourced]\n\n"
    ) * 20,
}

def fetch_json(url):
    """Fetch JSON from URL."""
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"  Error fetching {url}: {e}")
        return None

def extract_text_from_json(data):
    """Extract Chinese text from JSON structure."""
    texts = []
    
    if isinstance(data, list):
        for item in data:
            if isinstance(item, dict):
                chapter = item.get('chapter', '')
                if chapter:
                    texts.append(f"\n## {chapter}\n\n")
                
                content = item.get('paragraphs', item.get('content', []))
                if isinstance(content, list):
                    for para in content:
                        if para and para.strip():
                            texts.append(para.strip())
                            texts.append('\n\n')
    
    return ''.join(texts)

def download_book(book_info):
    """Download and process a book."""
    book_id = book_info['id']
    print(f"Downloading {book_id}...")
    
    data = fetch_json(book_info['url'])
    if not data:
        return None, 0
    
    text = extract_text_from_json(data)
    if not text or len(text) < 100:
        print(f"  Warning: extracted text too short")
        return None, 0
    
    # Convert to simplified
    try:
        text = converter.convert(text)
    except:
        pass
    
    count = len([c for c in text if '\u4e00' <= c <= '\u9fff'])
    print(f"  Success: {count} Chinese characters")
    
    return text, count

def create_sample(book_id):
    """Create sample text when download fails."""
    print(f"Creating sample for {book_id}...")
    text = SAMPLE_TEXTS.get(book_id, f"Sample text for {book_id}\n" * 50)
    count = len([c for c in text if '\u4e00' <= c <= '\u9fff'])
    print(f"  Sample: {count} Chinese characters")
    return text, count

def main():
    output_dir = 'app/src/main/assets/classics'
    os.makedirs(output_dir, exist_ok=True)
    
    print("=" * 60)
    print("Fetching Classical Chinese Texts")
    print("=" * 60)
    
    results = {}
    
    # Download main books
    for book_info in BOOKS:
        text, count = download_book(book_info)
        
        filepath = os.path.join(output_dir, f"{book_info['id']}.txt")
        if text and count > 100:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(text)
            results[book_info['id']] = count
        else:
            # Use sample fallback
            sample_text, sample_count = create_sample(book_info['id'])
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(sample_text)
            results[book_info['id']] = sample_count
    
    # Create samples for books without reliable sources
    for book_id in ['xunzi', 'guanzi']:
        text, count = create_sample(book_id)
        filepath = os.path.join(output_dir, f"{book_id}.txt")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(text)
        results[book_id] = count
    
    print("\n" + "=" * 60)
    print("Summary:")
    print("=" * 60)
    total = 0
    for book_id, count in sorted(results.items()):
        print(f"  {book_id:15s}: {count:8,d} chars")
        total += count
    print(f"  {'TOTAL':15s}: {total:8,d} chars")
    print("=" * 60)
    
    return 0 if results else 1

if __name__ == '__main__':
    sys.exit(main())
