from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
PREVIOUS = ROOT / "patch-main-campaign-level0-11.py"

subprocess.run(["python3", str(PREVIOUS)], cwd=ROOT, check=True)
html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0.22 / FULLY REMODELED — USEFUL IS NOT SAFE"
if marker in html:
    print("Main campaign Level 0.22 already installed.")
    raise SystemExit(0)

anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Level 0.22 insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level022Story=`LEVEL 0.22 / FULLY REMODELED — USEFUL IS NOT SAFE

Bê tông ướt kết thúc bằng một đường thẳng quá sạch.

Kai dừng trước ranh giới ấy. Sau lưng hắn, nước của Water Damage vẫn rì rầm quanh những mảng thảm mục. Phía trước, nền chuyển thành thảm xám có hoa văn đều, tường vàng xanh phẳng hơn, các đường nối được che kín. Một miệng thông gió nằm cao gần trần. Xa hơn là một cánh cửa hiện đại với tay nắm kim loại còn nguyên.

Lucia dùng thanh dò chạm nền trước khi bước qua.

Khô.

Cô không nói an toàn.

Kai cũng không.

Họ đứng lại ở mép chuyển tiếp, lau phần nước còn bám trên giày và kiểm tra thiết bị lần nữa. Sau những hành lang ngập, một nơi khô ráo dễ tạo cảm giác được cứu. Chính cảm giác đó khiến Kai dành thêm thời gian quan sát.

Máy sưởi gắn tường không phát nhiệt. Miệng thông gió có bụi nhưng không đủ để kết luận luồng khí đã ngừng bao lâu. Một tấm nẹp chân tường mới hơn bề mặt bên cạnh. Không thứ nào tự nói cho họ biết ai đã sửa nơi này.

Họ đi tiếp.

Các căn phòng giữ cùng một ngôn ngữ công nghiệp: cửa mới, tấm ốp, lỗ thông gió, những đoạn tường trông như vừa được hoàn thiện. Nhưng khoảng cách không giữ được tính nhất quán. Một hành lang mà Lucia đếm bốn cửa lúc đi vào chỉ còn ba cửa khi nhìn lại từ đầu kia.

Kai ghi sự khác biệt.

Không ai quay lại tìm cánh cửa thứ tư.

Ở căn phòng kế tiếp, họ gặp thứ đầu tiên có giá trị rõ ràng: một xe đẩy thấp, vài đoạn ống kim loại, dây buộc, một hộp dụng cụ mở nắp và những mảnh vật liệu xây dựng nằm rải trên sàn.

Lucia quỳ xuống nhưng không chạm ngay.

“Không dấu kéo lê mới. Không dây căng.”

Kai quan sát trần, khe cửa và khoảng tối dưới xe. “Chưa đủ để gọi là sạch.”

Họ kiểm tra từng góc trước.

Không có chuyển động được xác nhận.

Lucia chọn một đoạn dây buộc còn nguyên và một thanh kim loại ngắn có thể dùng làm que dò thay cho thanh đã cong ở vùng ngập. Kai lấy một cuộn băng vật liệu còn kín. Những món khác ở lại.

Không phải vì chúng vô dụng.

Vì mang được không đồng nghĩa nên mang.

Một chiếc máy cầm tay nằm trong hộp dụng cụ còn pin. Kai bật nó trong chưa đầy một giây rồi tắt. Động cơ phản hồi. Không có lý do để tiêu pin chỉ để thỏa mãn việc nó hoạt động.

Họ ghi vị trí tương đối của kho vật liệu theo các mốc cục bộ.

Mười phút sau, cùng một tổ hợp cửa và miệng thông gió xuất hiện phía trước.

Lucia nhìn ký hiệu của mình trên mép tường. Đúng nét. Đúng độ cao.

Nhưng xe đẩy không còn ở đó.

Kai kiểm tra ảnh đã ghi. Trong ảnh, xe đẩy nằm cách ký hiệu chưa đến hai mét.

Hiện tại, khoảng trống ấy chỉ có thảm xám.

“Không quay lại lấy thêm,” Lucia nói.

Kai gật.

Thứ họ vừa học không phải rằng đồ vật biến mất. Họ không nhìn thấy quá trình đó. Điều họ biết là vị trí hiện tại không còn khớp với trạng thái đã quan sát trước đó.

Từ đây, quy tắc thay đổi: vật liệu chỉ được tính là tài nguyên sau khi đã mang theo và kiểm tra. Không lập kế hoạch sống còn dựa trên một kho có thể không còn ở vị trí cũ.

Một tiếng kim loại khẽ vang sau cánh cửa bên trái.

Cả hai dừng.

Lucia dịch khỏi trục cửa. Kai giữ góc còn lại. Họ chờ.

Không có tiếng thứ hai.

Không ai mở cửa chỉ để giải quyết sự tò mò.

Họ chọn tuyến có nhiều mốc quan sát hơn.

Ở một phòng dài, các máy sưởi gắn tường xuất hiện đều đặn nhưng nhiệt độ không đổi. Lucia kiểm tra một chiếc bằng mu bàn tay mà không chạm vào bề mặt. Không hơi nóng. Kai nhìn khe thông gió phía trên. Một luồng khí rất yếu làm sợi bụi dao động.

Thông tin nhỏ, nhưng dùng được.

Họ nghỉ tại nơi có hai lối thoát nhìn thấy được, không phải căn phòng kín trông tiện nghi hơn. Lucia thay thanh dò cong bằng đoạn kim loại vừa lấy. Kai dùng một phần băng vật liệu để gia cố túi chứa thiết bị bị ướt ở Water Damage.

Đây là lần đầu tiên kể từ khi gặp nhau mà môi trường cho họ vật liệu đủ rõ công dụng để sử dụng ngay.

Nó không làm nơi này tử tế hơn.

Lucia đặt đoạn dây buộc còn lại vào phần đồ dùng chung thay vì giữ riêng.

Kai nhìn động tác ấy, rồi ghi nó vào phân bổ vật tư mà không bình luận.

Sự tin cậy giữa họ không tăng vì một câu hứa. Nó tăng vì một món đồ hữu hạn được đặt ở nơi cả hai đều có thể kiểm kê.

Khi rời phòng, họ không lấy thêm thứ gì.

Những bề mặt được sửa sang tiếp tục kéo dài, nhưng càng đi, một số đoạn bắt đầu để lộ phần chưa hoàn thiện: cạnh tường thô, vật liệu xây dựng lộ ra, những khoảng trống có vẻ như công việc đã dừng giữa chừng.

Kai không gọi đó là tiến gần lối ra.

Lucia cũng không.

Họ chỉ ghi nhận rằng mức độ hoàn thiện đang thay đổi.

Và tiếp tục kiểm tra.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'exploration:{sublevelId:"SUBLEVEL.00.11",difficulty:{rating:3,source:"PROJECT_DESIGNED",wikiClass:"UNVERIFIED"}}':
        'exploration:{sublevelId:"SUBLEVEL.00.22",difficulty:{rating:2,source:"PROJECT_DESIGNED",wikiClass:"UNVERIFIED"}}',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.11.COMPLETE",nextBeat:"STORY.LEVEL0.22.ENTRY",completed:[':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.22.COMPLETE",nextBeat:"STORY.LEVEL0.23.ENTRY",completed:[',
    '"STORY.LEVEL0.11.ENTRY","STORY.LEVEL0.11.COMPLETE"]}':
        '"STORY.LEVEL0.11.ENTRY","STORY.LEVEL0.11.COMPLETE","STORY.LEVEL0.22.ENTRY","STORY.LEVEL0.22.COMPLETE"]}',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.11",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}':
        'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.22",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.11",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Water Damage by measuring local depth, current, lighting and landmarks instead of trusting a shifting global route."}]':
        '{id:"EVT.1.LEVEL0.11",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Water Damage by measuring local depth, current, lighting and landmarks instead of trusting a shifting global route."},{id:"EVT.1.LEVEL0.22",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Fully Remodeled, salvaging only verified portable construction materials and refusing to treat apparently useful infrastructure as stable or safe."}]',
    '{id:"KNOW.STORY.LEVEL0.11",factId:"STORY.LEVEL0.11.COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        '{id:"KNOW.STORY.LEVEL0.11",factId:"STORY.LEVEL0.11.COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.22",factId:"STORY.LEVEL0.22.COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    '{id:"THREAD.MAIN.LEVEL0.11",status:"resolved",turn:1,fact:"Traverse Water Damage using local measurements while preserving uncertainty about shifting geometry, water safety, sounds and causes."}]':
        '{id:"THREAD.MAIN.LEVEL0.11",status:"resolved",turn:1,fact:"Traverse Water Damage using local measurements while preserving uncertainty about shifting geometry, water safety, sounds and causes."},{id:"THREAD.MAIN.LEVEL0.22",status:"resolved",turn:1,fact:"Traverse Fully Remodeled while distinguishing verified portable salvage from infrastructure whose persistence, safety and origin remain unknown."}]',
    '{role:"assistant",content:level011Story}':
        '{role:"assistant",content:level011Story},{role:"assistant",content:level022Story}'
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level 0.22 replacement expected exactly one match, found {count}: {old[:100]}")
    html = html.replace(old, new, 1)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level 0.22 story/state continuation.")
