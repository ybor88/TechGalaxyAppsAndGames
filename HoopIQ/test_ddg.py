import urllib.request, re, json
from urllib.parse import quote as q

query = 'Kareem Abdul-Jabbar basketball player'
ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
url = f'https://duckduckgo.com/?q={q(query)}&iax=images&ia=images'
req = urllib.request.Request(url, headers={'User-Agent': ua})
with urllib.request.urlopen(req, timeout=10) as r:
    html = r.read().decode('utf-8', errors='replace')

m2 = re.search(r"vqd=([\d\-]+)", html)
m3 = re.search(r'"vqd":"([^"]+)"', html)
m4 = re.search(r"vqd%3D([^&\"']+)", html)
print("m2 (digits):", m2.group(1) if m2 else None)
print("m3 (json):", m3.group(1) if m3 else None)
print("m4 (encoded):", m4.group(1) if m4 else None)
idx = html.find('vqd')
print("snippet:", repr(html[idx:idx+150]) if idx >= 0 else 'NOT FOUND')
print("html length:", len(html))
