# BACKROOMsV2: ranh giới cố định giữa Snapshot và truyện

Repo: https://github.com/rabpitvn1-create/BACKROOMsV2

Nhánh: ui/reader-divider-clip. Nguồn main: 06ac3e40a2de1dcb5cbc7d1dfd93c262e303c261.

## Yêu cầu đã xác nhận

Một đường kẻ ngang cố định nằm giữa Snapshot và khung kể chuyện. Khi vuốt lên, toàn bộ khung truyện, gồm tiêu đề cảnh, chữ và lựa chọn, trượt lên rồi bị che ở đường phân cách; không đi vào vùng Snapshot. Snapshot và header đứng yên. Không sửa engine, story, save, ảnh hoặc kích thước Snapshot.

Người viết: ChatGPT. Người review dự kiến: chủ nhân/Muse; chưa có phản hồi Muse. Đây là sửa CSS cục bộ theo yêu cầu đã xác nhận, không thêm dependency hoặc thiết kế tính năng mới.

## Hiện trạng đã đọc

android-apk/app/src/main/assets/index.html: header, Snapshot, hr.reader-divider và .game-layout là sibling trong shell cố định chiều cao. Reader chứa toàn bộ story-panel. Main đã tách Snapshot khỏi reader từ PR #175. Divider cũ chỉ cao 3px và sát hai khung.

Đã đọc .agents/skills/ponytail/SKILL.md, web/tests/ui-mechanism.test.mjs, package.json và workflow deploy-pages.yml. Bản Pages được đọc qua HTTP có build 4619da4597d26651, đã có cấu trúc Snapshot ngoài reader; workflow triển khai bản đó thành công. Không kết luận phiên bản trong ảnh 376.png chỉ từ ảnh.

## Thay đổi

* android-apk/app/src/main/assets/index.html: dải nền kín cao 12px nằm ngoài reader, đường đồng 2px ở giữa, có lớp hiển thị riêng phía trên vùng cuộn. Không dùng sticky bên trong truyện.
* .game-layout giữ overflow-y:auto, thêm contain:paint để cắt cả phần trang trí/bóng của nội dung tại mép vùng đọc. Đặt z-index:0 tách khỏi Snapshot và divider. Nội dung biến mất từ mép dưới dải phân cách, dải luôn đứng yên.
* docs/ui-reader-divider-2026-11-10.md: bàn giao này.

## Kiểm chứng và phần mở

Kết quả lượt kiểm cuối ghi sau khi chạy. Test DOM sẵn có kiểm divider nằm ngay sau Snapshot và ngay trước reader, Snapshot ở ngoài vùng cuộn, resetReaderScroll chỉ tác động reader. Không tạo test lặp lại từng giá trị CSS.

Chưa có kiểm tra trực tiếp trên Moto Edge 70 hoặc video cuộn thực tế. Chromium ở lượt trước bị môi trường chặn socket; không dùng jsdom làm bằng chứng đo layout hoặc pixel. Chưa có review Muse. Chưa merge hoặc triển khai thay đổi này.

## Kết quả lượt kiểm cuối

```bash
node web/assemble.mjs /workspace/scratch/d5e16f829b8b/divider-check-site
node --test web/tests/ui-mechanism.test.mjs
git diff --check
```

Đạt: assemble 57 asset, build ebf1ebef4bfc0d68; bộ UI mechanism pass, 0 fail. Các kiểm tra quan hệ DOM, 8 tình huống autoplay/skip, 3 tình huống migration, build ID và chặn desktop đều đạt. Đối chiếu nội dung script inline với HEAD bằng Python xác nhận script game không thay đổi. Diff không có lỗi whitespace.

Không chạy lại bộ playthrough toàn truyện vì thay đổi chỉ nằm trong 4 dòng CSS và main hiện tại đã qua lượt kiểm 147 cảnh ở bước merge PR #175. Chưa có bằng chứng pixel hoặc cuộn trên thiết bị thật; không coi kết quả test DOM là thay thế cho kiểm tra đó.

## Bổ sung trong cùng PR #176: sửa header theo ảnh 377

Chủ nhân yêu cầu header trong ảnh 377 khớp ảnh mẫu ban đầu 362. Header hiện tại dùng border-image jade-frame.png của Snapshot nên có dải ngọc và hoa văn dày không đúng loại. Bổ sung này giữ phần divider đã làm, giữ phạm vi cùng PR, không mở PR mới.

Thay .campaign-header:after bằng skin riêng campaign-header-v3.png do ImageGen tạo theo ảnh 362; nền đen, viền đồng mảnh, mây bốn góc và đuôi mây thấp hai bên. Bỏ border-image Snapshot và nền mực có vết loang trên header. Header cao clamp(58px,15.3vw,72px), nút menu 46×38px thay 40×34px, viền đồng và ba nét vàng. Chữ vẫn là HTML thật; giữ màu/font/title và bố trí trái menu, phải tiêu đề. Giữ nền phủ mép và safe-area padding hiện có.

File bổ sung: android-apk/app/src/main/assets/ui/campaign-header-v3.png. File index.html sửa CSS header ngoài CSS divider. Người viết/review như trên; chưa có review Muse.

Dùng image_gen tích hợp, một ảnh duy nhất, tham chiếu 362.png, transparent_background=true. Output 2172×724 có alpha ngoài dải header; CSS background-size 100% 211.7%, background-position center 46.34% hiển thị dải được vẽ mà không chỉnh/cắt file ảnh.

Prompt:

Use case: precise-object-edit. Asset type: ONE reusable empty header skin for mobile game UI. Input image is the exact STYLE REFERENCE. Reconstruct ONLY the uppermost thin rectangular header surrounding the hamburger menu and MAIN_BACKROOMS title. Remove all text and remove hamburger button entirely, leaving blank interior. This is NOT the green jade scene/snapshot frame below. Match original header: near-black charcoal #101416, subtle fine paper grain, extremely thin weathered desaturated antique brass/bronze outer outline with small rounded corners; delicate faint continuous bronze cloud-scroll linework tucked into all four corners, with longer low horizontal curled cloud tails along bottom left and bottom right. No jade green band, no bright gold, no thick rim, no ornate inset panel, no heavy ink crack or ink blot. Huge quiet black empty center for real HTML title and button. Flat orthographic single horizontal bar, aspect ratio 6.4:1 (e.g. 2048 x 320 actual painted rectangle). Make the bar full-bleed to canvas edges, under 1 percent exterior padding, with truly transparent exterior only beyond the rounded corners, interior opaque. No lettering, numbers, symbols, button, screenshot, scene, characters, sword, phone or watermark. Faithful copy of ONLY the TOP HEADER from reference; do not borrow the lower jade snapshot frame.

Đích là khớp mẫu, chưa khẳng định pixel-perfect khi chưa có screenshot trên điện thoại. Kết quả kiểm lại header ghi dưới đây; phần kiểm divider phía trên là kết quả trước lần bổ sung.

Kiểm lại sau bổ sung header: assemble thành công 58 asset, build ef8163c6ce5ff985; UI mechanism test pass, 0 fail; git diff --check sạch; script inline giữ nguyên. Lệnh như lượt kiểm trên, output assemble lần này là /workspace/scratch/d5e16f829b8b/header-check-site. Chưa kiểm pixel hoặc hiệu ứng cuộn trên Moto Edge 70 thật.
