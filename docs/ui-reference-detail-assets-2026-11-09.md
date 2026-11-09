# Backrooms V2: sửa chi tiết giao diện theo ảnh 367, 368, 369

## Phạm vi và nguồn

Repo: https://github.com/rabpitvn1-create/BACKROOMsV2

Nhánh: ui/reference-detail-assets. Nguồn main: 2099407e781b072a4fcc1fbb18edac7235797878.

Mục tiêu: thay khung tiêu đề cảnh, thẻ chức năng, thanh lựa chọn và nền mực tối cho giống các ảnh cận cảnh chủ nhân gửi. Tiêu chí đích là khớp hình thức khi hiển thị trong game; chưa mặc định đạt 100% chỉ vì ImageGen đã tạo ảnh.

Nguồn hiện trạng: android-apk/app/src/main/assets/index.html, web/assemble.mjs, web/ui-enhancements.js, README.md. Chỉ dẫn repo: .agents/skills/ponytail/SKILL.md, mức full.

Người viết: ChatGPT. Người review dự kiến: chủ nhân/Muse; chưa có phản hồi Muse.

## Dữ kiện và quyết định

* Bản trước dùng chung jade-frame.png của Snapshot cho nút chức năng và tiêu đề, làm sai tỷ lệ và loại hoa văn.
* 367.png: thẻ ngọc xanh đen, viền vàng mảnh, bốn góc có nét cuộn nhỏ; chữ vàng là HTML.
* 368.png: lựa chọn có viền đồng ngoài, ngọc mảnh bên trong, mây nét đồng ở bên phải; không dùng ký tự mây Unicode thay hoa văn nữa.
* 369.png: tiêu đề nền đen mực, đường kép cũ rất mảnh và hoa văn nhạt ở hai đầu; không dùng khung Snapshot thu nhỏ.
* Giữ nguyên engine, save, nội dung truyện và markup. Asset chỉ làm nền; chữ, focus và thao tác vẫn là DOM thật. Không thêm dependency runtime.
* Dùng bốn ảnh riêng do image_gen tích hợp tạo tuần tự; không dùng thư viện ảnh khác để vẽ/cắt/chỉnh ảnh. Hai ảnh dài có khoảng trống alpha trên dưới; CSS background-size/position hiển thị đúng dải được vẽ mà không sửa file ảnh.
* Skin active giữ background-image, tránh style nút chung do native/web inject xóa texture khi bấm. Trạng thái autoplay/skip bật vẫn có dấu hiệu sáng hơn.

## File liên quan

* android-apk/app/src/main/assets/index.html: CSS dùng chung web và APK; tách từng skin, bỏ mây Unicode khỏi lựa chọn, chỉnh padding và kích thước nút.
* android-apk/app/src/main/assets/ui/function-jade-v2.png: thẻ nút chức năng.
* android-apk/app/src/main/assets/ui/choice-copper-v2.png: khung lựa chọn có mây bên phải.
* android-apk/app/src/main/assets/ui/scene-header-v2.png: dải tiêu đề cảnh trống chữ.
* android-apk/app/src/main/assets/ui/story-ink-v2.png: nền giấy mực.
* docs/ui-reference-detail-assets-2026-11-09.md: bàn giao này.

## Prompt tạo asset bằng image_gen

Mỗi lần dùng đúng một ảnh tham chiếu và một output. Ba skin dùng transparent_background=true; nền mực dùng false.

### function-jade-v2.png, tham chiếu 367.png

Use case: precise-object-edit. Reference is a close-up of a game interface. Extract/reconstruct ONLY ONE of the small rectangular dark jade function buttons labelled Thao Tác or Đọc nhanh in the reference, removing ALL lettering. Output a single blank production UI button skin, landscape 2.15:1 ratio (approximately 1536x720), front facing orthographic. Reproduce the reference precisely: very thin warm luminous pale yellow gold perimeter with tiny 8px radius, subtle external gold glow, deep almost-black emerald/teal jade stone interior with fine dense mottled grain and uneven oxidized turquoise patches. Fine delicate antique muted gold filigree at exactly FOUR corners: small curls like ornate manuscript corner flourishes, staying within the outer 18% width and 25% height. Straight gold perimeter, NOT the thick copper/large cloud frame of the Snapshot. Keep huge empty dark jade central area for real HTML text. Shape touches canvas at all edges with at most 1% inset. Exterior outside the slightly rounded rectangle transparent, interior opaque textured jade. No text, letters, symbols, other UI, screenshot, caption, characters, montage or watermark. This is an exact faithful asset reconstruction from reference, not a redesign.

### choice-copper-v2.png, tham chiếu 368.png

