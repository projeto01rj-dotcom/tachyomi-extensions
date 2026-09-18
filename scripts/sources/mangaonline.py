import requests
from bs4 import BeautifulSoup

NAME = "mangaonline"
BASE_URL = "https://mangaonline.tv"

GA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"}


def _extract(html):
    soup = BeautifulSoup(html, "html.parser")
    out = {}
    for a in soup.select('a[href*="/manga/"][href$="/"]'):
        slug = a["href"].rstrip("/").split("/")[-1]
        if not slug or len(slug) > 150:
            continue
        title = None
        if a.has_attr("title") and a["title"].strip():
            title = a["title"].strip()
        else:
            text = " ".join(a.stripped_strings).strip()
            if text:
                title = text
        prev = out.get(slug)
        if prev is None or (title and len(title) > len(prev["title"])):
            img = a.find("img")
            thumb = img.get("data-src") or img.get("data-lazy-src") or img.get("src") if img else None
            out[slug] = {"title": title or slug.title(), "thumb": thumb}
    return out


def fetch(max_pages=10):
    items = []
    seen = set()
    page = 1
    while page <= max_pages:
        candidates = [
            f"{BASE_URL}/manga/?m_orderby=latest&page={page}",
            f"{BASE_URL}/manga/page/{page}/?m_orderby=latest",
        ]
        got = 0
        for url in candidates:
            try:
                html = requests.get(url, headers=GA, timeout=30).text
            except requests.RequestException:
                continue
            found = _extract(html)
            if not found:
                continue
            for slug, info in found.items():
                if slug in seen:
                    continue
                seen.add(slug)
                items.append(
                    {
                        "key": slug,
                        "title": info["title"],
                        "url": f"{BASE_URL}/manga/{slug}/",
                        "thumb": info.get("thumb"),
                    }
                )
                got += 1
            break
        if got == 0:
            break
        page += 1
    return items