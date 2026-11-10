# BACKROOMsV2: khắc phục web chưa nhận logo

Repo: https://github.com/rabpitvn1-create/BACKROOMsV2

Nhánh: fix/pages-artifact-attempt, nguồn main 62f062f772e5f9f1a08457537521536c8cc70f41.

Mục tiêu: triển khai mã có logo từ PR #176 lên Pages và sửa lỗi trùng tên artifact khi chạy lại deployment. Không sửa UI, engine, story hoặc save. Người viết: ChatGPT; review dự kiến chủ nhân/Muse, chưa có phản hồi Muse. Đã đọc workflow thật và .agents/skills/ponytail/SKILL.md.

## Dữ kiện và nguyên nhân

Trang công khai đọc qua HTTP vẫn có build 4619da4597d26651, h1#title trống và renderer titleEl.textContent=state.title, không có img.campaign-logo. Đây là bản trước PR #176; chưa có căn cứ quy lỗi cho bộ nhớ đệm trên điện thoại.

Run 42715360389 tại main 62f062f build thành công 59 asset, build 8a1dac3c0c009f55; upload thành công nhưng deploy-pages bị kẹt syncing_files rồi timeout sau 10 phút. Lần chạy lại thứ 2 build/upload thành công, sau đó deploy thất bại ngay với lỗi Multiple artifacts named github-pages, count 2. Không xóa artifact để xử lý.

## Sửa

File .github/workflows/deploy-pages.yml dùng cùng tên github-pages-${{ github.run_attempt }} cho input name của upload-pages-artifact và artifact_name của deploy-pages. Mỗi attempt có tên riêng; deploy chỉ chọn gói của attempt hiện tại. Không đổi quyền, audience, action version, timeout hoặc logic build.

Nguồn input chính thức: https://github.com/actions/upload-pages-artifact/blob/main/README.md và https://github.com/actions/deploy-pages/blob/main/README.md.

## Kiểm chứng và phần mở

Kiểm cấu hình upload/deploy đồng nhất và khác tên giữa attempt 1/2; build site và kiểm logo có trong output; git diff --check. Kết quả cụ thể ghi sau khi chạy.

Phân biệt: thay tên artifact khắc phục lỗi retry trùng tên; chưa chứng minh nguyên nhân GitHub Pages từng kẹt syncing_files. Cần deployment thực tế thành công và đọc lại trang công khai cùng URL PNG để xác nhận logo đã lên. Chưa kiểm trực tiếp Moto Edge 70 và chưa có screenshot.

Lượt kiểm cuối: node web/assemble.mjs tạo 59 asset, build 8a1dac3c0c009f55. Python/PyYAML xác nhận input upload/deploy cùng tên, tên khác nhau giữa attempt 1 và 2; HTML output có campaign-logo và URL PNG đã version; PNG tồn tại. git diff --check sạch. Chưa gọi đây là triển khai thành công cho đến khi workflow thực tế và HTTP công khai được xác nhận.
