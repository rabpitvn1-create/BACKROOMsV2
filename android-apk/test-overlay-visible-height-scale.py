from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-overlay-visible-height-scale-final.py"
WORKFLOW = ROOT.parent / ".github/workflows/build-backroom-apk.yml"


def load_patch_module():
    spec = importlib.util.spec_from_file_location("overlay_visible_height_scale", PATCH)
    if spec is None or spec.loader is None:
        raise AssertionError("Cannot load visible-height patch module")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def synthetic_alpha_regression(module) -> None:
    with tempfile.TemporaryDirectory() as temporary:
        image_path = Path(temporary) / "synthetic.png"
        image = Image.new("RGBA", (100, 200), (0, 0, 0, 0))
        for y in range(40, 180):
            for x in range(10, 90):
                image.putpixel((x, y), (255, 255, 255, 255))
        image.save(image_path)

        metric = module.alpha_metric(image_path, "male")
        assert metric["visibleHeightRatio"] == 0.7
        assert metric["visibleBottomRatio"] == 0.9
        canvas_height = metric["baselineVisibleHeightRatio"] / metric["visibleHeightRatio"]
        assert abs(canvas_height * metric["visibleHeightRatio"] - module.BASELINES["male"]) < 1e-12


def runtime_regression(module) -> None:
    sample_main = (
        ".snapshot .snapshot-character{height:97%;width:auto;max-width:55%;object-fit:contain}"
        "img.style.width='auto';img.style.maxWidth='55%';"
        "img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';"
        "box.style.position='relative';box.style.overflow='hidden';"
    )
    patched_main = module.patch_main_runtime(sample_main)
    assert "max-width:55%" not in patched_main
    assert "img.style.maxWidth='55%'" not in patched_main
    assert "Number(__entityOverlay.maxHeight)" not in patched_main
    assert "box.style.overflow='visible'" in patched_main

    metadata = {
        "assets": {
            "synthetic.png": {
                "visibleHeightRatio": 0.7,
                "visibleBottomRatio": 0.9,
                "baselineVisibleHeightRatio": 0.97,
            }
        }
    }
    sample_index = """<html><head></head><body><script>
    gameplay.appendChild(game);management.appendChild(side);track.appendChild(gameplay);track.appendChild(management);shell.appendChild(track);
    track.addEventListener('pointerdown',function(e){if(e.pointerType==='mouse'||interactive(e.target)){reset();return}pointerId=e.pointerId;startX=lastX=e.clientX;startY=lastY=e.clientY;axis='';});
    </script></body></html>"""
    patched_index = module.patch_index_runtime(sample_index, metadata)
    for marker in (
        "width:auto!important",
        "max-width:none!important",
        "overflow:visible!important",
        "var canvasHeight=baseline/visible",
        "var bottomOffset=-(1-visibleBottom)*canvasHeight",
        "MutationObserver",
        "ANDROID_MANAGEMENT_RETURN_V1",
        "managementReturnButton",
        "returnButton.addEventListener('click',function(){setPage(0);})",
        "side.insertBefore(returnButton,side.firstChild)",
        "track.setPointerCapture(pointerId)",
    ):
        assert marker in patched_index


def workflow_order_regression() -> None:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    marker = "patch-overlay-visible-height-scale-final.py"
    scripts_line = next(line for line in workflow.splitlines() if "scripts=(" in line)
    scripts = scripts_line.split("scripts=(", 1)[1].rsplit(")", 1)[0].split()
    assert scripts[-1] == marker, "Visible-height finalizer must be the last runtime patch"
    assert scripts.index(marker) > scripts.index("patch-party-turn-combat-final.py")
    assert f"python3 android-apk/test-overlay-visible-height-scale.py" in workflow


def metadata_contract_regression(module) -> None:
    assert module.BASELINES == {"male": 0.97, "female": 0.93, "entity": 0.97}
    source = PATCH.read_text(encoding="utf-8")
    assert '"visibleHeightRatio"' in source
    assert '"lucia_entity_overlay.png": "female"' in source
    assert '"syvial_entity_overlay.png": "female"' in source
    assert '(assets / "entity").glob("*.png")' in source
    assert "ANDROID_MANAGEMENT_RETURN_V1" in source
    assert "managementReturnButton" in source
    json.dumps(module.BASELINES)

    entity_paths = sorted((ROOT / "app/src/main/assets/entity").glob("*.png"))
    assert entity_paths
    for entity_path in entity_paths:
        metric = module.alpha_metric(entity_path, "entity")
        assert 0 < metric["visibleHeightRatio"] <= 1
        assert metric["baselineVisibleHeightRatio"] == module.BASELINES["entity"]


def main() -> None:
    module = load_patch_module()
    synthetic_alpha_regression(module)
    runtime_regression(module)
    workflow_order_regression()
    metadata_contract_regression(module)
    print("Overlay visible-height and Management return regression checks passed.")


if __name__ == "__main__":
    main()
