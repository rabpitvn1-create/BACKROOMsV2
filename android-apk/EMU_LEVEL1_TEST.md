# Emulator Level 1 progression probe

This marker keeps the existing APK build workflow in the same PR as the emulator progression check. The emulator workflow downloads the successful APK artifact built for the exact PR head SHA and then exercises that APK on Android.

Revision: handles the API 35 AOSP-keyboard contacts permission activity as emulator-only system UI, recovers before declaring MainActivity lost, and rejects false submission acknowledgements from permission dialogs.
