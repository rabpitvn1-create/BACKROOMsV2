# BACKROOMsV2

BACKROOMsV2 là game Backrooms dạng text RPG chạy trực tiếp trong APK Android. Giao diện được dựng bằng HTML/CSS/JavaScript trong Android WebView, còn Game State Core, combat, inventory, party, continuity và save được xử lý ở lớp Android/Kotlin. Nội dung canon, ảnh Level, Character/Entity assets và knowledge database được đóng gói trong ứng dụng; phần Game Master dùng provider AI đã cấu hình trong build/runtime.

**Phiên bản hiện tại:** `1.1.77` (`versionCode 79`)  
**Android:** min SDK 24, target/compile SDK 35  
**Runtime:** Android WebView + Java/Kotlin + LiteRT

## Điểm chính

- Gameplay text theo canon với Game State Core làm lớp trạng thái có thẩm quyền.
- Storytelling hiển thị theo thứ tự thời gian trong một khung liên tục, trong khi Combat HUD và các lớp combat vẫn được quản lý riêng.
- Điều hướng ngang giữa Gameplay và Character/Status được tối ưu cho Android WebView, có xử lý pointer capture, touch fallback, cuộn dọc và đường quay lại Game Master rõ ràng.
- Combat runtime hỗ trợ party, Entity, HP delta, overlay theo lượt và nhịp hiển thị ổn định.
- Character/Entity overlay được scale theo **chiều cao thị giác** dựa trên alpha bounding box, không bị thu nhỏ theo chiều rộng ảnh và không phụ thuộc fit-width.
- Kai và các party combat assets được đồng bộ từ nguồn đã xác minh bằng hash/kích thước trước khi đóng gói APK.
- LiteRT intent model được train/verify trong CI trước build.
- Save và migration nằm trong Game State Core; dữ liệu chơi được lưu cục bộ trên thiết bị.
- Runtime debug tracing có lớp best-effort để theo dõi provider routing, writer/repair, audit, state commit, combat và UI bridge mà không làm hỏng luồng gameplay chính.

## Kiến trúc

| Thành phần | Vai trò |
| --- | --- |
| `android-apk/app/src/main/assets/index.html` | UI text game chạy trong WebView |
| `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java` | Android/WebView bridge, provider orchestration, lifecycle và native integration |
| `android-apk/app/src/main/java/com/rabpit/backroom/core/` | Game State Core, action/command pipeline, combat, inventory, party, encounter, save/migration và canon policy |
| `android-apk/app/src/main/assets/knowledge/` | Knowledge/canon data đóng gói trong APK |
| `android-apk/app/src/main/assets/entity/` | Entity visual assets |
| `android-apk/app/src/main/assets/level_snapshots/` | Level snapshot assets |
| `android-apk/patch-*.py` | Build-time finalizers/compatibility patches được áp dụng theo thứ tự cố định |
| `android-apk/test-*.mjs`, `android-apk/test-*.py` | Regression tests cho UI/runtime/overlay/canon |
| `.github/workflows/build-backroom-apk.yml` | Pipeline release chuẩn: validate → train LiteRT → sync assets → patch → test → build → verify → publish |

## Build cục bộ

Yêu cầu tối thiểu cho Android build:

- JDK 17
- Android SDK 35
- Gradle 8.10.2 hoặc môi trường tương thích

Build baseline từ source đang check-in:

```bash
cd android-apk
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK debug được tạo tại:

```text
android-apk/app/build/outputs/apk/debug/app-debug.apk
```

Lưu ý: build release trên GitHub Actions còn chạy một **runtime patch chain** trước Gradle. Vì vậy build Gradle thuần ở máy local không mặc định tương đương 100% với APK release. Pipeline trong `.github/workflows/build-backroom-apk.yml` là nguồn chuẩn cho artifact phát hành.

## Cấu hình Game Master

Workflow release hiện kiểm tra các biến cấu hình sau:

```text
GEMINI_API_KEY_1   # có thể fallback từ GEMINI_API_KEY trong GitHub Actions
GEMINI_API_KEY_2
GEMINI_API_KEY_3
GEMINI_API_KEY_4
GEMINI_API_KEY_5
HAIKU_API
HAIKU_BASE_URL
HAIKU_MODEL
```

Không commit giá trị secret vào repository. GitHub Actions lấy chúng từ repository secrets/variables và build sẽ fail sớm nếu cấu hình bắt buộc bị thiếu.

## CI và phát hành

`Build Backroom APK` chạy khi có thay đổi liên quan tới `android-apk/**` hoặc workflow build. Pipeline hiện thực hiện các bước chính:

1. Validate campaign/New Game startup và release contract.
2. Verify Game Master configuration.
3. Train và kiểm tra LiteRT intent classifier.
4. Sync các Kai/party assets đã xác minh.
5. Áp dụng Android runtime patch chain theo thứ tự cố định.
6. Chạy regression tests Node/Python và Kotlin unit tests.
7. Build APK debug bằng Gradle.
8. Verify APK bằng `apksigner`, `aapt`, `zipalign` và kiểm tra packaged assets/runtime markers.
9. Upload artifact; trên `main` thì publish GitHub Release và tải lại APK để so byte-for-byte.

Artifact của phiên bản này là `Backroom-1.1.77.apk`; release tag là `v1.1.77`.

## Quy tắc quan trọng khi sửa code

- Không bỏ qua patch order. `patch-overlay-visible-height-scale-final.py` phải tiếp tục là finalizer overlay cuối trong runtime patch chain.
- Không đưa lại các legacy UI patches đã bị workflow cấm.
- Không resize/crop/normalize source Character/Entity PNG chỉ để làm vừa UI. Scale runtime phải dựa trên visible-height metadata.
- Khi sửa swipe/touch trên Android WebView, phải giữ cuộn dọc và không chặn textarea, button, modal, inventory/equipment interaction.
- Khi thay visual asset được sync trong release build, cập nhật luôn source/hash/dimensions của bước sync để CI không ghi đè bằng asset cũ.
- Bug fix thay đổi hành vi cần có regression test tương ứng; không coi build xanh là đủ nếu packaged runtime contract chưa được kiểm tra.

## Release notes

Release notes theo phiên bản nằm trong `android-apk/RELEASE_NOTES_<version>.txt`. Phiên bản hiện tại dùng:

```text
android-apk/RELEASE_NOTES_1.1.77.txt
```
