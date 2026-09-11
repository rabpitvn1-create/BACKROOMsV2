from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
PREVIOUS = ROOT / "patch-main-campaign-level0-lucia-decision.py"

# Materialize the previous campaign state first. Both patches are idempotent, so a clean
# checkout and an already-patched local tree converge to the same source state.
subprocess.run(["python3", str(PREVIOUS)], cwd=ROOT, check=True)

html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL ε / INCESSANT HUM-BUZZ — STRUCTURAL DRIFT"
if marker in html:
    print("Main campaign Level epsilon already installed.")
    raise SystemExit(0)

anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Level epsilon insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level0EpsilonStory=`LEVEL ε / INCESSANT HUM-BUZZ — STRUCTURAL DRIFT

Không có cánh cửa nào ghi tên nơi họ vừa bước vào.

Kai chỉ biết cấu trúc đã đổi.

Những căn phòng vàng thấp và tương đối phẳng của khu trước kéo giãn thành một khoảng lớn đến mức ánh đèn huỳnh quang không còn phủ đều xuống nền. Trần nâng cao thành nhiều tầng. Những bục bê tông và mặt sàn lệch độ cao cắt ngang nhau; phía dưới chúng là các khoảng tối giống đường hầm, còn phía trên có những dải nền hẹp chạy dọc tường như lối kỹ thuật không lan can.

Tiếng ù vẫn ở đó.

Nhưng nó dày hơn.

Không chỉ phát từ một hàng đèn. Âm thanh chồng lên nhau từ nhiều cao độ, tạo thành một lớp rung liên tục khiến việc xác định khoảng cách bằng tai trở nên kém đáng tin.

Lucia dừng lại cạnh Kai. Cô không gọi nơi này là một Level khác. Hắn cũng không.

Họ chưa có dữ kiện cho cái tên đó.

Kai nhìn lên bục cao gần nhất, rồi xuống khoảng hở dưới nền. “Không đi vào đường hầm trước.”

Lucia quan sát miệng tối. “Mất đường nhìn.”

“Và chưa biết nó nối đi đâu.”

Họ chọn tuyến còn giữ được ánh sáng.

Quy tắc vừa thống nhất ở khu trước lập tức có giá trị. Lucia giữ phía sau trong lúc Kai kiểm tra mép bục. Khi nền nâng lên quá cao để nhìn đồng thời cả tầng trên lẫn tầng dưới, họ không tách nhau để tiết kiệm thời gian. Một người đổi vị trí, người kia chờ cho tới khi hai góc quan sát chồng lại.

Chậm hơn.

Nhưng không ai biến mất sau một góc khuất.

Ở đoạn thứ ba, nền phía trước kết thúc đột ngột thành một bậc cao gần ngang ngực. Kai đặt tay lên mép, kiểm tra tải trước khi trèo. Bề mặt chắc. Hắn lên trước rồi quay lại giữ góc quan sát, không chìa tay xuống như thể Lucia không thể tự lên.

Cô dùng điểm bám bên tường, đẩy người qua mép và đứng dậy cạnh hắn.

“Bên trái.”

Kai đã thấy.

Một hành lang vàng chạy ngang phía trên họ, nhưng không có cầu thang nào nối tới. Xa hơn, một đoạn tường bị cắt bởi khoảng trống hình chữ nhật để lộ thêm một tầng khác. Các cấu trúc có vẻ thuộc cùng một công trình nhưng không tuân theo cách một tòa nhà bình thường phải được nối với nhau.

Kai ghi nhận vị trí tương đối, không biến nó thành bản đồ tuyệt đối.

Lucia đánh một dấu phấn nhỏ ở mặt đứng của bục.

Hai người tiếp tục.

Mười phút sau, họ nhìn thấy dấu đó ở phía trên đầu.

Không phải trên mặt đứng.

Nó nằm trên cạnh dưới của một dải nền cao hơn, xoay chín mươi độ so với hướng Lucia đã vạch.

Cô ngẩng lên. “Nét của tôi.”

“Ừ.”

“Không có đoạn nào đưa mình lên đó.”

“Chưa thấy.”

Kai không nói cấu trúc đã xoay. Đó chỉ là một khả năng. Dấu phấn có thể đã đổi vị trí cùng bề mặt, tuyến đường có thể đã nối lại theo cách họ không quan sát được, hoặc quan hệ giữa các khoảng không đơn giản không ổn định như hình học quen thuộc.

Hắn chỉ giữ điều có thể xác nhận: một mốc vật lý không còn ở quan hệ không gian mà hai người đã ghi nhận lúc tạo nó.

Lucia không cố lấy lại dấu. “Từ giờ đánh cả thời điểm.”

“Và độ cao tương đối.”

Cô thêm: “Nếu còn tương đối được.”

Kai nhìn cô một nhịp.

Không phải đùa.

Chỉ là một giới hạn được nói đúng tên.

Tiếng ù bất chợt tăng lên khi một dãy đèn phía xa đồng loạt sáng mạnh. Cả hai dừng cùng lúc. Lucia đưa tay lên gần tai nhưng không tháo thiết bị bảo vệ. Kai theo dõi trần, nền và những khoảng tối thay vì nhìn chằm chằm vào nguồn sáng.

Không có thứ gì lao ra.

Không có tiếng chân thứ ba.

Không có lý do để biến sự thay đổi âm thanh thành một Entity.

Sau vài giây, tiếng ù trở lại mức cũ.

“Ảnh hưởng định hướng.” Lucia nói.

“Có thể.”

“Ít nhất che âm thanh khác.”

“Cái đó xác nhận được.”

Họ điều chỉnh khoảng cách xuống ngắn hơn.

Đó là thay đổi đầu tiên trong cách hai người phối hợp không xuất phát từ lời hứa, mà từ một nguy cơ cùng quan sát thấy. Lucia không cần Kai ra lệnh phải ở gần hơn. Kai cũng không coi việc cô tự điều chỉnh là sự thừa nhận quyền chỉ huy của hắn.

Họ đang xây một quy trình chung.

Ở một khoảng phòng lớn hơn, ba tầng nền giao nhau quanh một cột vuông khổng lồ. Phía dưới cùng có đường hầm tiếp tục chìm vào bóng tối. Tuyến giữa vòng quanh cột. Tuyến trên cao dẫn qua một khe sáng nhưng quá hẹp để giữ hai góc quan sát cùng lúc.

Kai kiểm tra cả ba từ vị trí an toàn trước khi chọn tuyến giữa.

Lucia không phản đối.

Không phải vì cô đã tin mọi quyết định của hắn.

Tuyến giữa đơn giản giữ được nhiều lựa chọn rút lui nhất.

Họ đi được chưa đầy một vòng cột thì bức tường vàng phía sau che mất lối vừa qua.

Không chuyển động.

Không tiếng va.

Nó chỉ ở đó khi cả hai nhìn lại.

Lucia lập tức xoay sang tuyến phía trước. Kai kiểm tra cạnh tường mới, rồi khoảng trống trên đầu. Không có khe cơ khí, bản lề hay dấu trượt.

“Không quay lại được.”

“Hiện tại.” Lucia sửa.

Kai gật đầu.

Một từ chính xác hơn.

Họ không phí đạn vào tường. Không đập phá chỉ để chứng minh có thể. Không lao xuống đường hầm vì sợ bị khóa lại.

Hai người tiếp tục theo tuyến giữa cho tới khi khoảng trần hạ dần, những bục lớn thưa đi và màu vàng trở nên xỉn hơn. Tiếng ù mất bớt một lớp cao tần. Không gian phía trước kéo thành những hành lang dài hơn, thấp hơn, với thảm ẩm và các bóng đèn cách xa nhau hơn.

Kai dừng ở ranh giới ánh sáng.

Hắn nhìn lại.

Các phòng nhiều tầng vẫn ở phía sau, nhưng lối vừa đi qua đã không còn giữ cùng tỷ lệ. Không đủ để hắn tuyên bố họ đã tìm được một quy luật chuyển vùng. Chỉ đủ để biết môi trường phía trước lại khác.

Lucia đứng cạnh, không vượt ranh giới.

“Đi tiếp?”

Kai kiểm tra lần cuối trạng thái trang bị và khoảng cách giữa hai người. “Cùng nhau.”

Lần này câu trả lời không còn là một thử nghiệm giữa hai người lạ.

Nó vẫn chưa phải lòng tin vô điều kiện.

Nhưng sau một vùng mà mặt sàn, độ cao và chính vị trí của dấu đường đều có thể phản lại trí nhớ, cả hai đã có thêm một điều có thể kiểm chứng:

Khi môi trường thay đổi, người kia báo điều mình thấy thay vì bịa ra điều mình muốn tin.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'exploration:{sublevelId:""}':
        'exploration:{sublevelId:"SUBLEVEL.00.EPSILON",difficulty:{rating:3,source:"PROJECT_DESIGNED",wikiClass:"UNVERIFIED"}}',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",nextBeat:"STORY.LEVEL0.EPSILON.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE"]}':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.EPSILON.COMPLETE",nextBeat:"STORY.LEVEL0.01.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE","STORY.LEVEL0.EPSILON.ENTRY","STORY.LEVEL0.EPSILON.COMPLETE"]}',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}':
        'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.EPSILON",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.LUCIA_JOIN",turn:1,type:"party-join",actor:"lucia",fact:"After independently verifying that short routes can disconnect, Kai and Lucia mutually chose to travel together under explicit tactical boundaries."}]':
        '{id:"EVT.1.LEVEL0.LUCIA_JOIN",turn:1,type:"party-join",actor:"lucia",fact:"After independently verifying that short routes can disconnect, Kai and Lucia mutually chose to travel together under explicit tactical boundaries."},{id:"EVT.1.LEVEL0.EPSILON",turn:1,type:"story-beat",fact:"Kai and Lucia traversed the larger multi-level structural drift of Level epsilon without inventing an exit, resident Entity, or Async attribution."}]',
    '{id:"KNOW.STORY.LEVEL0.LUCIA_DECISION",factId:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        '{id:"KNOW.STORY.LEVEL0.LUCIA_DECISION",factId:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.EPSILON",factId:"STORY.LEVEL0.EPSILON.COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    'relationshipChanges:[{id:"RELDELTA.1.LUCIA",turn:1,actor:"lucia",fact:"Relationship advanced from stranger contact to earned tactical trust only; no romantic state is established."}]':
        'relationshipChanges:[{id:"RELDELTA.1.LUCIA",turn:1,actor:"lucia",fact:"Relationship advanced from stranger contact to earned tactical trust only; no romantic state is established."},{id:"RELDELTA.1.LUCIA.EPSILON",turn:1,actor:"lucia",fact:"Shared navigation procedure was reinforced under structural and auditory pressure; relationship remains earned tactical trust with no romance."}]'
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level epsilon state replacement expected one match, found {count}: {old[:80]}")
    html = html.replace(old, new, 1)

signature_anchor = 'window.campaignLevel0LuciaDecisionSignature={currentBeat:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",nextBeat:"STORY.LEVEL0.EPSILON.ENTRY",partyMember:"lucia",relationship:"earned_tactical_trust",romance:"none",sublevelId:"",playerAgency:"mutual-party-decision"};'
if html.count(signature_anchor) != 1:
    raise RuntimeError("Level epsilon signature anchor missing")
html = html.replace(signature_anchor, signature_anchor + '\nwindow.campaignLevel0EpsilonSignature={currentBeat:"STORY.LEVEL0.EPSILON.COMPLETE",nextBeat:"STORY.LEVEL0.01.ENTRY",parentLevel:0,sublevelId:"SUBLEVEL.00.EPSILON",difficulty:3,relationship:"earned_tactical_trust",romance:"none",knowledgeBoundary:"observed-structure-only"};', 1)

# New-game prose follows the campaign state. This remains deterministic initialization;
# ordinary gameplay turns are still player-driven after the opening state is established.
prose_anchor = 'story:level0LuciaDecision,'
if html.count(prose_anchor) != 1:
    raise RuntimeError(f"Level epsilon prose replacement expected one match, found {html.count(prose_anchor)}")
html = html.replace(prose_anchor, 'story:level0EpsilonStory,', 1)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level epsilon story beat.")
