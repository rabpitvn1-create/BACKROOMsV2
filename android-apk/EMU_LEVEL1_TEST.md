# Emulator Level 1 progression probe

This marker keeps the existing APK build workflow in the same PR as the emulator progression check. The emulator workflow downloads the successful APK artifact built for the exact PR head SHA and then exercises that APK on Android.

Revision: uses strict WebView control selectors so narrative text such as “Bạn sẽ làm gì tiếp theo?” or prose containing “thực hiện” cannot be mistaken for the actual EditText or Execute button. Emulator system-UI recovery remains enabled.
