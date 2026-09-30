#!/usr/bin/env python3
"""
Fetch classical Chinese texts and save to assets.
Uses chinese-poetry GitHub repo JSON files and ctext.org API.
"""
import json
import os
import sys
import urllib.request
import time
import re

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
        'type': 'json',
    },
    {
        'id': 'mengzi',
        'url': f'{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/mengzi.json',
        'type': 'json',
    },
    {
        'id': 'daxue',
        'url': f'{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/daxue.json',
        'type': 'json',
    },
    {
        'id': 'zhongyong',
        'url': f'{BASE_URL}/%E5%9B%9B%E4%B9%A6%E4%BA%94%E7%BB%8F/zhongyong.json',
        'type': 'json',
    },
    {
        'id': 'xunzi',
        'wikisource_pages': [
            '荀子/勸學篇', '荀子/修身篇', '荀子/不苟篇', '荀子/榮辱篇', 
            '荀子/非相篇', '荀子/非十二子篇', '荀子/仲尼篇', '荀子/儒效篇',
            '荀子/王制篇', '荀子/富國篇', '荀子/王霸篇', '荀子/君道篇',
            '荀子/臣道篇', '荀子/致士篇', '荀子/議兵篇', '荀子/彊國篇',
            '荀子/天論篇', '荀子/正論篇', '荀子/禮論篇', '荀子/樂論篇',
            '荀子/解蔽篇', '荀子/正名篇', '荀子/性惡篇', '荀子/君子篇',
            '荀子/成相篇', '荀子/賦篇', '荀子/大略篇', '荀子/宥坐篇',
            '荀子/子道篇', '荀子/法行篇', '荀子/哀公篇', '荀子/堯問篇',
        ],
        'type': 'wikisource',
    },
    {
        'id': 'guanzi',
        'skip': True,  # Downloaded separately
        'type': 'skip',
    },
]

def fetch_url(url):
    """Fetch content from URL."""
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.read().decode('utf-8')
    except Exception as e:
        print(f"  Error fetching {url}: {e}")
        return None

def fetch_json(url):
    """Fetch JSON from URL."""
    content = fetch_url(url)
    if content:
        try:
            return json.loads(content)
        except Exception as e:
            print(f"  Error parsing JSON: {e}")
    return None

def extract_text_from_json(data):
    """Extract Chinese text from JSON structure."""
    texts = []
    
    # Handle single book format (like daxue, zhongyong)
    if isinstance(data, dict) and 'paragraphs' in data:
        chapter = data.get('chapter', '')
        if chapter:
            texts.append(f"## {chapter}\n\n")
        
        paragraphs = data.get('paragraphs', [])
        for para in paragraphs:
            if para and para.strip():
                texts.append(para.strip())
                texts.append('\n\n')
        
        return ''.join(texts)
    
    # Handle multi-chapter format (like lunyu, mengzi)
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

def fetch_ctext_chapter(book_id, chapter):
    """Fetch a chapter from ctext.org API."""
    url = f"https://api.ctext.org/gettext?urn=ctp:{book_id}/{chapter}&if=en"
    print(f"  Fetching {chapter}...", end=' ')
    
    content = fetch_url(url)
    if not content:
        print("FAILED")
        return ""
    
    # Parse the response - ctext API returns plain text
    try:
        # Remove English translations and annotations
        lines = content.split('\n')
        chinese_lines = []
        for line in lines:
            line = line.strip()
            # Keep lines with Chinese characters
            if any('\u4e00' <= c <= '\u9fff' for c in line):
                chinese_lines.append(line)
        
        text = '\n'.join(chinese_lines)
        char_count = len([c for c in text if '\u4e00' <= c <= '\u9fff'])
        print(f"OK ({char_count} chars)")
        return text + '\n\n'
    except Exception as e:
        print(f"ERROR: {e}")
        return ""

def fetch_wikisource_page(page_title):
    """Fetch a page from Chinese Wikisource."""
    # URL encode the page title
    encoded_title = urllib.parse.quote(page_title)
    url = f"https://zh.wikisource.org/w/api.php?action=query&titles={encoded_title}&prop=revisions&rvprop=content&format=json&formatversion=2"
    
    print(f"  Fetching {page_title}...", end=' ')
    
    content = fetch_url(url)
    if not content:
        print("FAILED")
        return ""
    
    try:
        data = json.loads(content)
        pages = data.get('query', {}).get('pages', [])
        if not pages:
            print("NO DATA")
            return ""
        
        page_content = pages[0].get('revisions', [{}])[0].get('content', '')
        
        # Clean up wikisource markup
        # Remove templates, comments, etc.
        text = page_content
        # Remove {{ }} templates
        text = re.sub(r'\{\{[^}]*\}\}', '', text)
        # Remove [[ ]] links but keep the text
        text = re.sub(r'\[\[(?:[^|\]]*\|)?([^\]]*)\]\]', r'\1', text)
        # Remove <ref> tags
        text = re.sub(r'<ref[^>]*>.*?</ref>', '', text, flags=re.DOTALL)
        text = re.sub(r'<ref[^>]*\/>', '', text)
        # Remove HTML comments
        text = re.sub(r'<!--.*?-->', '', text, flags=re.DOTALL)
        # Remove == headers ==
        text = re.sub(r'=+\s*([^=]*)\s*=+', r'\1', text)
        
        # Keep only lines with Chinese characters
        lines = text.split('\n')
        chinese_lines = []
        for line in lines:
            line = line.strip()
            if any('\u4e00' <= c <= '\u9fff' for c in line):
                chinese_lines.append(line)
        
        result = '\n'.join(chinese_lines)
        char_count = len([c for c in result if '\u4e00' <= c <= '\u9fff'])
        print(f"OK ({char_count} chars)")
        return result + '\n\n'
    except Exception as e:
        print(f"ERROR: {e}")
        return ""

