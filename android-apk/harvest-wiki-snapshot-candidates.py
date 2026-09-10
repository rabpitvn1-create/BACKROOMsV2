from __future__ import annotations

import hashlib
import io
import json
import math
import os
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/assets/knowledge/sublevels_0_6_source.json"
ASSET_ROOT = ROOT / "app/src/main/assets/level_snapshots"
OUTPUT = ASSET_ROOT / "wiki_snapshot_candidates.generated.json"
REVIEW_DIR = ASSET_ROOT / "_wiki_review"
FANDOM_API = "https://backrooms.fandom.com/api.php"
USER_AGENT = "BACKROOMsV2-snapshot-audit/1.1 (+https://github.com/rabpitvn1-create/BACKROOMsV2)"
TIMEOUT = 25
MAX_PER_PAGE = 60

# Article chrome, badges, author cards and obvious non-scene art. This is intentionally
# conservative: surviving candidates are not approved until a visual review is recorded.
REJECT_NAME_PARTS = (
    "site-logo",
    "logo",
    "icon",
    "threat",
    "survival",
    "difficulty",
    "class-",
    "class_",
    "badge",
    "banner",
    "button",
    "divider",
    "mapmaker",
    "ms-dos",
    "template",
    "author",
    "profile",
    "avatar",
    "wikitext",
    "audio",
    "speaker",
    "sound",
    "music",
    "warning",
    "notice",
)
REJECT_EXACT_NAMES = {
    "sd-hexagon.png",
    "whitebackground.png",
    "blackvoid.png",
    "void.png",
    "real-black-screen.png",
    "tock.gif",
    "viginette7.png",
    "binarygif.gif",
}


def request_bytes(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        },
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.read()


def fetch_json(url: str) -> dict:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.load(response)


def fandom_title(source_page: str) -> str:
    parsed = urllib.parse.urlparse(source_page)
    if parsed.netloc.lower() != "backrooms.fandom.com":
        raise ValueError(f"unsupported source host for harvester: {parsed.netloc}")
    marker = "/wiki/"
    if marker not in parsed.path:
        raise ValueError(f"not a Fandom wiki page: {source_page}")
    return urllib.parse.unquote(parsed.path.split(marker, 1)[1]).replace("_", " ")


def parse_images(source_page: str) -> list[str]:
    params = urllib.parse.urlencode(
        {
            "action": "parse",
            "page": fandom_title(source_page),
            "prop": "images",
            "format": "json",
            "formatversion": "2",
            "origin": "*",
        }
    )
    payload = fetch_json(FANDOM_API + "?" + params)
    images = (payload.get("parse") or {}).get("images") or []
    result: list[str] = []
    for image in images:
        if isinstance(image, str):
            name = image
        elif isinstance(image, dict):
            name = str(image.get("title") or image.get("name") or "")
        else:
            continue
        name = name.strip()
        if name:
            result.append(name.removeprefix("File:"))
    return result


def image_info(file_names: list[str]) -> dict[str, dict]:
    result: dict[str, dict] = {}
    for start in range(0, len(file_names), 40):
        batch = file_names[start : start + 40]
        titles = "|".join("File:" + name for name in batch)
        params = urllib.parse.urlencode(
            {
                "action": "query",
                "titles": titles,
                "prop": "imageinfo",
                "iiprop": "url|mime|size|sha1",
                "format": "json",
                "formatversion": "2",
                "origin": "*",
            }
        )
        payload = fetch_json(FANDOM_API + "?" + params)
        pages = (payload.get("query") or {}).get("pages") or []
        if isinstance(pages, dict):
            pages = list(pages.values())
        for page in pages:
            if not isinstance(page, dict):
                continue
            title = str(page.get("title") or "").removeprefix("File:")
            infos = page.get("imageinfo") or []
            if not title or not infos or not isinstance(infos[0], dict):
                continue
            result[title] = infos[0]
    return result


