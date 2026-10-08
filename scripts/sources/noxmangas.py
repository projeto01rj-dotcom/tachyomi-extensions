import base64
import hashlib
import re
import time

import requests
from bs4 import BeautifulSoup

NAME = "noxmangas"
BASE_URL = "https://noxmangas.org"
SITE_ID = "00000000-0000-0000-0000-000000000003"

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
BROWSER_HEADERS = {
    "Accept": "*/*",
    "Accept-Language": "pt-BR,pt;q=0.9",
    "Cache-Control": "no-cache",
    "sec-ch-ua": '"Not/A)Brand";v="8", "Chromium";v="126"',
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": '"Windows"',
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-origin",
}


class NixClient:
    def __init__(self):
        self.session = requests.Session()
        self.slot = ""
        self.token = ""
        self.sig = ""

    def _refresh(self, pathname):
        r = self.session.get(BASE_URL + "/_nix/signer.js", headers={"User-Agent": UA}, timeout=20)
        r.raise_for_status()
        m = re.search(r"const z=\[(.*?)\],", r.text)
        z = [x.strip().strip('"') for x in m.group(1).split(",")]
        slot = z[0][::-1]
        token = "".join(x[::-1] for x in z[4:])
        k = "".join(x[::-1] for x in z[1:4])
        payload = f"GET|{pathname}|{SITE_ID}|{slot}|{token}|{k}"
        sig = base64.urlsafe_b64encode(hashlib.sha256(payload.encode()).digest()).rstrip(b"=").decode()
        self.slot, self.token, self.sig = slot, token, sig

    def _headers(self, pathname):
        if not self.slot:
            self._refresh(pathname)
        return {
            "User-Agent": UA,
            "Origin": BASE_URL,
            "Referer": BASE_URL + "/",
            "X-Web-Token": self.token,
            "X-Web-Signature": self.sig,
            "X-Web-Slot": self.slot,
            "X-Site-ID": SITE_ID,
            **BROWSER_HEADERS,
        }

    def get_json(self, path):
        pathname = path.split("?")[0]
        r = self.session.get(BASE_URL + path, headers=self._headers(pathname), timeout=25)
        if r.status_code == 401:
            self._refresh(pathname)
            r = self.session.get(BASE_URL + path, headers=self._headers(pathname), timeout=25)
        r.raise_for_status()
        return r.json()

    def get_comics(self, sort, page):
        """Use the signed API first, then the site's public catalog HTML."""
        path = f"/api/v1/comics?page={page}&per_page=100&sort={sort}"
        try:
            return self.get_json(path)
        except (requests.RequestException, AttributeError, IndexError, ValueError):
            public_path = "/new" if sort == "newest" else "/popular"
            response = self.session.get(
                BASE_URL + f"{public_path}?page={page}",
                headers={"User-Agent": UA, "Accept": "text/html,application/xhtml+xml", "Referer": BASE_URL + "/"},
                timeout=25,
            )
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")
            comics = []
            seen = set()
            for link in soup.select('a[href^="/manga/"]'):
                href = link.get("href", "").split("?", 1)[0].rstrip("/")
                slug = href.removeprefix("/manga/")
                if not slug or slug in seen:
                    continue
                image = link.select_one("img")
                title = (image.get("alt") if image else None) or link.get_text(" ", strip=True)
                if not title:
                    continue
                seen.add(slug)
                comics.append({
                    "slug": slug,
                    "title": title.strip(),
                    "cover": image.get("src") if image else None,
                })
            return {"comics": comics, "total_pages": 1}


def fetch(max_pages=50):
    client = NixClient()
    items = []
    seen = set()
    for sort in ("newest", "popular"):
        page = 1
        while page <= max_pages:
            data = client.get_comics(sort, page)
            comics = data.get("comics") or []
            total_pages = int(data.get("total_pages") or 1)
            new = 0
            for comic in comics:
                slug = comic.get("slug")
                if not slug or slug in seen:
                    continue
                seen.add(slug)
                new += 1
                items.append(
                    {
                        "key": slug,
                        "title": comic.get("title") or slug.title(),
                        "url": f"{BASE_URL}/manga/{slug}",
                        "thumb": comic.get("cover"),
                    }
                )
            if not comics or page >= total_pages or new == 0:
                break
            page += 1
            time.sleep(0.4)
    return items
