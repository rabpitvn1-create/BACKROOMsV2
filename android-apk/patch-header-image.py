from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
HEADER_ASSET = ROOT / "app/src/main/assets/maxresdefault.jpg"

if not HEADER_ASSET.is_file():
    raise RuntimeError("Header image asset missing: app/src/main/assets/maxresdefault.jpg")

html = INDEX.read_text(encoding="utf-8")
marker = "BACKROOM_HEADER_IMAGE_V1"
style = r'''
<style id="backroomHeaderImageStyle">
/* BACKROOM_HEADER_IMAGE_V1 */
#gameplayPage .topbar{
  background-color:#0e1114;
  background-image:
    linear-gradient(90deg,rgba(4,6,8,.86) 0%,rgba(4,6,8,.64) 46%,rgba(4,6,8,.76) 100%),
    url('maxresdefault.jpg');
  background-size:cover;
  background-position:center 48%;
  background-repeat:no-repeat;
}
#gameplayPage .topbar .eyebrow,
#gameplayPage .topbar h1,
#gameplayPage .topbar .turn,
#gameplayPage .topbar .turn strong{
  text-shadow:0 1px 2px rgba(0,0,0,.98),0 0 7px rgba(0,0,0,.92);
}
#gameplayPage .topbar .eyebrow,
#gameplayPage .topbar .turn{color:#e3e8ec}
#gameplayPage .topbar h1,
#gameplayPage .topbar .turn strong{color:#fff}
</style>
'''

if marker not in html:
    if "</head>" not in html:
        raise RuntimeError("Header image patch: </head> anchor missing")
    html = html.replace("</head>", style + "\n</head>", 1)

for required in (
    marker,
    "url('maxresdefault.jpg')",
    "linear-gradient(90deg",
    "text-shadow:0 1px 2px",
):
    if required not in html:
        raise RuntimeError("Header image contract missing: " + required)

INDEX.write_text(html, encoding="utf-8")
print("Header image installed behind title/turn text with contrast gradient and text shadows.")

# apply-android-ui.py has already installed the three action buttons. Keep the supplied
# navigation artwork in a dedicated post-UI patch so icon changes do not touch action semantics.
navigation_patch = ROOT / "patch-navigation-icons.py"
if not navigation_patch.is_file():
    raise RuntimeError("Navigation icon patch missing")
exec(compile(navigation_patch.read_text(encoding="utf-8"), str(navigation_patch), "exec"), {
    "__name__": "__main__",
    "__file__": str(navigation_patch),
})
