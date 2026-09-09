from pathlib import Path
import base64
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"

# User-supplied navigation artwork, optimized to 64x64 WebP for the 19-21 CSS-pixel controls.
# Semantic mapping is intentional: magnifier = SEARCH, doorway = EXECUTE, compass = EXPLORE.
SEARCH_ICON_WEBP = "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"
EXECUTE_ICON_WEBP = "UklGRnQJAABXRUJQVlA4WAoAAAAQAAAAPwAAPwAAQUxQSHsDAAABBjrbtpu2nTHmXse2fSoblSt2tl0qdtKltJ1USWXrL9i2nTnmwHWt+Y1vYF/p3xERDCRJhtSDoMe4jd0v4P8nsuoU9KauR7RSelPXb8HBQ9NFJXtH13fhqcc+sg5/XOiS2XWdecceet86wKra4clRosqp6zP32IPvGgcYBW1hoNz7S0WVa75zjz30nm51NRpjgRa2dr/OkklBevM9+tC7mqwVigHa6GX+dTeITkps1/nog++3OigUy9mSEqDsnSmQYsIt75D5NkobiwBj0OWtcnckQGLY21RneaFFBilJwWH3jz9f4+t8UyqpuMbUBhYez2UMLRPclYQrXQtDx2p9WyIe6mQoC+MhLJbijiRc7WpvqtbEIAd9FQMAfNM05AGxZ8E3T3BHOs1bBWuSc5dqRjuJ1tsW6VXIQkcSLZLNFBldAhmVyMqXvhWtYoF8CPrRCy2AiGCTktHzgPkLyiIsXobb2wLiMoi9d5W88SdjHSPsUUMcdCjpwQUHus7+dQMXYqBLwEyi70daMV7JkzQgkEgx5Aejwbg28CpygJLpSrzk/qkZMUAgE0PqEHo68z5znNiQkTdVzRcCIA/Q1pMngOPHjp49f/XffKBsAMF7L8WYbQf3bN1MZONI0fnJ6eBdj6Brrkoll33Nautdg35vUUpB4XspEX7X5152/ypf/nNfT//RaSLeSCMLTCKF/EiBzLF0pSn/rvqmm3iRdx7RdA9uc7y8OeJnCu+5iPN/vJnEkPu//ennUn4p5efyw3PT+v1mNOehwMMCke/ekNGjSgFQMFygesypoK8POV0dPHeSdzpVwdCnbeOPETGsNQj7TcmJQME5p0Kuil4/xmsn+315dYuId8fA+kDyu0GR8HIBqe9+NwneNvarh6xgDiAuD4Dx2xlhAxlaSKY5WOMvFYsM757h4PtRv06e5Lz7u+E+cuhwA3RilUpO+5x+r/JH3nPozLud+N069uxTv4QImjXgzl3qdzPG73n825aoNdNeRkCWSgAjtz36Q0s0Bp4LzQLOGRQBjNn9xK+E0E3RUchCTDr5inLQNYWlqN1tBLmIede+RXaROwSNW53v10t2IFA23PO9g6k17Uy5M6ISGaXqtFtw9FXjbaJyl/Bcxp4W3/yZAxqN6wIxa09Ddj37r9M3ie4YGMne0+xNcwIW2XuqBLjYS9ULFwEAVlA4INIFAABwGgCdASpAAEAAPj0YikMiIaEWCq8wIAPEshhgCTUD0RHswL2TkmuYXirmNw31H/mPeLeZPzifSX5zPpF+pX6AH65eUB8I391/4ddz/C/zn8Gfgf27yhz43hx2qv7L+T/EX8y/ln91/MjzPdQK9H/xXGmUAPzZ/u/ZL/k/+l/kfyd9vv0F/xfcH/lX9L/1HAu/r06+UCF8uOgX/j1k7SKro82o3bQHFgM/CzQRD3QLsnDTvGxXi38wOJyl/OvIDq2LKg9uHbVRlinaSKlrv2NF2o9nbX9cl8yU/wnkAP7/YBiZzOJcRVcS/2fxJRiUux8//FB8itv05Z9DrwHP1Y12MNTqTNL/ajyHfoNprc/aPoq7klVfTsGyPynC6jD18XnvBn7Qngv59z4OYRh4CnaMScWWQ8XSts+dV0AzmmI6XKpZH7wK4BV03pUwx/SNlsM0H+N663ib0KFPP/R5rRQ97yfxoDnh75bn0tDudrS4At5Q+rTZGtnYo29Pp1beSuQO4/8zz811aDzuquF1IGAu/5Ra/7mF/89Z7/NsSSQr4ZxqFzNXM8yrCxq8h1QJ6IPmR4l7YS4Yl1/jX1qZvazvvRBjyW+DAUqOfbsR3Xn6g4r1NQFu7OfrSf+WEF8yj2sZV5Lvfuc3r8WrBYxyYHrkuzHYwXWhfK4nYiiSCxKMkMb4OvH7LaCfk7XAmAGC55bQW5qxj5CnpRHDZuj0aCEds/iCTTmsE5gaHvUERj/Uaz/zv/dNAiBhHLPFrrsLwsRCir8fkzz7lo0v47xNrKas1ak5W95iXIOKP4NfWGsE+F4mzIhsLpwFf9dB5zBkjf6MueXuvU/n8lHPQYpic2bWsuLB2X7nE3LVicONufD27ndJLJ/ifekeRQ4BFDnZN3OZIp538FhvRDPdO4n4/5+xMnKW798bJ2bwVjh6MFC8Ig9xcPvLJ8rIfgVswKxcPqvEkWGWpZVcqG19frS42bP/JCrPaF7hF4d7cy0EpQC0Gf0n/O8hzQ5w0NdFqgacaeCtzFatgIXJswjdAXAoeHbX0bvLFRpmK7HwydP3vVv3l9PqiYrWGyxIcb6oTK1q71xvX00eLlv+ARAGVjLrWCoiwYhRKlGbf/7+S/rGCX7q9NCtJnG+vHIJPt2DVukoqPEZ1+GzNzBw+N//v+gxZmnRlxD4NPvzurXsQVm787sf06mxHxqHkbX1lcny1//rA/jRDcPhbXJuNLHV9Bvmxj7s7+qokf+S+/N9DjxI6NVB8yDrcLy9URkyKF+7mG/P4JY8KBPXEJvzcplPvCG/rHHhzgg830sW2ZIPHnaJFB5j0Pgk0OEaLzz0oxxxlbjJ/ko68A5CG/U8DMxuNxux2QA4z6VjviX0sil66HnnPVf8Ow/vXZ5MWGKH5KPs/2IkMFo3JfbTlzwtPzv5/4fnYL2/vYG2dEdgGgC4NVGOV60vj/h5houeuGh8V2B6DuNYy+2HImcMB/l5Tpbn5jLaww/QAPGXXprvmsxJ10zRUIAhW775iQ+V1L2Iny25wQ5fl9KoVegXbAZL300HXMXnLuLmVas59Ch+YTKwgnSA+2WMNMU4KNwaOn3XwBLybmiz9J+bAE9f8xaFSddB13vUWDNPDHW75z5v/OYj7PJfuRrfn+SFdP92P+TtrCneAy9VNAmABm6xYNfyJPkAOdLF7wVKEvIP4bUeVcAtmNF9SwNSJdfgEft6+/JdR1J1n64r+pgXIwvH7wLpBDB1k+Omk9r3I0EMMKI/kEaVg2fsrXR9itRuPhYBPBG9ouTR+pZtsNnGM8l4oJp+VsytN2QdX9invekdFkB2QqlH2tSXvlyKOwQYvX/xaopWRr9akzs2sQVb/MXbBtRWg3bjXVhUqqXn9e3Y7uTOA+b9wP+PsOP9CSOxzURUr48Xc12KyrUXRM1YaR2WmnEs+8g/Bur7gTaIdVhTL6u2wQWhOkTaWM6UFlKy1liTcslbQWaLCe9sSAAAAA=="
EXPLORE_ICON_WEBP = "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"