Use case: precise-object-edit. Reconstruct ONLY ONE full empty long horizontal choice button skin from the two large choice bars at bottom of the supplied reference. NO lettering whatsoever. Match shape and material faithfully. Output a single ultra-wide landscape bar at aspect ratio 8.5:1, e.g. 2040x240 pixels. Front view orthographic. Nearly black charcoal blue-green opaque flat interior (#1a2023). Rounded rectangle with modest 12px corners. Thin worn copper outer rim, inside dark near-black gap, then fine luminous desaturated jade/teal inner rim. NO gold glow, NO bright yellow rim. On lower RIGHT ONLY, an elegant large ancient Chinese cloud flourish made of several delicate continuous antique copper lines, like curling wind/waves. Cloud fills rightmost 17% width and lower 70% height, but has no filled colored area. All other corners bare. Keep the rest of central area totally empty for HTML choice text. No text, glyphs, scenery, other buttons, panels, screenshot or watermark. Full frame touching almost all four canvas edges, under 1% transparent padding. Truly transparent exterior only outside rounded corners. Interior opaque. Reference the lower choice bars specifically, not jade function buttons or Snapshot.

### scene-header-v2.png, tham chiếu 369.png

Use case: precise-object-edit. Edit the supplied narrow title strip into a clean reusable game UI skin: remove ALL text 'BACKROOMS // LEVEL 0' and 'CẢNH 04' while preserving the original frame, dimensions, colors, ink texture and ornament positions with maximum fidelity. Reconstruct ONLY this narrow horizontal strip, aspect ratio 9:1, full width. Dark charcoal-black ink-stained paper, extremely subtle horizontal grain. Very thin desaturated old brass/grey-gold double horizontal rules near top and bottom, double narrow vertical side rules. At left and right ends only, intricate delicate faded antique Chinese dragon/cloud ornamental line art like the reference; asymmetric aged weathering, subdued and ghostlike, NO bright gold, NO teal thick border. Huge empty center remains dark charcoal black for real HTML text. EXACT flat orthographic game header, rectangle with square corners, no extra outer copper rounded frame from above reference. No text, numbers, labels, other UI or watermark. Border almost touches all edges; no large transparent padding. Transparent exterior only, opaque dark center. Match reference exactly rather than redesigning.

### story-ink-v2.png, tham chiếu 368.png

Use case: background-extraction. Reconstruct just the dark ink-paper background texture behind the story text and buttons in the supplied reference. A flat portrait 2:3 texture sheet, full bleed. NO text, NO letters, NO buttons, NO borders, NO gold marks, NO objects or icons. Faithful to reference: nearly black blue charcoal (#0b1114), very subtle fine horizontal fibrous scanline texture, large organic black Chinese ink blooms mainly at bottom and right edge, a few hairline cracks near the edge, faint weathered uneven bluish grey paper sheen. Center and upper left stay calm and extremely dark to support pale yellow HTML typography. Flat 2D texture, zero perspective, no scenery, no gradients to bright colors, no parchment brown, no vignette bright spot, no watermark. Subtle and restrained, almost black, do not make it ornamental. Match the background of supplied interface as closely as possible.

## Kiểm chứng và phần còn mở

Lượt kiểm cuối ngày 2026-11-09:

```bash
node web/assemble.mjs /tmp/backroom-detail-site
node --test web/tests/story.test.mjs web/tests/vn-flow.test.mjs web/tests/save-store.test.js web/tests/ui-enhancements.test.mjs web/tests/ui-mechanism.test.mjs
git diff --check
```

Kết quả thực tế: assemble thành công với 56 asset; cả 5 bộ test pass, 0 fail; diff không có lỗi whitespace. Kiểm bổ sung xác nhận toàn bộ script engine byte-identical với main 2099407, cả bốn URL asset mới tồn tại trong nguồn và bản assemble, các selector dùng chung khung Snapshot và mây Unicode của lựa chọn đã được bỏ.

Chưa kiểm chứng: screenshot so sánh pixel trên điện thoại. Chromium cục bộ từng bị chặn Unix socket nên không dùng kết quả jsdom để tuyên bố pixel-perfect. Chưa build APK cục bộ vì thiếu Gradle; cần xem workflow trên GitHub sau merge.

Chưa coi ảnh raster tái dựng là bằng chứng khớp 100%; còn cần đối chiếu giao diện ở kích thước điện thoại. Kiểm tra APK thật phụ thuộc workflow build và thiết bị. Không tuyên bố Muse đã review.


## Đối chiếu bản web và vòng sửa bổ sung

PR #173 đã merge bằng rebase tại 7f6eed2. Workflow build/phát hành APK và Pages đều success. Trình duyệt công khai đã tải đúng build b505014cc11a5a1e, hiển thị header mới, nền mực và bốn thẻ ngọc; mở/đóng lịch sử hoạt động.

Quan sát trên màn hình rộng phát hiện mây bên phải lựa chọn bị giãn ngang khi background raster co giãn theo toàn chiều rộng thanh. Vòng sửa bổ sung giữ khung đồng/ngọc bằng CSS, hiển thị riêng phần bên phải của raster theo chiều cao với width:auto, không co giãn mây theo chiều rộng lựa chọn. Pseudo-element không có chữ, không chặn input; có padding riêng để không đè nội dung. Giữ nguyên asset ImageGen.

Trong lúc triển khai, main có commit story bf2ebc1 của công việc khác. Vòng sửa lấy main mới này làm nền, giữ nguyên nội dung story đó. Người viết vẫn là ChatGPT, chưa có review Muse cho UI. Kiểm tra lại sau vòng sửa được ghi trong báo cáo cuối và PR tiếp theo; chưa coi desktop screenshot là xác nhận pixel-perfect trên điện thoại.

Kết quả kiểm lại vòng sửa tỷ lệ: assemble thành công (build dbb2aa20c619c069); cả 5 bộ test pass, 0 fail; script engine giữ nguyên so với main bf2ebc1; git diff --check sạch.
