import urllib.request, re, json, sys
from urllib.parse import quote as q

def test_bing(query):
    ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36'
    url = f'https://www.bing.com/images/search?q={q(query)}&form=HDRSC2&first=1'
    req = urllib.request.Request(url, headers={'User-Agent': ua})
    with urllib.request.urlopen(req, timeout=12) as r:
        html = r.read().decode('utf-8', errors='replace')
    # Bing stores image URLs in murl attribute
    urls = re.findall(r'murl&quot;:&quot;(https?://[^&]+)&quot;', html)
    if not urls:
        urls = re.findall(r'"murl":"(https?://[^"]+)"', html)
    print(f"Bing found {len(urls)} urls")
    for u in urls[:3]:
        print(" ", u[:100])
    return urls

def test_ddg(query):
    ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36'
    url = f'https://duckduckgo.com/?q={q(query)}&iax=images&ia=images'
    req = urllib.request.Request(url, headers={'User-Agent': ua})
    with urllib.request.urlopen(req, timeout=12) as r:
        html = r.read().decode('utf-8', errors='replace')
    print(f"DDG html length: {len(html)}")
    idx = html.find('vqd')
    print("vqd snippet:", repr(html[idx:idx+120]) if idx >= 0 else 'NOT FOUND')
    return []

query = 'Kareem Abdul-Jabbar basketball player'
print("=== BING ===")
try:
    test_bing(query)
except Exception as e:
    print("BING error:", e)
print()
print("=== DDG ===")
try:
    test_ddg(query)
except Exception as e:
    print("DDG error:", e)
