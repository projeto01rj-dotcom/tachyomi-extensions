import argparse
import datetime
import json
import os
import subprocess
import sys


def gh(*args):
    return subprocess.run(["gh", *args], capture_output=True, text=True)


def main():
    ap = argparse.ArgumentParser(
        description="Abre issues para mangás removidos pela fonte (removed_by_source)."
    )
    ap.add_argument("--out", default="catalog", help="Diretório do catálogo")
    ap.add_argument("--repo", required=True, help="repositório no formato owner/repo")
    args = ap.parse_args()

    removed = {}
    diffs_dir = os.path.join(args.out, "diffs")
    if os.path.isdir(diffs_dir):
        for fn in sorted(os.listdir(diffs_dir)):
            if not fn.endswith(".json"):
                continue
            try:
                with open(os.path.join(diffs_dir, fn), encoding="utf-8") as f:
                    delta = json.load(f)
                for r in delta.get("removed", []):
                    if r.get("source") and r.get("key"):
                        removed[f"{r['source']}/{r['key']}"] = r
            except Exception:  # noqa: BLE001
                continue

    issues_path = os.path.join(args.out, "removal_issues.json")
    issues = {}
    if os.path.exists(issues_path):
        with open(issues_path, encoding="utf-8") as f:
            issues = json.load(f)

    gh("label", "create", "removed-by-source", "--repo", args.repo, "--force",
       "--description", "Obra removida pela fonte fornecedora dos dados", "--color", "b60205")

    created = 0
    for rid, r in sorted(removed.items()):
        if rid in issues:
            continue
        title = f"[Removido] {r['source']}: {r['title']}"
        body = (
            f"A fonte **{r['source']}** não lista mais a obra **{r['title']}** no catálogo.\n\n"
            "Esta obra foi marcada como **removida pela fonte fornecedora dos dados** — "
            "ela não foi apagada por este repositório, apenas sinalizada.\n\n"
            f"- **Fonte:** `{r['source']}`\n"
            f"- **Obra:** {r['title']}\n"
            f"- **URL anterior:** {r['url']}\n"
            f"- **Removida em:** {r.get('removed_at', 'desconhecido')}\n"
        )
        p = gh("issue", "create", "--repo", args.repo, "--title", title, "--body", body,
               "--label", "removed-by-source")
        if p.returncode == 0:
            issues[rid] = {
                "url": p.stdout.strip(),
                "created_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            }
            created += 1
            print(f"issue criada: {title} -> {p.stdout.strip()}", flush=True)
        else:
            print(f"falha ao criar issue '{title}': {p.stderr.strip()}", file=sys.stderr, flush=True)

    os.makedirs(args.out, exist_ok=True)
    with open(issues_path, "w", encoding="utf-8") as f:
        json.dump(issues, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{created} nova(s) issue(s) de remoção criada(s).", flush=True)


if __name__ == "__main__":
    main()