def candidate_rejection(name: str, info: dict) -> str:
    lower = name.casefold()
    if lower in REJECT_EXACT_NAMES:
        return "KNOWN_NON_SCENE_ASSET"
    if any(part in lower for part in REJECT_NAME_PARTS):
        return "OBVIOUS_CHROME_OR_BADGE_FILENAME"
    mime = str(info.get("mime") or "").casefold()
    if mime not in {"image/jpeg", "image/png", "image/webp", "image/gif"}:
        return "UNSUPPORTED_MIME"
    width = int(info.get("width") or 0)
    height = int(info.get("height") or 0)
    if width < 160 or height < 100:
        return "TOO_SMALL_FOR_SNAPSHOT_REVIEW"
    if not info.get("url"):
        return "NO_ASSET_URL"
    return ""


def target_rows(catalog: dict) -> list[dict]:
    rows: list[dict] = []
    for parent in catalog.get("parentDifficulties") or []:
        level = int(parent["parentLevel"])
        rows.append(
            {
                "id": f"LEVEL.{level}",
                "designation": parent["designation"],
                "name": parent.get("name") or "",
                "sourcePage": parent["wiki"],
            }
        )
    for record in catalog.get("records") or []:
        rows.append(
            {
                "id": record["id"],
                "designation": record["designation"],
                "name": record.get("name") or "",
                "sourcePage": record["wiki"],
            }
        )
    return rows


def harvest() -> dict:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    rows = target_rows(catalog)
    if len(rows) != 40:
        raise RuntimeError(f"expected 40 Level/sublevel targets, got {len(rows)}")

    harvested: dict[str, dict] = {}
    for index, row in enumerate(rows, start=1):
        target_id = row["id"]
        entry = {
            "designation": row["designation"],
            "name": row["name"],
            "sourcePage": row["sourcePage"],
            "pageImages": [],
            "candidates": [],
            "error": "",
        }
        try:
            names = parse_images(row["sourcePage"])
            infos = image_info(names)
            for name in names:
                info = infos.get(name) or {}
                rejection = candidate_rejection(name, info)
                entry["pageImages"].append(
                    {
                        "fileTitle": name,
                        "width": info.get("width"),
                        "height": info.get("height"),
                        "mime": info.get("mime"),
                        "wikiSha1": info.get("sha1"),
                        "heuristicRejection": rejection,
                    }
                )
                if rejection:
                    continue
                entry["candidates"].append(
                    {
                        "fileTitle": name,
                        "assetUrl": info.get("url"),
                        "descriptionUrl": info.get("descriptionurl"),
                        "mime": info.get("mime"),
                        "width": info.get("width"),
                        "height": info.get("height"),
                        "wikiSha1": info.get("sha1"),
                        "review": "PENDING_VISUAL_REVIEW",
                    }
                )
                if len(entry["candidates"]) >= MAX_PER_PAGE:
                    break
        except Exception as exc:  # Research must not break APK compilation.
            entry["error"] = f"{type(exc).__name__}: {exc}"
        harvested[target_id] = entry
        print(
            "SNAPSHOT_HARVEST "
            + json.dumps(
                {
                    "index": index,
                    "target": target_id,
                    "designation": row["designation"],
                    "pageImageCount": len(entry["pageImages"]),
                    "candidateCount": len(entry["candidates"]),
                    "error": entry["error"],
                    "candidates": entry["candidates"],
                },
                ensure_ascii=False,
                separators=(",", ":"),
            ),
            flush=True,
        )

    return {
        "schemaVersion": 2,
        "generatedBy": "harvest-wiki-snapshot-candidates.py",
        "sourcePolicy": ["backrooms.fandom.com", "backrooms-wiki.wikidot.com", "liminal-archives.wikidot.com"],
        "targetCount": len(rows),
        "targets": harvested,
    }


def dhash64(image) -> str:
    from PIL import Image

    gray = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = list(gray.getdata())
    value = 0
    bit = 0
    for y in range(8):
        row = y * 9
        for x in range(8):
            if pixels[row + x] > pixels[row + x + 1]:
                value |= 1 << bit
            bit += 1
    return f"{value:016x}"


