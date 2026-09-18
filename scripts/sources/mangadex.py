import requests

NAME = "mangadex"
BASE_URL = "https://mangadex.org"
API = "https://api.mangadex.org"
UA = "tachyomi-extensions-catalog-sync/1.0 (catalog sync; local-use)"


def _title(attrs):
    title = attrs.get("title") or {}
    for lang in ("pt-br", "pt", "en"):
        if title.get(lang):
            return title[lang]
    for alt in attrs.get("altTitles") or []:
        if alt.get("en"):
            return alt["en"]
    return "Sem título"


def fetch(max_pages=5):
    items = []
    seen = set()
    offset = 0
    for _ in range(max_pages):
        params = {
            "limit": 100,
            "offset": offset,
            "availableTranslatedLanguage[]": ["pt-br"],
            "contentRating[]": ["safe", "suggestive"],
            "order[latestUploadedChapter]": "desc",
            "hasAvailableChapters": "true",
            "includes[]": ["cover_art"],
        }
        r = requests.get(API + "/manga", params=params, headers={"User-Agent": UA}, timeout=30)
        r.raise_for_status()
        payload = r.json()
        data = payload.get("data", [])
        total = payload.get("total", 0)
        new = 0
        for manga in data:
            mid = manga["id"]
            if mid in seen:
                continue
            seen.add(mid)
            new += 1
            attrs = manga.get("attributes", {})
            items.append(
                {
                    "key": mid,
                    "title": _title(attrs),
                    "url": f"{BASE_URL}/title/{mid}",
                }
            )
        if not data or offset >= total or new == 0:
            break
        offset += len(data)
    return items