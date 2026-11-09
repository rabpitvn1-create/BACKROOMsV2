# Snapshot character scale fix

## Project and repository
Project: VIBECODEΩ / BACKROOMsV2
Repository: rabpitvn1-create/BACKROOMsV2
Working branch: fix/snapshot-character-scale
Verification branch: release/snapshot-character-scale

## Goal
Normalize the apparent full-body height of companion overlays in the Snapshot stage without editing the generated artwork files individually.

## Scope
In scope:
* Companion overlay rendering in android-apk/app/src/main/assets/index.html.
* Regression coverage in web/tests/ui-mechanism.test.mjs.
* Existing companion assets: Hạ Liên, Trần Khải, Trần Yến, Dạ Yến Linh, Cao Lâm, wanderer, mortal and future overlays that use the same companion slot.

Out of scope:
* Cao Minh's separate player wrapper and sword layers.
* Entity overlay sizing.
* Redrawing or cropping binary artwork.
* Story, canon, scene order, dialogue, save format and profile content.

## Acceptance criteria
* All companion overlays keep the existing left-side slot and 92% stage container.
* Transparent top and bottom padding in source artwork no longer makes one character visibly much shorter than another.
* Feet remain on the same visual baseline.
* Nameplate position does not move with the image correction.
* Bad or unusually sparse alpha masks cannot expand without a hard cap.
* Existing companion source resolution and scene selection remain unchanged.
* Existing tests continue to pass and the Android debug APK builds successfully.

## Locked facts and decisions
The runtime previously gave every companion the same 92% container while generated overlay files contained different amounts of transparent vertical padding. Scaling the container itself would also move the nameplate, so the correction is applied only to the image inside the unchanged container.

The runtime measures alpha bounds from a downsampled canvas after the image loads. It scales the image by the inverse visible-height ratio and shifts the image vertically so the visible bottom stays aligned with the existing baseline. The scale is capped at an effective 107% frame height to prevent pathological assets from exploding the layout.

This uses the existing browser Canvas API and adds no dependency.

## Writer and reviewer
Writer: ChatGPT.
Reviewer: Muse, pending actual review. No Muse approval has been claimed.

## Files changed
* android-apk/app/src/main/assets/index.html
* web/tests/ui-mechanism.test.mjs
* android-apk/SNAPSHOT_CHARACTER_SCALE_FIX.md

## Implementation
Added a small runtime calibration path for companion images:
* companionCalibrationFromBounds computes scale and vertical offset from alpha bounds.
* calibrateCompanionImage samples the loaded overlay at at most 256 px high.
* calibration is cached per resolved image source.
* image styles are reset when the companion source changes or fails.
* the companion container itself remains unchanged, preserving nameplate and stage placement.

Added regression assertions for:
* an asset with 87% visible vertical content,
* an asset with transparent bottom padding,
* the hard scale cap for sparse assets.

## Verification status
Verified on 2026-11-09 against runtime/test code commit 77cec84e9407effb649e02b9cabe908867f58ec1.

Source checks on fix/snapshot-character-scale:
* Inline game script parsed successfully with the JavaScript parser.
* Exactly one companion calibration helper and one image load hook are present.
* The companion container remains height:92%; calibration changes only the image.
* Regression assertions cover 87% visible-height artwork, bottom transparent padding and the scale cap.
* Diff from main is limited to the runtime file, the regression test and this handoff document.

Repository CI verification used release/snapshot-character-scale, carrying the same runtime and test code:
* GitHub Actions run 42595677781, Build Backroom APK.
* Verify Java-only baseline: success.
* gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon: success.
* Prepare APK: success.
* Verify packaged APK, including signature, package metadata, zipalign and required assets: success.
* Upload APK artifact: success.
* Publish GitHub Release and Verify published APK: skipped because the verification branch is not main.

Not verified:
* No real-phone screenshot comparison was possible from the branch because GitHub Pages deploys only from main.
* The added web regression assertions were source-checked but the repository has no pull-request/release-branch workflow that executes web/tests/ui-mechanism.test.mjs.

## Remaining work
Muse review is still pending. Main remains untouched. Merging to main requires an explicit owner decision because a main push triggers public GitHub Pages deployment and the APK workflow's release steps.
