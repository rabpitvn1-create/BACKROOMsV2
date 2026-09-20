# The BACKROOMS — Drive Source Map for Runtime Knowledge

## Authority order

1. User instruction trong lượt hiện tại.
2. Text Game / Game Master hard rules.
3. Live save/campaign state.
4. Current machine-readable character canon.
5. On-demand world/level/entity canon.
6. Legacy logs hoặc mirrors chỉ dùng khi không xung đột với nguồn mới hơn.

## Current machine-readable character canon

- `app/src/main/assets/knowledge/characters_current.json`
- `app/src/main/assets/knowledge/knowledge_db.json`

Hai file trên đã được đồng bộ sang **Cao Minh R15**. Không được phục hồi dữ kiện player-character từ mirror hoặc save cũ nếu mâu thuẫn.

## Character Drive audit

- `Cao_Minh_Codex.docx` — Drive ID `1TDBphEo1wxrdlRXTI9WUOJPinmWv1PHq` — R15.
- `Iris_Codex.docx` — nguồn riêng của Iris.
- `Syvial_Codex.docx` — nguồn riêng của Syvial.
- `Lucia_Codex.docx` — nguồn riêng của Hứa Thuý Mai / Lucia Lục.

### Cao Minh source map

| Domain | Anchor / runtime key | Current lock |
| --- | --- | --- |
| Identity | `CAO-QUICK-01` | Cao Minh / Vạn Giới Ma Tôn; Ma Đạo Kiếm Tu thuần tiên hiệp; không có thân phận quân sự/công nghệ hiện đại hoặc tương lai. |
| Backrooms entry | `CAO-BACKROOMS-01` | Đã đứng ở đỉnh cao thế giới nguyên sinh, rơi vào Level 0 ngoài ý muốn, rồi chủ động khám phá vì Backrooms là một thế giới chưa hiểu. |
| Vạn Quỷ Ma Tâm | `CAO-CORE-VQM-01` | Ma nguyên gần như vô tận; hồi phục và tự phục hồi trang bị bản mệnh; không tự thêm mana/cooldown/tha hóa. |
| Vạn Quỷ Ma Thân | `CAO-VQMT-01` | Giải phóng toàn diện, không mất lý trí hoặc quyền kiểm soát. |
| Huyết Ma Kiếm | `CAO-EQP-HUYET-MA-KIEM-01` | Đại kiếm bản mệnh, kiếm thể + Huyết Sát Ma Khí + ngự kiếm. |
| Huyết Ma Chiến Khải | `CAO-EQP-HUYET-MA-KHAI-01` | Ma khải từ Ma Kim/ma văn/tinh huyết/ma nguyên, không phải công nghệ. |
| Vạn Tàng Giới | `CAO-EQP-VAN-TANG-01` | Kho tiểu không gian vật vô tri + Phản Bổn; không copy/create sinh linh hoặc vật mới. |
| Ultimate | `CAO-ULT-HUYETMA24-01` | Huyết Ma Nhị Thập Tứ Trảm: đúng 24 trảm, ngoại giới dừng hoàn toàn. |
| Diệp Minh | `CAO-REL-DIEP-MINH-OPEN-01` | Có thù; nguyên nhân/quá khứ/kết cục OPEN. |

### Relationship locks

- Cao Minh ↔ Iris: không có quan hệ có sẵn; first contact nếu gặp.
- Cao Minh ↔ Syvial: không có quan hệ có sẵn; first contact nếu gặp.
- Cao Minh ↔ Lucia: OPEN cho đến khi live continuity xác lập.
- Cao Minh ↔ Diệp Minh: có thù, chi tiết OPEN.

## Runtime notes

- Internal player id: `cao_minh`.
- Combat skills: Huyết Ma Tứ Liên, Ma Tâm Trấn Hồn, Huyết Ảnh Ma Độn, Thiên Ma Bộ, Huyết Ma Nhị Thập Tứ Trảm.
- Default equipment set: Huyết Ma Kiếm, Huyết Ma Chiến Khải, Vạn Tàng Giới.
- CharacterEncounterCore sở hữu spawn/join character.
- Gameplay numbers không tự biến thành lore.
- New-game canon version: `cao-minh-r15`; save thuộc canon cũ được reset về baseline R15 thay vì trộn hai continuity.