def download_wikisource_book(book_info):
    """Download a book from Wikisource page by page."""
    book_id = book_info['id']
    pages = book_info['wikisource_pages']
    
    print(f"Downloading {book_id} from Wikisource ({len(pages)} pages)...")
    
    all_text = []
    success_count = 0
    
    for page in pages:
        text = fetch_wikisource_page(page)
        if text:
            all_text.append(text)
            success_count += 1
        time.sleep(0.5)  # Be nice to the API
    
    if success_count == 0:
        print(f"  Failed to download any pages")
        return None, 0
    
    combined = ''.join(all_text)
    
    # Convert to simplified
    try:
        combined = converter.convert(combined)
    except:
        pass
    
    count = len([c for c in combined if '\u4e00' <= c <= '\u9fff'])
    print(f"  Success: {success_count}/{len(pages)} pages, {count} Chinese characters")
    
    return combined, count

def download_wikisource_full_book(book_info):
    """Download a complete book from a single Wikisource page."""
    book_id = book_info['id']
    page_title = book_info['wikisource_full']
    
    print(f"Downloading {book_id} from Wikisource (full page: {page_title})...")
    
    text = fetch_wikisource_page(page_title)
    
    if not text:
        print(f"  Failed to download {page_title}")
        return None, 0
    
    # Convert to simplified
    try:
        text = converter.convert(text)
    except:
        pass
    
    count = len([c for c in text if '\u4e00' <= c <= '\u9fff'])
    print(f"  Success: {count} Chinese characters")
    
    return text, count

def download_json_book(book_info):
    """Download and process a book from JSON source."""
    book_id = book_info['id']
    print(f"Downloading {book_id} from GitHub...")
    
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

def main():
    output_dir = 'app/src/main/assets/classics'
    os.makedirs(output_dir, exist_ok=True)
    
    print("=" * 60)
    print("Fetching Classical Chinese Texts")
    print("=" * 60)
    
    results = {}
    
    # Download all books
    for book_info in BOOKS:
        if book_info.get('type') == 'skip':
            print(f"Skipping {book_info['id']} (downloaded separately)")
            # Check existing file
            filepath = os.path.join(output_dir, f"{book_info['id']}.txt")
            if os.path.exists(filepath):
                with open(filepath, 'r', encoding='utf-8') as f:
                    existing = f.read()
                    existing_count = len([c for c in existing if '\u4e00' <= c <= '\u9fff'])
                    results[book_info['id']] = existing_count
            continue
            
        if book_info['type'] == 'json':
            text, count = download_json_book(book_info)
        elif book_info['type'] == 'wikisource':
            text, count = download_wikisource_book(book_info)
        elif book_info['type'] == 'wikisource_full':
            text, count = download_wikisource_full_book(book_info)
        else:
            print(f"Unknown type for {book_info['id']}")
            continue
        
        filepath = os.path.join(output_dir, f"{book_info['id']}.txt")
        if text and count > 100:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(text)
            results[book_info['id']] = count
        else:
            print(f"  Failed to download {book_info['id']}, keeping existing file")
            # Check if existing file exists
            if os.path.exists(filepath):
                with open(filepath, 'r', encoding='utf-8') as f:
                    existing = f.read()
                    existing_count = len([c for c in existing if '\u4e00' <= c <= '\u9fff'])
                    results[book_info['id']] = existing_count
    
    print("\n" + "=" * 60)
    print("Summary:")
    print("=" * 60)
    total = 0
    for book_id in ['lunyu', 'daxue', 'zhongyong', 'mengzi', 'xunzi', 'guanzi']:
        count = results.get(book_id, 0)
        print(f"  {book_id:15s}: {count:8,d} chars")
        total += count
    print(f"  {'TOTAL':15s}: {total:8,d} chars")
    print("=" * 60)
    
    return 0 if results else 1

if __name__ == '__main__':
    sys.exit(main())
