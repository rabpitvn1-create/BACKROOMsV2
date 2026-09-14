from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
INDEX = ASSETS / "index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
METADATA = ASSETS / "overlay-visible-metrics.json"

BASELINES = {
    "male": 0.97,
    "female": 0.93,
    "entity": 0.97,
}

CHARACTER_ASSETS = {
    "kai_snapshot_overlay.png": "male",
    "Kai_MadGod_snapshot_overlay.png": "male",
    "kai_entity_overlay.png": "male",
    "lucia_entity_overlay.png": "female",
    "syvial_entity_overlay.png": "female",
}


def alpha_metric(path: Path, group: str) -> dict[str, object]:
    with Image.open(path) as image:
        image.load()
        if "A" not in image.getbands():
            raise RuntimeError(f"Overlay must have an alpha channel: {path}")
        alpha_box = image.getchannel("A").getbbox()
        if alpha_box is None:
            raise RuntimeError(f"Overlay alpha channel is empty: {path}")
        _, top, _, bottom = alpha_box
        width, height = image.size

    visible_height_ratio = (bottom - top) / height
    visible_bottom_ratio = bottom / height
    if not 0 < visible_height_ratio <= 1:
        raise RuntimeError(f"Invalid visible alpha height for {path}: {visible_height_ratio}")

    return {
        "group": group,
        "canvasWidth": width,
        "canvasHeight": height,
        "visibleHeightRatio": round(visible_height_ratio, 8),
        "visibleBottomRatio": round(visible_bottom_ratio, 8),
        "baselineVisibleHeightRatio": BASELINES[group],
    }


def collect_metrics(assets: Path = ASSETS) -> dict[str, object]:
    entries: dict[str, dict[str, object]] = {}
    for name, group in CHARACTER_ASSETS.items():
        path = assets / name
        if not path.is_file():
            raise RuntimeError(f"Required Character/combat overlay missing: {path}")
        entries[name] = alpha_metric(path, group)

    entity_paths = sorted((assets / "entity").glob("*.png"))
    if not entity_paths:
        raise RuntimeError("No Entity PNG assets found for visible-height metadata")
    for path in entity_paths:
        entries[f"entity/{path.name}"] = alpha_metric(path, "entity")

    return {
        "version": 1,
        "scaleBasis": "alphaBoundingBoxHeight",
        "formula": "renderedCanvasHeightRatio=baselineVisibleHeightRatio/visibleHeightRatio",
        "baselines": BASELINES,
        "assets": entries,
    }


def patch_main_runtime(source: str) -> str:
    source = source.replace("img.style.maxWidth='55%';", "")
    source = source.replace(
        "img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';",
        "",
    )
    source = source.replace(
        "box.style.position='relative';box.style.overflow='hidden';",
        "box.style.position='relative';box.style.overflow='visible';",
    )

    character_rule = re.compile(r"(\.snapshot \.snapshot-character\{[^}]*)max-width:55%;([^}]*})")
    source, _ = character_rule.subn(r"\1\2", source)
    return source


def patch_navigation_return(source: str) -> str:
    if "ANDROID_MANAGEMENT_RETURN_V1" in source:
        raise RuntimeError("Management return finalizer was applied more than once")

    page_anchor = (
        "gameplay.appendChild(game);management.appendChild(side);track.appendChild(gameplay);"
        "track.appendChild(management);shell.appendChild(track);"
    )
    page_replacement = page_anchor + """
  /* ANDROID_MANAGEMENT_RETURN_V1 */
  const returnButton=document.createElement('button');
  returnButton.type='button';returnButton.id='managementReturnButton';returnButton.className='management-return-button';
  returnButton.textContent='← GAME MASTER';returnButton.setAttribute('aria-label','Quay lại Game Master');
  returnButton.addEventListener('click',function(){setPage(0);});
  side.insertBefore(returnButton,side.firstChild);
"""
    if source.count(page_anchor) != 1:
        raise RuntimeError(
            f"Management return page anchor expected once, found {source.count(page_anchor)}"
        )
    source = source.replace(page_anchor, page_replacement, 1)

    pointer_anchor = (
        "track.addEventListener('pointerdown',function(e){if(e.pointerType==='mouse'||interactive(e.target)){reset();return}"
        "pointerId=e.pointerId;startX=lastX=e.clientX;startY=lastY=e.clientY;axis='';});"
    )
    pointer_replacement = (
        "track.addEventListener('pointerdown',function(e){if(e.pointerType==='mouse'||interactive(e.target)){reset();return}"
        "pointerId=e.pointerId;startX=lastX=e.clientX;startY=lastY=e.clientY;axis='';"
        "try{track.setPointerCapture(pointerId)}catch(_){};});"
    )
    if source.count(pointer_anchor) != 1:
        raise RuntimeError(
            f"Management return pointer anchor expected once, found {source.count(pointer_anchor)}"
        )
    source = source.replace(pointer_anchor, pointer_replacement, 1)
    return source


def runtime_style() -> str:
    return r'''<style id="overlayVisibleHeightScaleStyle">
/* OVERLAY_VISIBLE_HEIGHT_SCALE_V1 */
#gameplayPage,#gameplayPage .game,.snapshot{overflow:visible!important}
.snapshot .snapshot-character,.snapshot .snapshot-entity{
  width:auto!important;
  max-width:none!important;
  max-height:none!important;
  height:var(--overlay-canvas-height)!important;
  bottom:var(--overlay-bottom-offset)!important;
  object-fit:contain!important;
  clip:auto!important;
  clip-path:none!important;
}
/* ANDROID_MANAGEMENT_RETURN_V1 */
.management-return-button{
  position:sticky;
  top:4px;
  z-index:95;
  align-self:flex-start;
  min-height:36px;
  margin:0 0 var(--ui-gap) 0;
  padding:8px 12px;
  border:1px solid #30373e;
  border-radius:var(--panel-radius);
  background:#11161a;
  color:#e7eef2;
  font-family:var(--gameplay-font);
  font-size:10px;
  font-weight:700;
  letter-spacing:.08em;
  touch-action:manipulation;
}
</style>
'''


