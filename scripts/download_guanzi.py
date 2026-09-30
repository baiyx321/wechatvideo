#!/usr/bin/env python3
"""
Download Guanzi from Wikisource using a different approach.
"""
import urllib.request
import time
import re

try:
    import opencc
    converter = opencc.OpenCC('t2s')
except ImportError:
    import os
    print("Installing opencc...")
    os.system("pip install -q opencc-python-reimplemented")
    import opencc
    converter = opencc.OpenCC('t2s')

# Guanzi chapters - split into smaller batches
GUANZI_CHAPTERS_BATCH1 = [
    '牧民', '形勢', '權修', '立政', '乘馬', '七法', '版法', '幼官',
    '幼官圖', '五輔', '宙合', '樞言', '八觀', '法禁', '重令',
]

GUANZI_CHAPTERS_BATCH2 = [
    '法法', '兵法', '大匡', '中匡', '小匡', '戒', '地圖', '參患',
    '制分', '君臣上', '君臣下', '小稱', '四稱', '正言', '侈靡',
]

GUANZI_CHAPTERS_BATCH3 = [
    '心術上', '心術下', '白心', '水地', '四時', '五行', '勢', '正',
    '九變', '任法', '明法', '正世', '治國', '內業', '封禪',
]

GUANZI_CHAPTERS_BATCH4 = [
    '小問', '七臣七主', '禁藏', '入國', '九守', '桓公問', '度地',
    '地員', '弟子職', '言昭', '修權', '問', '謀失', '戒備',
]

GUANZI_CHAPTERS_BATCH5 = [
    '國蓄', '山國軌', '山權數', '山至數', '國准', '輕重甲', '輕重乙',
    '輕重丙', '輕重丁', '輕重戊', '輕重己', '輕重庚', '輕重辛',
    '輕重壬', '輕重癸', '外言',
]

def fetch_chapter(chapter_name):
    """Fetch one chapter using action=raw for simpler parsing."""
    import urllib.parse
    encoded_chapter = urllib.parse.quote(chapter_name)
    url = f"https://zh.wikisource.org/wiki/管子/{encoded_chapter}?action=raw"
    
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as response:
            content = response.read().decode('utf-8')
        
        # Clean up wikitext
        text = content
        # Remove templates
        text = re.sub(r'\{\{[^}]*\}\}', '', text)
        # Remove links but keep text
        text = re.sub(r'\[\[(?:[^|\]]*\|)?([^\]]*)\]\]', r'\1', text)
        # Remove refs
        text = re.sub(r'<ref[^>]*>.*?</ref>', '', text, flags=re.DOTALL)
        text = re.sub(r'<ref[^>]*\/>', '', text)
        # Remove comments
        text = re.sub(r'<!--.*?-->', '', text, flags=re.DOTALL)
        # Remove headers
        text = re.sub(r'=+\s*([^=]*)\s*=+', r'\1', text)
        
        # Keep only lines with Chinese
        lines = text.split('\n')
        chinese_lines = []
        for line in lines:
            line = line.strip()
            if any('\u4e00' <= c <= '\u9fff' for c in line):
                chinese_lines.append(line)
        
        result = '\n'.join(chinese_lines)
        count = len([c for c in result if '\u4e00' <= c <= '\u9fff'])
        
        return result, count
        
    except Exception as e:
        return "", 0

def download_batch(chapters, batch_name):
    """Download a batch of chapters."""
    print(f"\n{batch_name}:")
    all_text = []
    total_chars = 0
    
    for chapter in chapters:
        print(f"  {chapter}...", end=' ')
        text, count = fetch_chapter(chapter)
        
        if text:
            all_text.append(f"\n## {chapter}\n\n{text}\n\n")
            total_chars += count
            print(f"OK ({count} chars)")
        else:
            print("FAILED")
        
        time.sleep(1.5)  # Longer delay to avoid rate limiting
    
    print(f"  Batch total: {total_chars} chars")
    return ''.join(all_text), total_chars

def main():
    print("=" * 60)
    print("Downloading Guanzi from Wikisource")
    print("=" * 60)
    
    all_batches = []
    total = 0
    
    # Download in batches with delays between batches
    for batch_num, (chapters, name) in enumerate([
        (GUANZI_CHAPTERS_BATCH1, "Batch 1"),
        (GUANZI_CHAPTERS_BATCH2, "Batch 2"),
        (GUANZI_CHAPTERS_BATCH3, "Batch 3"),
        (GUANZI_CHAPTERS_BATCH4, "Batch 4"),
        (GUANZI_CHAPTERS_BATCH5, "Batch 5"),
    ], 1):
        
        text, count = download_batch(chapters, name)
        all_batches.append(text)
        total += count
        
        if batch_num < 5:  # Don't sleep after the last batch
            print(f"\nWaiting 30 seconds before next batch...")
            time.sleep(30)
    
    # Combine all text
    combined = ''.join(all_batches)
    
    # Convert to simplified
    try:
        combined = converter.convert(combined)
    except:
        pass
    
    # Save to file
    output_path = 'app/src/main/assets/classics/guanzi.txt'
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(combined)
    
    print("\n" + "=" * 60)
    print(f"Total: {total:,} Chinese characters")
    print(f"Saved to: {output_path}")
    print("=" * 60)

if __name__ == '__main__':
    main()
