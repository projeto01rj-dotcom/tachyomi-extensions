import argparse
import datetime
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_store import load_catalog, save_json  # noqa: E402
from sources import mangaonline, mangadex, noxmangas, saikai  # noqa: E402

ADAPTERS = {s.NAME: s for s in (mangadex, saikai, mangaonline, noxmangas)}


def main():
    ap = argparse.ArgumentParser(description="Sincroniza o catálogo de mangás de cada fonte.")
    ap.add_argument("--repo-dir", default=".", help="Raiz do repositório")
    ap.add_argument("--out", default="catalog", help="Subpasta de saída (catalogo + diffs)")
    ap.add_argument("--source", default=None, help="Sincronizar apenas uma fonte (opcional)")
    args = ap.parse_args()

    out = os.path.join(args.repo_dir, args.out)
    os.makedirs(out, exist_ok=True)

    now = datetime.datetime.now(datetime.timezone.utc)
    iso = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    ts = now.strftime("%Y-%m-%dT%H-%M-%S")

    names = [args.source] if args.source else list(ADAPTERS)
    summary = {}
    changed = False
    global_removed = []

    for name in names:
        adapter = ADAPTERS[name]
        catalog_path = os.path.join(out, f"{name}.json")
        prev = load_catalog(catalog_path) or {
            "source": name,
            "base_url": adapter.BASE_URL,
            "updated_at": None,
            "mangas": {},
        }
        try:
            snapshot = {it["key"]: it for it in adapter.fetch()}
            mangas = prev.setdefault("mangas", {})
            present = set(snapshot)

            removed = []
            for key, entry in list(mangas.items()):
                if entry.get("status") == "removed":
                    continue
                if key not in present:
                    entry["status"] = "removed"
                    entry["removed_by_source"] = True
                    entry["removed_at"] = iso
                    removed.append(
                        {
                            "source": name,
                            "key": key,
                            "title": entry.get("title", ""),
                            "url": entry.get("url", ""),
                            "removed_at": iso,
                        }
                    )

            newly_added = []
            for key in sorted(set(snapshot) - set(mangas)):
                info = snapshot[key]
                mangas[key] = {
                    "title": info.get("title", ""),
                    "url": info.get("url", ""),
                    "thumb": info.get("thumb"),
                    "status": "active",
                    "first_seen": iso,
                    "removed_at": None,
                }
                newly_added.append(
                    {"source": name, "key": key, "title": info.get("title", ""), "url": info.get("url", "")}
                )

            prev["updated_at"] = iso
            save_json(catalog_path, prev)

            if newly_added or removed:
                delta = {
                    "source": name,
                    "fetched_at": iso,
                    "added": newly_added,
                    "removed": removed,
                }
                save_json(os.path.join(out, "diffs", f"{ts}_{name}.json"), delta)
                changed = True

            global_removed.extend(removed)
            summary[name] = {
                "ok": True,
                "present": len(present),
                "registered": len(mangas),
                "added": len(newly_added),
                "removed": len(removed),
            }
            print(
                f"[{name}] ok: presentes={len(present)} adicionados={len(newly_added)} "
                f"removidos={len(removed)}",
                flush=True,
            )
        except Exception as exc:  # noqa: BLE001 - uma fonte com falha nao derruba as demais
            summary[name] = {"ok": False, "error": f"{type(exc).__name__}: {exc}"}
            print(f"[{name}] FALHOU: {type(exc).__name__}: {exc}", file=sys.stderr, flush=True)

    save_json(
        os.path.join(out, "last_run.json"),
        {"ran_at": iso, "changed": changed, "sources": summary, "removed": len(global_removed)},
    )

    ok_sources = [n for n in summary if summary[n].get("ok")]
    if not ok_sources:
        print("Nenhuma fonte sincronizada com sucesso.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()