from __future__ import annotations

import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/assets/knowledge/sublevels_0_6_source.json"
OUTPUT = ROOT / "app/src/main/assets/level_snapshots/wiki_snapshot_candidates.generated.json"
FANDOM_API = "https://backrooms.fandom.com/api.php"
USER_AGENT = "BACKROOMsV2-snapshot-audit/1.0 (+https://github.com/rabpitvn1-create/BACKROOMsV2)"
TIMEOUT = 25
MAX_PER_PAGE = 40

# These are article chrome, badges, maps/diagrams, author cards, and other non-scene art.
# This is only a first-pass filter; every surviving candidate still requires visual review.
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
                "iiprop": "url|mime|size",
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


def scene_candidate(name: str, info: dict) -> bool:
    lower = name.casefold()
    if any(part in lower for part in REJECT_NAME_PARTS):
        return False
    mime = str(info.get("mime") or "").casefold()
    if mime not in {"image/jpeg", "image/png", "image/webp", "image/gif"}:
        return False
    width = int(info.get("width") or 0)
    height = int(info.get("height") or 0)
    if width < 320 or height < 180:
        return False
    ratio = width / max(height, 1)
    if ratio < 0.55 or ratio > 3.2:
        return False
    return bool(info.get("url"))


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
            "candidates": [],
            "error": "",
        }
        try:
            names = parse_images(row["sourcePage"])
            infos = image_info(names)
            for name in names:
                info = infos.get(name) or {}
                if not scene_candidate(name, info):
                    continue
                entry["candidates"].append(
                    {
                        "fileTitle": name,
                        "assetUrl": info.get("url"),
                        "descriptionUrl": info.get("descriptionurl"),
                        "mime": info.get("mime"),
                        "width": info.get("width"),
                        "height": info.get("height"),
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
        "schemaVersion": 1,
        "generatedBy": "harvest-wiki-snapshot-candidates.py",
        "sourcePolicy": ["backrooms.fandom.com", "backrooms-wiki.wikidot.com", "liminal-archives.wikidot.com"],
        "targetCount": len(rows),
        "targets": harvested,
    }


def main() -> int:
    payload = harvest()
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
