from pathlib import Path


FACADE = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
facade = FACADE.read_text(encoding="utf-8")

if "StoryProgressionPolicy.normalizeCandidate" in facade:
    raise RuntimeError("GameCore debug trace must not reintroduce StoryProgressionPolicy normalization")
for marker in (
    '"candidateAfterStoryNormalization"',
    '"storyProgression"',
    '"gameCorePending"',
    '"gameCoreCommands"',
    '"gameCoreCommitResult"',
    '"coreCommittedState"',
    '"validated_candidate_committed"',
):
    if marker not in facade:
        raise RuntimeError("Checked-in GameCore debug trace marker missing: " + marker)

print("GameCore runtime debug trace verified in checked-in Kotlin; no source rewrite performed.")
