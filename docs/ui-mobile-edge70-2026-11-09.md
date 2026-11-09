# BACKROOMsV2: giao diện điện thoại và Snapshot cố định

Repo: https://github.com/rabpitvn1-create/BACKROOMsV2

Nhánh: ui/mobile-edge70-fixed-snapshot. Main bắt đầu: 991eb3c.

## Yêu cầu và tiêu chí đạt

Chủ nhân xác nhận sửa các chi tiết còn lệch mẫu 362–366 so với ảnh hiện trạng 371, dùng ChatGPT ImageGen cho asset. Ưu tiên Moto Edge 70, nền phủ đủ bốn cạnh vùng hiển thị trang, nội dung tương thích vùng an toàn và góc bo màn hình. Snapshot đứng ngay dưới header, nằm ngoài vùng truyện cuộn; có đường phân cách để truyện không cuộn đè lên ảnh. Tắt giao diện chơi web trên máy tính.

Người viết: ChatGPT. Người review dự kiến: chủ nhân/Muse, chưa có phản hồi Muse. Không tuyên bố Muse đồng thuận. Không thay engine, nội dung truyện, logic lựa chọn, save format hoặc native Android window flags. Không thêm dependency runtime.

## Nguồn và quyết định

Nguồn runtime: README.md; android-apk/app/src/main/assets/index.html; web/assemble.mjs; web/save-boot.js; web/ui-enhancements.js; web/tests; .github/workflows/deploy-pages.yml. Đã đọc chỉ dẫn .agents/skills/ponytail/SKILL.md ở mức full.

* Snapshot trước đây nằm trong .game-layout có overflow-y:auto, sử dụng sticky. Nay đưa thành sibling phía trước .game-layout, sau header; reader-divider là sibling giữa ảnh và vùng cuộn. resetReaderScroll vẫn đặt scrollTop của .game-layout, nên đổi cảnh không di chuyển ảnh.
* Bỏ margin và giới hạn max-width ngoài; shell phủ 100% chiều ngang và 100dvh. Drawer Hồ sơ phủ toàn bộ vùng trang. Popup có thể chạm mép; vùng nội dung có padding bảo vệ nút, chữ.
* viewport-fit=cover giữ nguyên. env(safe-area-inset-*) áp dụng vào padding nội dung và điều khiển, không tạo lề trống bên ngoài nền. Giới hạn snapshot ở 42dvh để còn vùng đọc trên cửa sổ thấp hoặc xoay ngang. Không khóa kích thước vật lý một model máy; viewport web phụ thuộc mật độ hiển thị, cỡ chữ và thanh trình duyệt.
* Web gate phân loại điện thoại bằng navigator.userAgentData.mobile nếu có, còn lại UA điện thoại Android/iPhone/iPod/Windows Phone. Không dựa chiều rộng: máy tính thu hẹp cửa sổ vẫn bị chặn, điện thoại xoay ngang vẫn hoạt động. Đây là hạn chế giao diện, không phải cơ chế bảo mật chống giả UA. Trình duyệt bật chế độ trang desktop có thể bị chặn.
* Gate chỉ được assembler chèn vào web. Máy tính thấy thông báo mở bằng điện thoại, boot không tải truyện, không đọc/ghi save. APK dùng nguồn chung không có gate này.
* Thay ô tên nhân vật có biểu tượng mây Unicode bằng asset mới; dùng lại skin nút chức năng đã có cho Save/Load. Khung card dùng hoa văn đồng/ngọc mảnh thay khung Snapshot.

Nguồn chuẩn về safe-area và vùng bo: https://www.w3.org/TR/css-env-1/ và https://chromium.googlesource.com/chromium/src/+/HEAD/docs/ui/android/display_cutout.md. Trang web chỉ vẽ trong vùng trình duyệt cấp phát. Không tuyên bố loại bỏ được thanh địa chỉ, thanh hệ thống hay phần kính bo vật lý.

## File thay đổi

* android-apk/app/src/main/assets/index.html: DOM tách Snapshot; divider; bố cục full bleed; safe-area; skin profile, card và Save/Load.
* android-apk/app/src/main/assets/ui/profile-row-v3.png: ảnh ImageGen mới, giữ nguyên output.
* web/mobile-only.js: gate điện thoại, chỉ có trong web assemble.
* web/assemble.mjs: chèn gate, CSS thông báo máy tính, bỏ boot trên máy tính, version asset mới bằng cơ chế sẵn có.
* web/save-boot.js: máy tính không đọc save khi tải trang.
* web/ui-enhancements.js: sửa mô tả Snapshot cho khớp cơ chế mới.
* web/tests/ui-mechanism.test.mjs và vn-flow.test.mjs: cập nhật quan hệ DOM, giả lập UA điện thoại cho web; kiểm máy tính không boot hoặc chạm save.
* Tài liệu này: bàn giao tự chứa.

