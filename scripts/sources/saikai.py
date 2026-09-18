import requests

NAME = "saikai"
BASE_URL = "https://housesaikai.net"
API = "https://api.housesaikai.net"

HEADERS = {
    "Accept": "application/json, text/plain, */*",
    "Origin": BASE_URL,
    "Referer": BASE_URL + "/",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
}

ENDPOINTS = [
    {
        "path": "/api/lancamentos",
        "params_extra": {"relationships": "language,type,format,latestReleases.separator"},
    },
    {
        "path": "/api/stories",
        "params_extra": {
            "sortProperty": "pageviews",
            "sortDirection": "desc",
            "relationships": "language,type,format",
        },
    },
]


def _page_items(data):
    out = []
    for story in data or []:
        slug = story.get("slug")
        if not slug:
            continue
        out.append(
            {
                "key": slug,
                "title": story.get("title") or slug.title(),
                "url": f"{BASE_URL}/comics/{slug}",
            }
        )
    return out


def fetch(max_pages=15):
    items = []
    seen = set()
    for endpoint in ENDPOINTS:
        for page in range(1, max_pages + 1):
            params = {
                "format": "2",
                "per_page": "100",
                "page": str(page),
            }
            params.update(endpoint["params_extra"])
            r = requests.get(API + endpoint["path"], params=params, headers=HEADERS, timeout=30)
            r.raise_for_status()
            payload = r.json()
            batch = _page_items(payload.get("data") or [])
            new = 0
            for it in batch:
                if it["key"] in seen:
                    continue
                seen.add(it["key"])
                items.append(it)
                new += 1
            meta = payload.get("meta") or {}
            if not batch or page >= (meta.get("last_page") or 1) or new == 0:
                break
    return items