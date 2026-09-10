# Emulator Level 1 progression probe

This marker keeps the existing APK build workflow in the same PR as the emulator progression check. The emulator workflow downloads the successful APK artifact built for the exact PR head SHA and then exercises that APK on Android.

Revision: the real `Khám phá` WebView action now participates in `exitProbe` through its typed `EXPLORE` action kind instead of depending on exit keywords that are absent from the macro label. `transitionReady` / `exitReady` are also accepted as canon-locked transition readiness without requiring a second random exit roll.

For CI only, the debug APK recognizes the explicit `emuLevel1Progression` Activity extra. After the normal six-turn minimum in each Level, that extra makes an otherwise eligible EXPLORE exit probe deterministic. Production probability thresholds are unchanged. The emulator still clicks the real WebView action, waits through true-turn combat, and requires authoritative Level 1 to be observed before authoritative Level 2.
