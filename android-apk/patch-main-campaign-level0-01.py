from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
PREVIOUS = ROOT / "patch-main-campaign-level0-epsilon.py"

subprocess.run(["python3", str(PREVIOUS)], cwd=ROOT, check=True)

html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0.01 / THE EXIT ? — FALSE PROMISE"
if marker in html:
    print("Main campaign Level 0.01 already installed.")
    raise SystemExit(0)

anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Level 0.01 insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level001Story=`LEVEL 0.01 / THE EXIT ? — FALSE PROMISE

Những tầng nền và bục cao của vùng trước lùi lại phía sau mà không có một ranh giới rõ ràng.

Không có cửa. Không có biển chỉ dẫn. Chỉ đến khi Kai quay lại lần thứ ba, hắn mới xác nhận những khoảng phòng nhiều tầng đã biến mất khỏi đường nhìn.

Phía trước chỉ còn hành lang.

Dài, hẹp và vàng xỉn.

Thảm dưới chân ẩm hơn trước. Không tới mức đọng nước, nhưng mỗi bước ép ra một cảm giác mềm lạnh qua đế giày. Những bóng huỳnh quang nằm cách xa nhau, để lại các quãng ánh sáng yếu nối bằng vùng tối nhợt nhạt.

Lucia dừng ở mép một đoạn hành lang thẳng đến mức điểm cuối chìm vào màu vàng.

“Không thấy đường cũ.”

Kai kiểm tra phía sau thêm một lần. “Tôi cũng vậy.”

Hắn không gọi đó là lối vào. Không ai trong hai người nhìn thấy khoảnh khắc chuyển vùng.

Họ chỉ biết môi trường hiện tại khác với thứ vừa đi qua.

Kai ghi lại điều có thể quan sát: hành lang dài hơn, độ ẩm cao hơn, khoảng cách đèn lớn hơn. Lucia đánh dấu thời điểm lên cạnh một vết phấn mới rồi bổ sung độ cao tương đối như quy trình đã hình thành ở vùng trước.

Hai người đi tiếp.

Sau gần hai mươi phút, họ gặp chữ EXIT.

Bốn ký tự màu đỏ nằm trên một tấm biển nhỏ phía trên khung cửa cuối hành lang.

Lucia không tăng tốc.

Kai cũng không.

Hắn dừng ngoài khoảng mở, kiểm tra chân tường, trần, khe cửa và phần nền trước ngưỡng. Không dây. Không vết kéo. Không dấu chân đủ mới để xác nhận có người vừa đi qua.

“Biển không phải bằng chứng.” Lucia nói.

“Ừ.”

Kai mở cửa từ vị trí lệch khỏi trục.

Phía sau là một hành lang vàng khác.

Cùng loại thảm ẩm. Cùng ánh đèn thưa. Không khí có mùi bụi ướt và vật liệu cũ.

Không có cầu thang. Không có bầu trời. Không có Frontrooms.

Lucia nhìn tấm biển một lần rồi đi qua.

Không ai gọi nó là thất bại. Một lời hứa chỉ có thể thất bại nếu họ từng tin nó.

Nhưng biển EXIT thay đổi cách họ kiểm tra môi trường.

Từ đó, mọi dấu hiệu mang ý nghĩa thoát ra đều bị hạ xuống thành giả thuyết cho tới khi có bằng chứng vật lý. Kai ghi vị trí tương đối của từng biển. Lucia đánh dấu mặt sau khung cửa, nơi ít có khả năng bị nhìn nhầm từ xa.

Biển thứ hai xuất hiện hơn một giờ sau.

Nó không có cửa bên dưới.

Chỉ có chữ EXIT gắn lên tường.

Biển thứ ba chỉ về một lối rẽ trái. Sau bảy đoạn hành lang, tuyến đó đưa họ trở lại chính biển thứ ba từ hướng ngược lại.

Lucia nhìn dấu phấn ở mặt sau tường. “Cùng dấu.”

“Cùng vết xước bên dưới.”

Không phải một bản sao mà họ có thể phân biệt bằng mắt.

Kai không kết luận hành lang dịch chuyển hay thời gian lặp. Họ không quan sát được cơ chế. Dữ kiện duy nhất là một tuyến đi liên tục đã trả họ về cùng một mốc trong quan hệ hướng không phù hợp với đường vừa đi.

Họ đổi tuyến.

Càng sâu, sự đơn điệu càng trở nên khó chịu theo một cách khác tiếng hum-buzz trước đó.

Ở vùng nhiều tầng, môi trường buộc mắt phải làm việc liên tục. Ở đây, gần như không có gì mới để bám vào. Cùng màu tường. Cùng độ ẩm. Cùng những khoảng đèn cách nhau. Sau hàng chục lần rẽ, trí nhớ bắt đầu muốn tự điền những chi tiết mà mắt không chắc đã thấy.

Kai dừng mỗi khi nhận ra mình sắp gọi một đoạn hành lang là “đoạn lúc nãy” chỉ vì nó giống đoạn lúc nãy.

Lucia làm điều tương tự.

Hai người bắt đầu đọc lại mốc cho nhau trước khi quyết định quay đầu.

Không phải để kiểm tra trí nhớ của người kia.

Để ngăn trí nhớ của chính mình trở thành bằng chứng.

Ở một đoạn tối hơn, Lucia dừng đột ngột.

Kai dừng theo quy trình, mắt quét phía trước thay vì quay ngay sang cô.

“Nghe thấy gì?”

“Không.”

Một nhịp.

“Đó là vấn đề.”

Kai nhận ra tiếng đèn đã biến mất.

Không tiếng bước chân khác. Không tiếng thở lạ. Chỉ là một khoảng hành lang mà âm nền quen thuộc bị cắt sạch.

Họ không gọi sự im lặng là Entity.

Kai lùi một bước. Tiếng huỳnh quang trở lại rất nhỏ phía sau.

Lucia đánh dấu ranh giới âm thanh lên tường.

Hai người vòng sang tuyến khác thay vì bước sâu vào vùng chưa hiểu chỉ để thỏa tò mò.

Sự lựa chọn đó tốn thêm thời gian.

Nó cũng giữ họ còn đủ tỉnh táo để nhận ra biển EXIT tiếp theo không giống những biển trước.

Không phải vì hình dạng.

Vì phía dưới nó có một dấu phấn.

Lucia giơ tay trước khi Kai tới gần.

“Không phải của tôi.”

Kai quan sát từ xa. Nét trắng đã cũ, cạnh bị ẩm làm nhòe. Một mũi tên đơn giản chỉ xuống hành lang bên phải.

Dấu của một người khác là khả năng hợp lý.

Nhưng người đó là ai, còn sống hay không, đã đi qua khi nào, và mũi tên có còn chỉ đúng tuyến hay không — họ không biết.

Kai không gọi Iris.

Không gọi Syvial.

Một nét phấn không phải bằng chứng rằng một trong hai người đã ở đây.

Lucia cũng không nói tên họ.

“Ghi lại?”

“Ghi. Không theo ngay.”

Họ kiểm tra hai tuyến còn lại trước, giữ mũi tên như một dữ kiện chứ không biến nó thành mệnh lệnh từ một người vắng mặt.

Cuối cùng, tuyến bên phải vẫn là lựa chọn ít rủi ro nhất: nền nguyên, ánh sáng liên tục hơn, không có vùng im lặng vừa phát hiện.

Họ đi theo nó vì điều kiện hiện tại, không phải vì mũi tên.

Sau nhiều hành lang nữa, màu vàng bắt đầu nhạt đi. Một số khung cửa xuất hiện hai bên, phần lớn mở vào những căn phòng trống giống Level 0 nhưng sâu hơn và tối hơn. Xa phía trước, hình chữ nhật của một cánh cửa đứng mở, để lộ bóng của thứ giống kệ hoặc thùng.

Kai dừng trước khi tới đó.

Lucia đứng chếch phía sau, giữ góc còn lại.

“Khác rồi.”

“Ừ.”

Không ai gọi nó là lối thoát.

Sau tất cả những biển EXIT không dẫn ra ngoài, hai người đã kiếm được một nguyên tắc đáng tin hơn bất kỳ chữ viết nào trên tường:

Tên của một nơi không quyết định nơi đó dẫn tới đâu.

Chỉ có kiểm chứng mới làm được việc ấy.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'exploration:{sublevelId:"SUBLEVEL.00.EPSILON",difficulty:{rating:3,source:"PROJECT_DESIGNED",wikiClass:"UNVERIFIED"}}':
        'exploration:{sublevelId:"SUBLEVEL.00.01",difficulty:{rating:2,source:"WIKI_DIRECT",wikiClass:"CLASS 2"}}',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.EPSILON.COMPLETE",nextBeat:"STORY.LEVEL0.01.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE","STORY.LEVEL0.EPSILON.ENTRY","STORY.LEVEL0.EPSILON.COMPLETE"]}':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.01.COMPLETE",nextBeat:"STORY.LEVEL0.1.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE","STORY.LEVEL0.EPSILON.ENTRY","STORY.LEVEL0.EPSILON.COMPLETE","STORY.LEVEL0.01.ENTRY","STORY.LEVEL0.01.COMPLETE"]}',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.EPSILON",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}':
        'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.01",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.EPSILON",turn:1,type:"story-beat",fact:"Kai and Lucia traversed the larger multi-level structural drift of Level epsilon without inventing an exit, resident Entity, or Async attribution."}]':
        '{id:"EVT.1.LEVEL0.EPSILON",turn:1,type:"story-beat",fact:"Kai and Lucia traversed the larger multi-level structural drift of Level epsilon without inventing an exit, resident Entity, or Async attribution."},{id:"EVT.1.LEVEL0.01",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Level 0.01, treating EXIT signage, route loops, silence, and an unknown chalk mark as observations rather than proof of escape, Entities, or their missing teammates."}]',
    '{id:"KNOW.STORY.LEVEL0.EPSILON",factId:"STORY.LEVEL0.EPSILON.COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        '{id:"KNOW.STORY.LEVEL0.EPSILON",factId:"STORY.LEVEL0.EPSILON.COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.01",factId:"STORY.LEVEL0.01.COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    '{id:"THREAD.MAIN.LEVEL0.EPSILON",status:"resolved",turn:1,fact:"Traverse the multi-level structural anomaly without mistaking environmental change for an exit or Async evidence."}]':
        '{id:"THREAD.MAIN.LEVEL0.EPSILON",status:"resolved",turn:1,fact:"Traverse the multi-level structural anomaly without mistaking environmental change for an exit or Async evidence."},{id:"THREAD.MAIN.LEVEL0.01",status:"resolved",turn:1,fact:"Test apparent exit cues and unstable routes in Level 0.01 without promoting signage or unknown marks into unsupported conclusions."}]',
    '{role:"assistant",content:level0EpsilonStory}':
        '{role:"assistant",content:level0EpsilonStory},{role:"assistant",content:level001Story}'
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level 0.01 replacement expected exactly one match, found {count}: {old[:100]}")
    html = html.replace(old, new, 1)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level 0.01 story/state continuation.")
