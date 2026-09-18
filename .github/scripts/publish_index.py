import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

repo_name = os.environ.get("GITHUB_REPOSITORY", "projeto01rj-dotcom/tachyomi-extensions")

publish_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("publish")
apk_dir = publish_dir / "apk"
icon_dir = publish_dir / "icon"
apk_dir.mkdir(parents=True, exist_ok=True)
icon_dir.mkdir(parents=True, exist_ok=True)


def find_apksigner():
    which = shutil.which("apksigner")
    if which:
        return which
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_ROOT"):
        base = Path(os.environ.get(env, ""))
        build_tools = base / "build-tools"
        if build_tools.is_dir():
            versions = sorted(
                (d for d in build_tools.iterdir() if d.is_dir() and d.name[0].isdigit()),
                key=lambda d: tuple(int(p) for p in d.name.split(".") if p.isdigit()),
            )
            if versions:
                return str(versions[-1] / "apksigner")
    return None


def certificate_fingerprint(apk: Path) -> str:
    apksigner = find_apksigner()
    if apksigner:
        out = subprocess.run(
            [apksigner, "verify", "--print-certs", str(apk)],
            capture_output=True,
            text=True,
        )
        match = re.search(
            r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", out.stdout
        )
        if match:
            return match.group(1).replace(":", "").lower()
        raise RuntimeError(f"apksigner did not print a SHA-256 digest for {apk.name}: {out.stdout}\n{out.stderr}")
    keytool = shutil.which("keytool")
    if keytool:
        out = subprocess.run(
            [keytool, "-printcert", "-jarfile", str(apk)],
            capture_output=True,
            text=True,
        )
        for line in out.stdout.splitlines():
            if line.strip().startswith("SHA256:"):
                return line.strip().split(":", 1)[1].replace(":", "").strip().lower()
        raise RuntimeError(f"keytool did not print a SHA256 fingerprint for {apk.name}")
    raise RuntimeError("neither apksigner nor keytool is available")


def find_icon(source_root: Path, module: str, theme: str | None) -> Path | None:
    candidates = [source_root / "src" / module.replace(".", "/") / "res" / "mipmap-xhdpi" / "ic_launcher.png"]
    if theme:
        candidates.append(source_root / "lib-multisrc" / theme / "res" / "mipmap-xhdpi" / "ic_launcher.png")
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


root = Path.cwd()
info_files = list(root.glob("**/build/keiyoushi-source-info.json"))
if not info_files:
    raise SystemExit("No keiyoushi-source-info.json files found")

fingerprint = None
entries = []
for info_file in sorted(info_files):
    info = json.loads(info_file.read_text(encoding="utf-8"))
    pkg = info["packageName"]
    module = info["module"]
    apk_files = list((info_file.parent / "outputs/apk" / "release").glob("*.apk"))
    if not apk_files:
        raise SystemExit(f"{pkg}: no release apk under {info_file.parent}")
    apk = apk_files[0]

    source_root = root
    icon_src = find_icon(source_root, module, info.get("theme"))
    if icon_src is None:
        raise SystemExit(f"{pkg}: no ic_launcher.png found for module {module}")
    icon_dst = icon_dir / f"{pkg}.png"
    shutil.copy2(icon_src, icon_dst)
    shutil.copy2(apk, apk_dir / apk.name)

    if fingerprint is None:
        fingerprint = certificate_fingerprint(apk)

    entries.append(
        {
            "name": info["name"],
            "pkg": pkg,
            "apk": apk.name,
            "lang": module.split("/")[0],
            "code": info["versionCode"],
            "version": info["versionName"],
            "nsfw": 1 if info["contentWarning"] == 3 else 0,
            "sources": [
                {
                    "id": int(source["id"]),
                    "lang": source["lang"],
                    "name": source["name"],
                    "baseUrl": source["baseUrl"],
                }
                for source in info["sources"]
            ],
        }
    )

if fingerprint is None:
    raise SystemExit("Could not determine the signing certificate fingerprint")

entries.sort(key=lambda entry: entry["pkg"])

(publish_dir / "index.json").write_text(
    json.dumps(entries, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)
(publish_dir / "index.min.json").write_text(
    json.dumps(entries, separators=(",", ":"), ensure_ascii=False),
    encoding="utf-8",
)
(publish_dir / "repo.json").write_text(
    json.dumps(
        {
            "meta": {
                "name": "Tachyomi Extensions",
                "website": f"https://github.com/{repo_name}",
                "signingKeyFingerprint": fingerprint,
            }
        },
        indent=2,
        ensure_ascii=False,
    )
    + "\n",
    encoding="utf-8",
)

print(f"Published {len(entries)} extensions with fingerprint {fingerprint}")
for entry in entries:
    print(f"  {entry['pkg']} v{entry['version']} ({entry['apk']})")