def runtime_script(metadata: dict[str, object]) -> str:
    compact = json.dumps(metadata, ensure_ascii=False, separators=(",", ":"))
    return f'''<script>
/* OVERLAY_VISIBLE_HEIGHT_SCALE_V1 */
(function(){{
  if(window.__overlayVisibleHeightScaleV1)return;window.__overlayVisibleHeightScaleV1=true;
  var overlayMetrics={compact};
  function metricKey(src){{
    var clean=String(src||'').split('#')[0].split('?')[0].replace(/\\\\/g,'/');
    var entityAt=clean.lastIndexOf('/entity/');
    if(entityAt>=0)return 'entity/'+clean.slice(entityAt+8);
    return clean.slice(clean.lastIndexOf('/')+1);
  }}
  function applyVisibleHeightScale(img,explicitKey){{
    if(!img||!img.classList||(!img.classList.contains('snapshot-character')&&!img.classList.contains('snapshot-entity')))return false;
    var key=String(explicitKey||metricKey(img.getAttribute('src')||img.currentSrc||img.src));
    var metric=overlayMetrics.assets[key];if(!metric)return false;
    var visible=Number(metric.visibleHeightRatio),baseline=Number(metric.baselineVisibleHeightRatio),visibleBottom=Number(metric.visibleBottomRatio);
    if(!(visible>0&&baseline>0&&visibleBottom>0))return false;
    var canvasHeight=baseline/visible;
    var bottomOffset=-(1-visibleBottom)*canvasHeight;
    img.style.setProperty('--overlay-canvas-height',(canvasHeight*100).toFixed(6)+'%');
    img.style.setProperty('--overlay-bottom-offset',(bottomOffset*100).toFixed(6)+'%');
    img.dataset.overlayMetricKey=key;
    img.dataset.visibleHeightRatio=visible.toFixed(8);
    img.dataset.visibleHeightBaseline=baseline.toFixed(8);
    return true;
  }}
  function applyTree(root){{
    if(!root)return;
    if(root.matches&&root.matches('.snapshot-character,.snapshot-entity'))applyVisibleHeightScale(root);
    if(root.querySelectorAll)root.querySelectorAll('.snapshot-character,.snapshot-entity').forEach(function(img){{applyVisibleHeightScale(img);}});
  }}
  window.overlayVisibleMetrics=overlayMetrics;
  window.applyOverlayVisibleScale=applyVisibleHeightScale;
  var observer=new MutationObserver(function(records){{records.forEach(function(record){{
    if(record.type==='attributes')applyVisibleHeightScale(record.target);
    record.addedNodes&&record.addedNodes.forEach(applyTree);
  }});}});
  function start(){{var box=document.getElementById('snapshot');if(!box)return;applyTree(box);observer.observe(box,{{subtree:true,childList:true,attributes:true,attributeFilter:['src','class']}});}}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{{once:true}});else start();
}})();
</script>
'''


def patch_index_runtime(source: str, metadata: dict[str, object]) -> str:
    if "OVERLAY_VISIBLE_HEIGHT_SCALE_V1" in source:
        raise RuntimeError("Visible-height overlay finalizer was applied more than once")
    if "</head>" not in source or "</body>" not in source:
        raise RuntimeError("Overlay runtime insertion anchors are missing")
    source = patch_navigation_return(source)
    source = source.replace("</head>", runtime_style() + "</head>", 1)
    source = source.replace("</body>", runtime_script(metadata) + "</body>", 1)
    return source


def main() -> None:
    metadata = collect_metrics()
    main_source = patch_main_runtime(MAIN.read_text(encoding="utf-8"))
    index_source = patch_index_runtime(INDEX.read_text(encoding="utf-8"), metadata)

    if "img.style.maxWidth='55%'" in main_source:
        raise RuntimeError("Entity fit-width logic survived visible-height finalization")
    if "Number(__entityOverlay.maxHeight)" in main_source:
        raise RuntimeError("Legacy Entity canvas-height scaling survived visible-height finalization")
    if "box.style.position='relative';box.style.overflow='hidden'" in main_source:
        raise RuntimeError("Entity renderer still clips overlays")
    for marker in (
        "OVERLAY_VISIBLE_HEIGHT_SCALE_V1",
        "ANDROID_MANAGEMENT_RETURN_V1",
        "managementReturnButton",
        "returnButton.addEventListener('click',function(){setPage(0);})",
        "track.setPointerCapture(pointerId)",
        "width:auto!important",
        "max-width:none!important",
        "height:var(--overlay-canvas-height)!important",
        "baseline/visible",
        "MutationObserver",
    ):
        if marker not in index_source:
            raise RuntimeError("Final Android runtime marker missing: " + marker)

    METADATA.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    MAIN.write_text(main_source, encoding="utf-8")
    INDEX.write_text(index_source, encoding="utf-8")
    print(
        "Visible-height overlay scale finalized: "
        f"male={BASELINES['male']:.2f}, female={BASELINES['female']:.2f}, "
        f"entity={BASELINES['entity']:.2f}, assets={len(metadata['assets'])}; "
        "Management return control and pointer capture verified."
    )


if __name__ == "__main__":
    main()
