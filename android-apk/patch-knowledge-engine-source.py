from pathlib import Path


ENGINE = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
text = ENGINE.read_text(encoding="utf-8")

for marker in (
    'references = rawStrings(json.optJSONArray("references"))',
    'direct += "WRITING.DIALOGUE"',
    'actionText.contains(tag)',
    'return iris.contains("separated") || syvial.contains("separated")',
    'private fun rawStrings(array: JSONArray?)',
):
    if marker not in text:
        raise RuntimeError("Checked-in Knowledge Context authority missing: " + marker)

for retired in ('flags?.opt("entityRegistry")', "current entity registry tag"):
    if retired in text:
        raise RuntimeError("Historical Entity registry retrieval must not exist: " + retired)

print("Knowledge engine authority verified in checked-in Kotlin; no source rewrite performed.")