def review_group(target_id: str) -> str:
    if target_id.startswith("LEVEL."):
        return "parents"
    parts = target_id.split(".")
    return f"level-{int(parts[1]):02d}"


def generate_review_sheets(payload: dict) -> None:
    try:
        from PIL import Image, ImageDraw, ImageOps
    except Exception as exc:
        print(f"SNAPSHOT_REVIEW_SHEETS warning=Pillow-unavailable detail={exc}", flush=True)
        return

    REVIEW_DIR.mkdir(parents=True, exist_ok=True)
    groups: dict[str, list[tuple[str, dict]]] = {}
    exact_sha_to_refs: dict[str, list[str]] = {}
    for target_id, entry in payload["targets"].items():
        for candidate in entry["candidates"]:
            groups.setdefault(review_group(target_id), []).append((target_id, candidate))
            wiki_sha = str(candidate.get("wikiSha1") or "")
            if wiki_sha:
                exact_sha_to_refs.setdefault(wiki_sha, []).append(f"{target_id}:{candidate['fileTitle']}")

    duplicates = {sha: refs for sha, refs in exact_sha_to_refs.items() if len(refs) > 1}
    payload["exactWikiSha1Duplicates"] = duplicates
    print(f"SNAPSHOT_EXACT_DUPLICATES groups={len(duplicates)}", flush=True)

    cell_w, image_h, label_h, cols = 360, 240, 46, 4
    for group, items in sorted(groups.items()):
        rows = max(1, math.ceil(len(items) / cols))
        sheet = Image.new("RGB", (cell_w * cols, (image_h + label_h) * rows), "white")
        draw = ImageDraw.Draw(sheet)
        downloaded = 0
        for i, (target_id, candidate) in enumerate(items):
            x = (i % cols) * cell_w
            y = (i // cols) * (image_h + label_h)
            try:
                raw = request_bytes(str(candidate["assetUrl"]))
                candidate["downloadSha256"] = hashlib.sha256(raw).hexdigest()
                image = Image.open(io.BytesIO(raw))
                image.seek(0)
                image = image.convert("RGB")
                candidate["dhash64"] = dhash64(image)
                thumb = ImageOps.contain(image, (cell_w, image_h))
                px = x + (cell_w - thumb.width) // 2
                py = y + (image_h - thumb.height) // 2
                sheet.paste(thumb, (px, py))
                downloaded += 1
            except Exception as exc:
                candidate["downloadError"] = f"{type(exc).__name__}: {exc}"
                draw.rectangle((x, y, x + cell_w - 1, y + image_h - 1), outline="black")
                draw.text((x + 8, y + 8), "DOWNLOAD FAILED", fill="black")
            title = str(candidate["fileTitle"])
            label = f"{target_id} | {title}"
            if len(label) > 55:
                label = label[:52] + "..."
            draw.text((x + 5, y + image_h + 4), label, fill="black")
            dims = f"{candidate.get('width') or '?'}x{candidate.get('height') or '?'}"
            draw.text((x + 5, y + image_h + 22), dims, fill="black")
        out = REVIEW_DIR / f"{group}.png"
        sheet.save(out, format="PNG", optimize=True)
        print(
            f"SNAPSHOT_REVIEW_SHEET group={group} candidates={len(items)} downloaded={downloaded} asset={out.relative_to(ROOT)}",
            flush=True,
        )


def main() -> int:
    payload = harvest()
    if os.environ.get("GITHUB_ACTIONS") == "true":
        generate_review_sheets(payload)
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = [len(entry["candidates"]) for entry in payload["targets"].values()]
    failures = [key for key, entry in payload["targets"].items() if entry["error"]]
    print(
        f"SNAPSHOT_HARVEST_SUMMARY targets={len(counts)} candidates={sum(counts)} "
        f"empty={sum(1 for count in counts if count == 0)} errors={len(failures)}",
        flush=True,
    )
    if failures:
        print("SNAPSHOT_HARVEST_ERRORS " + ",".join(failures), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
