# BACKROOMS Android Text Game

Game chạy độc lập trong APK Android. Giao diện vẫn dùng WebView/HTML/JavaScript vì đó là UI của game; phần Android native, Game State Core và đường build APK đã được chuẩn hóa sang Java 17, không còn Kotlin, Python hay LiteRT trong runtime/build path.

## Runtime chính

- `android-apk/app/src/main/assets/index.html`: giao diện text game chạy trong WebView.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: Android/WebView bridge và AI orchestration.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/`: Game State Core thuần Java, kiểm tra state/inventory/party và save cục bộ.
- `android-apk/app/src/main/assets/knowledge/knowledge_db.json`: knowledge database đóng gói trong APK.
- `.github/workflows/build-backroom-apk.yml`: kiểm tra Java-only baseline, test, build và phát hành APK.

## Phiên bản hiện tại

**BR 1.0.0.0.0** (`versionCode 98`). Rương xuất hiện với tỉ lệ 5%; vật phẩm từ Entity và Core trong rương đều có tỉ lệ rơi 100%. Loot pool và số lượng phần thưởng giữ nguyên.

APK debug và ghi chú phiên bản được phát hành tại [GitHub Releases](https://github.com/rabpitvn1-create/BACKROOMsV2/releases). Xem [ghi chú BR 1.0.0.0.0](android-apk/RELEASE_NOTES_BR_1.0.0.0.0.txt) để biết các thay đổi.

## Build cục bộ

Yêu cầu JDK 17 và Gradle 8.10.2 hoặc tương thích với Android Gradle Plugin đang cấu hình.

```bash
cd android-apk
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK được tạo tại `android-apk/app/build/outputs/apk/debug/app-debug.apk`.