ICONS = {
    "searchActionButton": SEARCH_ICON_WEBP,
    "submit": EXECUTE_ICON_WEBP,
    "exploreActionButton": EXPLORE_ICON_WEBP,
}


def validate_webp_data(label: str, encoded: str) -> None:
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) < 16 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
        raise RuntimeError(f"Navigation icon is not a valid WebP payload: {label}")


def replace_action_icon(source: str, button_id: str, encoded: str) -> str:
    button_pattern = re.compile(
        r'<button\b[^>]*\bid="' + re.escape(button_id) + r'"[^>]*>.*?</button>',
        re.IGNORECASE | re.DOTALL,
    )
    match = button_pattern.search(source)
    if not match:
        raise RuntimeError(f"Navigation icon button missing: {button_id}")
    button = match.group(0)
    image_pattern = re.compile(
        r'(<img\b[^>]*\bclass="action-sprite"[^>]*\bsrc=")data:image/webp;base64,[^"]+("[^>]*>)',
        re.IGNORECASE,
    )
    replacement = lambda m: m.group(1) + "data:image/webp;base64," + encoded + m.group(2)
    updated_button, count = image_pattern.subn(replacement, button, count=1)
    if count != 1:
        raise RuntimeError(f"Navigation icon image anchor invalid for: {button_id}")
    return source[:match.start()] + updated_button + source[match.end():]


html = INDEX.read_text(encoding="utf-8")
for button_id, encoded in ICONS.items():
    validate_webp_data(button_id, encoded)
    html = replace_action_icon(html, button_id, encoded)

STYLE_MARKER = "BACKROOM_NAVIGATION_ICONS_V1"
if STYLE_MARKER not in html:
    style = r'''<style id="backroomNavigationIconStyle">
/* BACKROOM_NAVIGATION_ICONS_V1: smooth rendering for the supplied detailed artwork. */
.primary-action .action-sprite{image-rendering:auto}
</style>
'''
    if "</head>" not in html:
        raise RuntimeError("Navigation icon patch: </head> anchor missing")
    html = html.replace("</head>", style + "\n</head>", 1)

for required in (
    STYLE_MARKER,
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    "image-rendering:auto",
):
    if required not in html:
        raise RuntimeError("Navigation icon contract missing: " + required)

for encoded in ICONS.values():
    if "data:image/webp;base64," + encoded not in html:
        raise RuntimeError("Navigation icon payload was not installed")

INDEX.write_text(html, encoding="utf-8")
print("Navigation icons replaced: magnifier=Search, doorway=Execute, compass=Explore.")
