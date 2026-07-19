import urllib.request, json, ssl, sys
from urllib.parse import quote as q

out = []

def test(label, url):
    try:
        ctx = ssl.create_default_context()
        req = urllib.request.Request(url, headers={"User-Agent": "HoopIQ/2.0"})
        with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
            data = json.loads(r.read().decode("utf-8"))
        out.append(f"OK  {label}: {str(data)[:200]}")
        return data
    except Exception as e:
        out.append(f"ERR {label}: {e}")
        return {}

# 1. Wikipedia opensearch
d = test("WP opensearch", "https://en.wikipedia.org/w/api.php?action=opensearch&search=Los+Angeles+Lakers&limit=1&format=json")

# 2. Wikipedia pageimages
d2 = test("WP pageimages", "https://en.wikipedia.org/w/api.php?action=query&titles=Los_Angeles_Lakers&prop=pageimages&pithumbsize=600&piprop=thumbnail&format=json")

# 3. Commons category
d3 = test("Commons cat", "https://commons.wikimedia.org/w/api.php?action=query&list=categorymembers&cmtitle=Category:Los+Angeles+Lakers&cmnamespace=6&cmlimit=5&format=json")

# 4. Commons title search
d4 = test("Commons title", "https://commons.wikimedia.org/w/api.php?action=query&list=search&srsearch=Los+Angeles+Lakers&srnamespace=6&srwhat=title&srlimit=5&srprop=title&format=json")

with open("diag_out.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(out))
print("\n".join(out))
