# Backrooms V2: giao diện ngọc bích và đồng cũ

## Dự án và phạm vi

Repository: https://github.com/rabpitvn1-create/BACKROOMsV2

Nhánh: `ui/reference-jade-bronze`. Nguồn bắt đầu: `b3e1319`.

Mục tiêu: triển khai giao diện theo năm ảnh chủ nhân cung cấp: đọc truyện, Hound, thao tác có Trần Khải, ngăn Hồ sơ, Lịch sử đọc. Tiêu chí đích là khớp bố cục, màu, khung và typography. Chưa xác nhận mức khớp 100% vì chưa chụp được giao diện thực tế.

Người viết: ChatGPT. Người review dự kiến: Muse/chủ nhân. Chưa có phản hồi Muse, không coi là đã được review hoặc đồng thuận.

## Quyết định và nguồn

Nguồn sự thật về runtime: README.md; android-apk/app/src/main/assets/index.html; web/assemble.mjs; web/ui-enhancements.js; .github/workflows/deploy-pages.yml và build-backroom-apk.yml.

Ảnh tham chiếu: 362.png đến 366.png do chủ nhân đính kèm. Không chép các dòng chú thích bên dưới ảnh và hiệu ứng nhòe chữ vào nội dung game. Dùng lại Quicksand, nền và ảnh nhân vật hiện hữu. Giữ nguyên markup, engine, nội dung truyện, save và cơ chế Snapshot sticky.

Thay đổi chỉ là lớp CSS ở cuối style hiện hữu, cùng hai asset trong assets/ui. Không thêm dependency runtime. Khung PNG có alpha thực tại tâm. Nền giấy mực là SVG độc lập, không có tài nguyên bên ngoài. Assembler hiện hữu tự copy và thêm build query vào các URL CSS trong HTML.

## File thay đổi

* android-apk/app/src/main/assets/index.html: theme dùng chung APK và web, khung Snapshot, header, chữ vàng, lựa chọn, drawer và backlog; focus bàn phím và reduced motion.
* android-apk/app/src/main/assets/ui/jade-frame.png: khung GPT-IMG nền trong suốt, 1536 × 1024.
* android-apk/app/src/main/assets/ui/ink-paper.svg: nền mực tối bằng vector.
* docs/ui-reference-theme-2026-11-09.md: bàn giao tự chứa.

## Sinh asset

Dùng công cụ image_gen tích hợp, đầu vào tham chiếu 362.png. Prompt thực tế:

> Use case: background-extraction. Create ONE production game UI decorative frame asset closely reproducing ONLY the rounded landscape snapshot border in reference image, the jade green and weathered old copper frame around the yellow room. Landscape aspect ratio 3:2. Empty truly transparent central opening taking 92 percent width and 89 percent height. Absolutely no room, no characters, no weapons, no text, no black center, no external background. Thin double weathered antique copper edges; dark oxidized teal jade bands; intricate fine Chinese auspicious cloud scroll filigree concentrated at all four corners, exactly like reference. Top left and bottom right clouds curl inward slightly. Frame runs right to canvas edges, with 28px rounded outer corners. Keep edge bands slender, 2 percent of width, corner motifs about 12 percent width. Realistic textured metal with restrained gold highlights, nearly black jade. Entire central area and outside rounded corners truly transparent. Symmetric scalable UI frame, front view, flat orthographic, clean crisp edges.

## Đã kiểm chứng

Một lượt kiểm cuối ngày 2026-11-09:

```bash
node web/assemble.mjs /tmp/backroom-site
cd web/tests
node --test story.test.mjs vn-flow.test.mjs save-store.test.js ui-enhancements.test.mjs ui-mechanism.test.mjs
```

Kết quả: assemble thành công với 52 assets. Cả 5 bộ test pass, 0 fail. Story graph có 88 cảnh reachable. Save-store đạt 7/7 tình huống. VN flow chạy hết nội dung, khôi phục save, nhánh đặt flag; cơ chế autoplay/skip và migration đạt. Git diff không có lỗi whitespace.

Đã đọc metadata PNG: RGBA; pixel tâm (768, 512) có alpha 0.

## Chưa kiểm chứng được và phần còn mở

* Chromium headless cục bộ không khởi động: `socket() failed: Operation not permitted`. Do đó chưa có screenshot, đo layout thực tế hay đối chiếu pixel cho năm trạng thái.
* Trình duyệt từ xa không kết nối được localhost; file URL bị chính sách trình duyệt chặn. Không tiếp tục qua cách lách chính sách.
* Chưa kiểm chứng APK Android thật; môi trường không có lệnh Gradle.
* Chưa xác nhận typography, vị trí nhân vật và từng nét hoa văn khớp 100%. PNG sinh từ mẫu là bản tái dựng, không phải asset thiết kế gốc.
* Chờ Muse/chủ nhân review giao diện tại các viewport điện thoại và máy tính, đặc biệt phần cuộn truyện dưới Snapshot, lựa chọn, Hồ sơ và Lịch sử.
* Chưa merge main, triển khai Pages hoặc phát hành APK. Workflow main hiện tự deploy và phát hành nên nhánh riêng giữ thay đổi ở trạng thái review.
