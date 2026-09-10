from __future__ import annotations

import hashlib
import html
import io
import json
import math
import os
import re
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSET_ROOT = ROOT / "app/src/main/assets/level_snapshots"
CANDIDATES = ASSET_ROOT / "wiki_snapshot_candidates.generated.json"
OUTPUT = ASSET_ROOT / "wiki_snapshot_embedded.generated.json"
REVIEW_DIR = ASSET_ROOT / "wiki_review_tmp"
FANDOM_API = "https://backrooms.fandom.com/api.php"
USER_AGENT = "BACKROOMsV2-snapshot-audit/1.2 (+https://github.com/rabpitvn1-create/BACKROOMsV2)"
TIMEOUT = 25


def fetch_json(url: str) -> dict:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "application/json,text/plain,*/*"},
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.load(response)


def request_bytes(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"},
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.read()


def fandom_title(source_page: str) -> str:
    parsed = urllib.parse.urlparse(source_page)
    marker = "/wiki/"
    if parsed.netloc.lower() != "backrooms.fandom.com" or marker not in parsed.path:
        raise ValueError(f"not an allowed Fandom wiki page: {source_page}")
    return urllib.parse.unquote(parsed.path.split(marker, 1)[1]).replace("_", " ")


class ImageCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.images: list[dict[str, str]] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag.lower() != "img":
            return
        values = {str(k).lower(): str(v or "") for k, v in attrs}
        self.images.append(values)


def page_html(source_page: str) -> str:
    params = urllib.parse.urlencode(
        {
            "action": "parse",
            "page": fandom_title(source_page),
            "prop": "text",
            "format": "json",
            "formatversion": "2",
            "origin": "*",
        }
    )
    payload = fetch_json(FANDOM_API + "?" + params)
    text = (payload.get("parse") or {}).get("text") or ""
    if isinstance(text, dict):
        text = text.get("*") or ""
    return str(text)


def normalize_name(value: str) -> str:
    value = urllib.parse.unquote(html.unescape(value or ""))
    value = value.split("?", 1)[0].rsplit("/", 1)[-1]
    value = re.sub(r"[^a-z0-9]+", "", value.casefold())
    return value


def absolute_asset_url(value: str) -> str:
    value = html.unescape(value or "").strip()
    if value.startswith("//"):
        value = "https:" + value
    if value.startswith("http://"):
        value = "https://" + value[len("http://") :]
    return value


def preferred_url(attrs: dict[str, str]) -> str:
    for key in ("data-src", "data-original", "src"):
        value = absolute_asset_url(attrs.get(key, ""))
        if value.startswith("https://"):
            return value
    srcset = attrs.get("srcset", "")
    options: list[tuple[int, str]] = []
    for part in srcset.split(","):
        bits = part.strip().split()
        if not bits:
            continue
        url = absolute_asset_url(bits[0])
        if not url.startswith("https://"):
            continue
        width = 0
        if len(bits) > 1 and bits[1].endswith("w"):
            try:
                width = int(bits[1][:-1])
            except ValueError:
                pass
        options.append((width, url))
    return max(options, default=(0, ""))[1]


def image_name(attrs: dict[str, str]) -> str:
    for key in ("data-image-name", "data-image-key", "alt"):
        value = attrs.get(key, "").strip()
        if value:
            return value.removeprefix("File:")
    return urllib.parse.unquote(preferred_url(attrs).split("?", 1)[0].rsplit("/", 1)[-1])


def resolve_target(entry: dict) -> list[dict]:
    unresolved = {
        normalize_name(row.get("fileTitle", "")): row.get("fileTitle", "")
        for row in entry.get("pageImages") or []
        if not row.get("width") or not row.get("height")
    }
    # Also inspect empty candidate pages because parse/images can miss externally embedded files.
    inspect_all = not entry.get("candidates")
    collector = ImageCollector()
    collector.feed(page_html(entry["sourcePage"]))
    resolved: list[dict] = []
    seen_urls: set[str] = set()
    for attrs in collector.images:
        url = preferred_url(attrs)
        if not url or url in seen_urls:
            continue
        name = image_name(attrs)
        norm = normalize_name(name)
        alt_norm = normalize_name(attrs.get("alt", ""))
        title_match = norm in unresolved or alt_norm in unresolved
        if not inspect_all and not title_match:
            continue
        if inspect_all and not title_match:
            # Restrict broad fallback to images that Fandom itself identifies as article media.
            classes = attrs.get("class", "").casefold()
            if "pi-image-thumbnail" not in classes and "image" not in classes:
                continue
        lower = (name + " " + attrs.get("alt", "")).casefold()
        if any(token in lower for token in ("logo", "icon", "survival difficulty", "threat index", "twitter", "discord")):
            continue
        seen_urls.add(url)
        resolved.append(
            {
                "fileTitle": unresolved.get(norm) or unresolved.get(alt_norm) or name,
                "assetUrl": url,
                "alt": attrs.get("alt", ""),
                "widthHint": attrs.get("width", ""),
                "heightHint": attrs.get("height", ""),
                "review": "PENDING_VISUAL_REVIEW",
            }
        )
    return resolved


def group_for(target_id: str) -> str:
    if target_id.startswith("LEVEL."):
        return "embedded-parents"
    return "embedded-level-" + target_id.split(".")[1]


def make_sheets(payload: dict) -> None:
    try:
        from PIL import Image, ImageDraw, ImageOps
    except Exception as exc:
        print(f"SNAPSHOT_EMBEDDED_SHEETS warning=Pillow-unavailable detail={exc}", flush=True)
        return
    groups: dict[str, list[tuple[str, dict]]] = {}
    exact: dict[str, list[str]] = {}
    for target_id, entry in payload["targets"].items():
        for candidate in entry.get("candidates") or []:
            groups.setdefault(group_for(target_id), []).append((target_id, candidate))
    cell_w, image_h, label_h, cols = 360, 240, 46, 4
    REVIEW_DIR.mkdir(parents=True, exist_ok=True)
    for group, items in sorted(groups.items()):
        rows = max(1, math.ceil(len(items) / cols))
        sheet = Image.new("RGB", (cell_w * cols, (image_h + label_h) * rows), "white")
        draw = ImageDraw.Draw(sheet)
        for i, (target_id, candidate) in enumerate(items):
            x = (i % cols) * cell_w
            y = (i // cols) * (image_h + label_h)
            try:
                raw = request_bytes(candidate["assetUrl"])
                sha = hashlib.sha256(raw).hexdigest()
                candidate["downloadSha256"] = sha
                exact.setdefault(sha, []).append(f"{target_id}:{candidate['fileTitle']}")
                image = Image.open(io.BytesIO(raw))
                image.seek(0)
                image = image.convert("RGB")
                candidate["downloadWidth"], candidate["downloadHeight"] = image.size
                thumb = ImageOps.contain(image, (cell_w, image_h))
                sheet.paste(thumb, (x + (cell_w - thumb.width) // 2, y + (image_h - thumb.height) // 2))
            except Exception as exc:
                candidate["downloadError"] = f"{type(exc).__name__}: {exc}"
                draw.rectangle((x, y, x + cell_w - 1, y + image_h - 1), outline="black")
                draw.text((x + 8, y + 8), "DOWNLOAD FAILED", fill="black")
            label = f"{target_id} | {candidate['fileTitle']}"
            if len(label) > 55:
                label = label[:52] + "..."
            draw.text((x + 5, y + image_h + 4), label, fill="black")
            draw.text((x + 5, y + image_h + 22), candidate.get("alt", "")[:50], fill="black")
        out = REVIEW_DIR / f"{group}.png"
        sheet.save(out, format="PNG", optimize=True)
        print(f"SNAPSHOT_EMBEDDED_SHEET group={group} candidates={len(items)} asset={out.relative_to(ROOT)}", flush=True)
    payload["exactDownloadDuplicates"] = {sha: refs for sha, refs in exact.items() if len(refs) > 1}


def main() -> int:
    source = json.loads(CANDIDATES.read_text(encoding="utf-8"))
    targets: dict[str, dict] = {}
    total = 0
    for target_id, entry in source.get("targets", {}).items():
        try:
            candidates = resolve_target(entry)
            error = ""
        except Exception as exc:
            candidates = []
            error = f"{type(exc).__name__}: {exc}"
        targets[target_id] = {
            "designation": entry.get("designation", ""),
            "sourcePage": entry.get("sourcePage", ""),
            "candidates": candidates,
            "error": error,
        }
        total += len(candidates)
        if candidates or error:
            print(
                "SNAPSHOT_EMBEDDED "
                + json.dumps(
                    {"target": target_id, "candidateCount": len(candidates), "error": error, "candidates": candidates},
                    ensure_ascii=False,
                    separators=(",", ":"),
                ),
                flush=True,
            )
    payload = {"schemaVersion": 1, "generatedBy": Path(__file__).name, "targetCount": len(targets), "targets": targets}
    if os.environ.get("GITHUB_ACTIONS") == "true":
        make_sheets(payload)
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SNAPSHOT_EMBEDDED_SUMMARY targets={len(targets)} candidates={total}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
