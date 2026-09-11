from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
PREVIOUS = ROOT / "patch-main-campaign-level0-23.py"

subprocess.run(["python3", str(PREVIOUS)], cwd=ROOT, check=True)
html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0.41 / DISEASE — DO NOT NAME WHAT YOU HAVE NOT TESTED"
if marker in html:
    print("Main campaign Level 0.41 already installed.")
    raise SystemExit(0)

anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Level 0.41 insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level041Story=`LEVEL 0.41 / DISEASE — DO NOT NAME WHAT YOU HAVE NOT TESTED

Sự xuống cấp mà Kai và Lucia chỉ ghi nhận ở cuối Half Finished không còn xuất hiện thành từng mảng rời rạc.

Nó trở thành nền của cả khu vực.

Những bức tường vàng-xanh tối bị bong và nứt. Thảm ướt, mốc, nặng mùi đến mức Lucia kéo lớp che mặt lên trước khi bước sâu hơn. Ở những chỗ lớp phủ tường mất hẳn, bê tông trần lộ ra như một vết thương không có mép sạch.

Kai không gọi nơi này là “Disease” chỉ vì vẻ mục nát.

Tên không phải bằng chứng về cơ chế.

Họ giảm tốc độ. Không chạm tay trần vào tường. Không quỳ trực tiếp lên thảm. Những vật liệu có dấu mốc hoặc ẩm không được đưa vào kho chung.

Một đoạn hành lang phía trước bị xé ngang bởi một khoảng tối không phản lại ánh sáng theo cách họ chờ đợi. Mép của nó không giống một cánh cửa, cũng không giống bóng đổ thông thường.

Lucia giữ laser xanh ở rìa thay vì chiếu thẳng vào trong.

Kai quan sát từ góc khác.

Cả hai cùng thấy một điều: họ có thể xác nhận mép nhìn thấy được, nhưng không xác nhận được chiều sâu hay thứ nằm phía sau.

Vậy nên họ không bước vào.

Không gọi nó là lối tắt. Không gọi nó là lối ra. Không gọi nó là Entity.

Họ đánh dấu vị trí tương đối rồi chọn tuyến có sàn, tường và khoảng cách kiểm chứng được.

Đi thêm một quãng, Kai nhận ra tiếng bước chân của chính mình không thay đổi nhịp, nhưng một giọt nước đang rơi từ ống trần dường như đứng yên giữa không khí.

Hắn dừng.

Lucia cũng dừng ngay sau tín hiệu tay.

Giọt nước vẫn treo đó.

Không rơi.

Không bắn lên.

Không có chuyển động mà mắt họ nhận ra.

Kai nhìn đồng hồ. Lucia đếm nhịp thở của mình. Cả hai vẫn có thể cử động, trao đổi và quan sát.

“Chỉ ghi hiện tượng,” Lucia nói.

Kai gật.

Họ không kết luận thời gian của toàn khu vực đã dừng. Không kết luận đồng hồ đúng hay sai. Không suy ra nguyên nhân từ một vật thể bất thường.

Hai người lùi lại vài bước.

Ở góc nhìn mới, giọt nước biến khỏi tầm nhìn sau mép ống. Khi tiến sang bên thay vì tiến tới, họ lại thấy nó ở đúng vị trí cũ.

Kai ghi: vật thể quan sát được ở trạng thái bất động tương đối; phạm vi và cơ chế chưa biết.

Lucia ghi cùng dữ kiện bằng từ ngữ của cô.

Không ai nâng giả thuyết của mình thành sự thật chung.

Họ vòng qua khu vực đó.

Mùi ẩm ngày càng nặng. Một lần, Lucia ho khan sau khi đi ngang đoạn thảm phủ mốc dày. Kai lập tức ra hiệu rút khỏi luồng khí tù, nhưng không tuyên bố cô đã nhiễm bệnh. Họ chuyển sang hành lang thông thoáng hơn, kiểm tra nhịp thở và trạng thái của Lucia sau một khoảng nghỉ.

Cơn ho không lặp lại.

Dữ kiện được giữ đúng kích thước của nó: một triệu chứng ngắn sau phơi nhiễm môi trường, chưa đủ để chẩn đoán nguyên nhân.

Lucia nhìn hắn khi hắn đóng mục ghi chú.

“Anh không hỏi tôi có ổn không.”

“Anh kiểm tra rồi.”

“Khác nhau à?”

Kai nhìn lại vùng mốc phía sau. “Câu hỏi có thể nhận câu trả lời sai. Nhịp thở thì khó hơn.”

Lucia im vài giây rồi khẽ gật.

Không phải một khoảnh khắc lãng mạn.

Nhưng cô hiểu thêm cách Kai quan tâm: bằng hành động có thể kiểm chứng, không bằng lời trấn an mà hắn không chắc.

Và Kai hiểu thêm một điều về Lucia: cô chấp nhận sự chăm sóc không cần được tô thành cảm xúc lớn hơn bản chất của nó.

Họ tiếp tục.

Ở một ngã ba, một khoảng tối dạng vết xé xuất hiện trên vách trái. Tuyến phải có thảm ướt hơn nhưng vẫn có sàn liên tục. Tuyến giữa hẹp và lớp tường bong thành từng mảng.

Không tuyến nào hứa hẹn an toàn.

Kai và Lucia chọn tuyến giữa sau khi kiểm tra sàn và luồng khí, vì đó là tuyến duy nhất họ có thể xác minh từng bước mà không phải tiếp xúc trực tiếp với vùng tối hay đi xuyên lớp nước đọng sâu hơn.

Quyết định không đến từ việc biết nơi nào dẫn ra ngoài.

Họ vẫn không biết.

Nó đến từ việc biết đủ để loại bỏ hai rủi ro không cần thiết.

Phía cuối tuyến, vật liệu bắt đầu thay đổi lần nữa. Những khối hình cứng với tỉ lệ không ăn khớp với căn phòng xuất hiện qua các khe nhìn, như những mảnh cấu trúc bị ghép sai kích thước.

Kai dừng ở ngưỡng.

Lucia không bước qua hắn.

Cả hai quan sát đủ lâu để xác nhận sự thay đổi hình học là lặp lại, nhưng không tự nhận đã hiểu quy luật.

Họ đánh dấu điểm kiểm tra tiếp theo.

Disease không cho họ một chẩn đoán.

Nó buộc họ học cách sống sót mà không cần một chẩn đoán: tránh thứ chưa kiểm chứng, theo dõi hậu quả thật, và không để một cái tên biến thành kiến thức giả.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'exploration:{sublevelId:"SUBLEVEL.00.23",difficulty:{rating:3,source:"WIKI_DIRECT",wikiClass:"CLASS 3"}}':
        'exploration:{sublevelId:"SUBLEVEL.00.41",difficulty:{rating:4,source:"WIKI_DIRECT",wikiClass:"CLASS 4"}}',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.23.COMPLETE",nextBeat:"STORY.LEVEL0.41.ENTRY",completed:[':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.41.COMPLETE",nextBeat:"STORY.LEVEL0.5.ENTRY",completed:[',
    '"STORY.LEVEL0.23.ENTRY","STORY.LEVEL0.23.COMPLETE"]}':
        '"STORY.LEVEL0.23.ENTRY","STORY.LEVEL0.23.COMPLETE","STORY.LEVEL0.41.ENTRY","STORY.LEVEL0.41.COMPLETE"]}',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.23",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}':
        'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.41",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.23",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Half Finished by tightening formation when sensory distance diverged, verifying unfinished edges from multiple viewpoints, and recording structural state mismatches without assigning an unobserved cause."}]':
        '{id:"EVT.1.LEVEL0.23",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Half Finished by tightening formation when sensory distance diverged, verifying unfinished edges from multiple viewpoints, and recording structural state mismatches without assigning an unobserved cause."},{id:"EVT.1.LEVEL0.41",turn:1,type:"story-beat",fact:"Kai and Lucia traversed the decayed Level 0.41 environment by avoiding unverified dark tears, limiting contact with mold and wet material, and recording a locally observed Pause-like anomaly without inferring its mechanism or diagnosing Lucia after a transient cough."}]',
    '{id:"KNOW.STORY.LEVEL0.23",factId:"STORY.LEVEL0.23.COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        '{id:"KNOW.STORY.LEVEL0.23",factId:"STORY.LEVEL0.23.COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.41",factId:"STORY.LEVEL0.41.COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    '{id:"THREAD.MAIN.LEVEL0.23",status:"resolved",turn:1,fact:"Traverse Half Finished while countering isolation with close formation and multi-view verification, preserving uncertainty about structural changes and their cause."}]':
        '{id:"THREAD.MAIN.LEVEL0.23",status:"resolved",turn:1,fact:"Traverse Half Finished while countering isolation with close formation and multi-view verification, preserving uncertainty about structural changes and their cause."},{id:"THREAD.MAIN.LEVEL0.41",status:"resolved",turn:1,fact:"Traverse Level 0.41 while treating decay, darkness tears, transient symptoms and Pause-like observations as hazards to manage rather than proof of an unknown mechanism."}]',
    '{role:"assistant",content:level023Story}':
        '{role:"assistant",content:level023Story},{role:"assistant",content:level041Story}'
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level 0.41 replacement expected exactly one match, found {count}: {old[:100]}")
    html = html.replace(old, new, 1)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level 0.41 story/state continuation.")
