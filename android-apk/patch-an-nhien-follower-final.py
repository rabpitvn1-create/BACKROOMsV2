from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "patch-an-nhien-follower.py"

# Historical materialization must run before patch-special-followers-025.py because that patch still
# consumes the settled Java staging shape. The staging gameplay code is temporary only: after the
# 0.25% special-follower compatibility transform runs, patch-special-follower-kotlin-bridge.py
# removes the Java encounter/roll authority and bridges candidate projection to Kotlin.
code = SOURCE.read_text(encoding="utf-8")
exec(compile(code, str(SOURCE), "exec"), {"__name__": "__main__", "__file__": str(SOURCE)})
print("An Nhiên compatibility staging materialized; Kotlin bridge is applied after special-follower transforms.")
