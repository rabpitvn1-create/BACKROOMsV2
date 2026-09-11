from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
PREVIOUS = ROOT / "patch-main-campaign-level0-01.py"

subprocess.run(["python3", str(PREVIOUS)], cwd=ROOT, check=True)

html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0.1 / DEEP EMPTINESS — BORROWED SHELTER"
if marker in html:
    print("Main campaign Level 0.1 already installed.")
    raise SystemExit(0)

anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Level 0.1 insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level01Story=`LEVEL 0.1 / DEEP EMPTINESS — BORROWED SHELTER

Cánh cửa cuối vùng hành lang vàng không đưa họ ra ngoài.

Nó mở vào một hành lang dài hơn.

Hai bên là những khung cửa cách nhau không đều. Sau phần lớn số cửa chỉ có phòng trống: giấy tường vàng bạc màu, thảm cũ, vài góc tối không chứa gì ngoài bụi. Không có dấu hiệu nào đủ để gọi nơi này an toàn.

Kai kiểm tra ba phòng đầu theo cùng một trình tự. Lucia giữ hành lang, đổi góc khi hắn bước vào vùng khuất. Đến phòng thứ tư, cô khẽ gõ hai lần lên khung cửa.

Một chiếc kệ kim loại đứng sát tường.

Bên dưới là hai thùng gỗ cũ.

“Có đồ.”

“Có vật thể,” Kai sửa. “Chưa biết là đồ dùng được.”

Họ không chạm vào ngay.

Kai kiểm tra nền quanh thùng, khe tường, mặt dưới kệ và khoảng trần phía trên. Lucia quan sát lớp bụi. Không có vệt kéo mới, không dấu chân đủ rõ, không dây hay cơ cấu mà họ nhận ra.

Thùng đầu trống.

Thùng thứ hai chứa vài vật liệu đóng gói mục, một cuộn vải khô và hai chai không nhãn. Chất lỏng bên trong trong suốt.

Lucia cầm một chai lên ánh đèn nhưng không mở nắp.

“Không uống.”

“Không có lý do để uống.”

Họ giữ chai như mẫu chưa xác định, tách khỏi nước và vật tư đã biết. Cuộn vải được kiểm tra rồi mới đưa vào phần vật tư dùng được.

Không phải kho báu.

Nhưng sau những hành lang gần như không cho họ thứ gì, một món vật liệu khô có giá trị rất cụ thể.

Họ nghỉ trong căn phòng đó đúng thời lượng đã định trước.

Lucia ngồi gần cửa, súng trong tầm tay. Kai rà lại ghi chép. Những biển EXIT ở vùng trước không còn hữu ích như chỉ dẫn, nhưng chuỗi kiểm chứng đã để lại một thứ tốt hơn: họ biết mình có thể bác bỏ một giả thuyết mà không cần thay nó bằng một câu chuyện khác.

Tiếng động vang lên ngoài hành lang.

Một tiếng cộc.

Rồi im.

Lucia không đứng bật dậy. Kai cũng không rút súng chỉ vì một âm thanh.

Hai người dừng nghỉ sớm.

Kai nhìn qua khe cửa trước. Hành lang trống.

Không có mục tiêu để bắn. Không có dấu vết đủ để gọi đó là Entity.

Họ rời phòng bằng hướng ngược với nơi âm thanh phát ra, vì không có mục tiêu nào đáng để đổi lấy rủi ro kiểm tra một tiếng động không rõ nguồn.

Càng đi sâu, các phòng càng giống nhau. Một số có kệ. Một số có thùng. Phần lớn không có gì.

Chính khả năng tìm thấy vật tư trở thành cái bẫy nhận thức mới.

Sau căn phòng có cuộn vải, Lucia bắt đầu nhìn lâu hơn vào mỗi cánh cửa.

Kai nhận ra điều đó ở cửa thứ mười hai.

“Không cần kiểm tra hết.”

Lucia nhìn dãy cửa phía trước. “Có thể bỏ qua đồ dùng.”

“Cũng có thể tiêu hết thời gian và sự tập trung cho những phòng trống.”

Cô im một nhịp rồi gật đầu.

Họ đặt quy tắc: chỉ kiểm tra phòng khi có dấu hiệu khác biệt quan sát được — kệ, thùng, luồng khí, âm thanh, ánh sáng hoặc dấu vết. Không mở cửa chỉ vì hy vọng.

Quy tắc giúp họ đi nhanh hơn.

Nó cũng khiến căn phòng có ba chiếc kệ ở cuối một đoạn hành lang trở nên đáng chú ý.

Cửa đã mở.

Bụi trên nền bị gián đoạn bởi một vệt mờ chạy từ ngưỡng tới kệ giữa.

Kai ngồi thấp xuống nhưng không chạm vào.

“Cũ hay mới?” Lucia hỏi.

“Không đủ dữ kiện.”

Vệt có thể do giày, vật bị kéo hoặc chính lớp bụi bị thay đổi bởi thứ khác. Không có hình đế rõ. Không có hướng chuyển động chắc chắn.

Trên kệ là một hộp kim loại méo, vài mảnh nhựa và một mẩu giấy đã ẩm tới mức chữ không đọc được.

Không tên.

Không ký hiệu SRU.

Không dấu Async mà họ có thể xác nhận.

Kai chụp lại bố trí trước khi động vào bất cứ thứ gì. Hắn không biến một căn phòng có dấu sử dụng thành bằng chứng rằng Iris hay Syvial từng đi qua.

Lucia kiểm tra cửa sau của căn phòng.

Phía bên kia lại là hành lang.

Nhưng lần này ánh sáng thưa hơn, và những căn phòng mở dọc hai bên sâu tới mức đèn ngoài hành lang không chạm được vào tường cuối.

“Đi tiếp?” cô hỏi.

Kai nhìn lượng vật tư, thời gian đã đi và tuyến phía sau.

“Đi. Nhưng không săn đồ.”

Lucia chỉnh lại dây đeo. “Chỉ lấy thứ có lý do để kiểm tra.”

Đó không phải một lời hứa lớn.

Chỉ là một nguyên tắc sinh tồn được hai người cùng xây từ sai số vừa đủ nhỏ để còn sửa được.

Họ bước vào phần tối hơn của Deep Emptiness, mang theo một cuộn vải khô, hai chai chưa xác định và quan trọng hơn cả: không món nào trong số đó được phép trở thành thứ mà họ mong nó là chỉ vì họ đang cần nó.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'exploration:{sublevelId:"SUBLEVEL.00.01",difficulty:{rating:2,source:"WIKI_DIRECT",wikiClass:"CLASS 2"}}':
        'exploration:{sublevelId:"SUBLEVEL.00.1",difficulty:{rating:5,source:"WIKI_DIRECT",wikiClass:"CLASS 5"}}',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.01.COMPLETE",nextBeat:"STORY.LEVEL0.1.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE","STORY.LEVEL0.EPSILON.ENTRY","STORY.LEVEL0.EPSILON.COMPLETE","STORY.LEVEL0.01.ENTRY","STORY.LEVEL0.01.COMPLETE"]}':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.1.COMPLETE",nextBeat:"STORY.LEVEL0.11.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE","STORY.LEVEL0.EPSILON.ENTRY","STORY.LEVEL0.EPSILON.COMPLETE","STORY.LEVEL0.01.ENTRY","STORY.LEVEL0.01.COMPLETE","STORY.LEVEL0.1.ENTRY","STORY.LEVEL0.1.COMPLETE"]}',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.01",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}':
        'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.1",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.01",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Level 0.01, treating EXIT signage, route loops, silence, and an unknown chalk mark as observations rather than proof of escape, Entities, or their missing teammates."}]':
        '{id:"EVT.1.LEVEL0.01",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Level 0.01, treating EXIT signage, route loops, silence, and an unknown chalk mark as observations rather than proof of escape, Entities, or their missing teammates."},{id:"EVT.1.LEVEL0.1",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Deep Emptiness, finding sparse shelves and crates while keeping unidentified supplies, sounds, and traces unconfirmed."}]',
    '{id:"KNOW.STORY.LEVEL0.01",factId:"STORY.LEVEL0.01.COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        '{id:"KNOW.STORY.LEVEL0.01",factId:"STORY.LEVEL0.01.COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.1",factId:"STORY.LEVEL0.1.COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    '{id:"THREAD.MAIN.LEVEL0.01",status:"resolved",turn:1,fact:"Test apparent exit cues and unstable routes in Level 0.01 without promoting signage or unknown marks into unsupported conclusions."}]':
        '{id:"THREAD.MAIN.LEVEL0.01",status:"resolved",turn:1,fact:"Test apparent exit cues and unstable routes in Level 0.01 without promoting signage or unknown marks into unsupported conclusions."},{id:"THREAD.MAIN.LEVEL0.1",status:"resolved",turn:1,fact:"Traverse Deep Emptiness while treating sparse supplies and ambiguous traces as things to verify rather than answers to current needs."}]',
    '{role:"assistant",content:level001Story}':
        '{role:"assistant",content:level001Story},{role:"assistant",content:level01Story}'
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level 0.1 replacement expected exactly one match, found {count}: {old[:100]}")
    html = html.replace(old, new, 1)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level 0.1 story/state continuation.")