## ImageGen

Công cụ image_gen tích hợp. Tham chiếu: 365.png, phần Hồ sơ nhân vật. Một ảnh duy nhất, transparent_background=true. Prompt:

Use case: precise-object-edit. Input image is STYLE REFERENCE: recreate just ONE blank character profile row frame from the lower 'Hồ sơ nhân vật' list (Cao Minh / Hạ Liên rows). Asset type: production game UI skin, no text. ONE ultra-wide horizontal rectangular frame, 7.3:1 width:height, across full canvas width. EXACT subdued reference: straight extremely thin worn antique bronze perimeter, dark muted jade inner edges, delicate tiny curling cloud filigree tucked into all FOUR corners. Large empty interior is near black blue charcoal #111619, subtle ink grain. Corners square or almost square. No separate cloud icon at right; no pictograms; no rounded modern pill. Ornament corner size small, maximum 8 percent width. No thick or shiny gold, no bright teal, no glow. Frame occupies whole image width, leave under 1 percent outside padding. Outside frame transparent, interior opaque. Remove ALL letters, names and other controls. Do not draw screenshot, multiple rows, panel, phone, title, objects, captions or watermark. Match lower profile row material, proportions, and understated corner ornamentation faithfully. Return only this single reusable blank row skin.

Output có khoảng alpha trên/dưới. CSS hiển thị dải khung bằng background-size/position; không cắt hoặc chỉnh file ảnh bằng công cụ khác. Tên và thao tác vẫn là DOM thật.

## Kiểm chứng và phần mở

Kết quả lượt kiểm cuối được ghi sau khi chạy. Đích QA mô phỏng gồm 407×904 và vùng trang thấp hơn khi có thanh trình duyệt, dựa trên tỷ lệ ảnh đính kèm 1220×2712. Đây là kích thước mô phỏng, chưa khẳng định viewport CSS thực trên máy chủ nhân. Cần kiểm thực tế Moto Edge 70 về góc bo, browser safe-area và tỷ lệ chữ. Không tuyên bố khớp 100% hoặc đã chạy trên phần cứng thật. Chưa phát hành APK, chưa merge main.

## Kết quả lượt kiểm cuối

```bash
node web/assemble.mjs /workspace/scratch/d5e16f829b8b/mobile-site
node --test web/tests/story.test.mjs web/tests/vn-flow.test.mjs web/tests/save-store.test.js web/tests/ui-enhancements.test.mjs web/tests/ui-mechanism.test.mjs
git diff --check
```

Đã kiểm chứng: assemble thành công, 57 asset, build 12bb0d9a732e506a; 5/5 bộ test pass, 0 fail. Save-store 7/7; story 88 cảnh reachable; playthrough web và standalone đến hết 87 lựa chọn; restore không nhân đôi lịch sử; autoplay/skip và migration hoạt động. Test DOM xác nhận Snapshot ở ngoài reader, divider nằm giữa, reset chỉ cuộn reader. Test desktop tải trang thật qua HTTP trong jsdom xác nhận shell bị ẩn, thông báo hiện, không fetch story và save IndexedDB giữ nguyên. Diff không có lỗi whitespace. Source engine không đổi, chỉ DOM/CSS; assembler thay luồng boot ở bản web theo gate.

Chưa kiểm chứng được: thử khởi động /usr/bin/chromium qua Playwright thất bại với socket() failed: Operation not permitted, nên không có ảnh chụp trình duyệt, không có đo kích thước hoặc đối chiếu pixel ở các viewport dự kiến. Không coi jsdom là kiểm chứng layout thật. Chưa thử Moto Edge 70 vật lý, góc bo và safe-area thực tế; chưa build APK cục bộ vì môi trường không có Gradle. Không thêm dependency hay thay cấu hình bảo mật máy để vượt giới hạn này.

Việc còn mở: chủ nhân/Muse review hình ảnh trên máy thật. Chưa merge main hoặc triển khai Pages trong lượt này; thay đổi được chuẩn bị trên nhánh riêng.
