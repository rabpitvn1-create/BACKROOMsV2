from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
PREVIOUS = ROOT / "patch-main-campaign-level0-22.py"

subprocess.run(["python3", str(PREVIOUS)], cwd=ROOT, check=True)
html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0.23 / HALF FINISHED — TRUST THE EDGE, NOT THE PROMISE"
if marker in html:
    print("Main campaign Level 0.23 already installed.")
    raise SystemExit(0)

anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Level 0.23 insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level023Story=`LEVEL 0.23 / HALF FINISHED — TRUST THE EDGE, NOT THE PROMISE

Những bề mặt hoàn thiện của Fully Remodeled không biến mất cùng lúc. Chúng thưa dần.

Một tấm nẹp chân tường dừng giữa vách. Sau đó là lớp thạch cao chưa sơn. Xa hơn nữa, khung kim loại lộ ra sau những mảng tường chưa khép kín. Văn phòng và không gian giống nhà xưởng nối nhau như một công trình bị bỏ dở giữa ca làm việc, nhưng Kai không thấy công nhân, dụng cụ đang vận hành hay bất kỳ bằng chứng nào cho biết ai đã xây nó.

Lucia dừng ở một mép sàn chưa hoàn thiện. Phần phía trước thấp hơn vài centimet, đủ để một bước bất cẩn làm lệch trọng tâm.

Cô chiếu laser xanh dọc cạnh sàn rồi nói ngắn: “Mép thật.”

Kai tự kiểm tra bằng thanh dò trước khi bước qua.

Cảnh báo của Lucia là lý do để dừng. Không phải lý do để bỏ qua xác minh.

Họ tiếp tục theo cùng nguyên tắc.

Ở đây, một cánh cửa có thể được gắn vào khung tường chỉ mới hoàn thành một nửa. Một hành lang văn phòng kết thúc ở vùng bê tông thô. Những tấm trần treo xuất hiện từng cụm rồi biến mất, để lộ khoảng tối phía trên hệ khung.

Kai không gọi những khoảng tối đó là lối đi.

Lucia cũng không thử leo lên.

Sau vài đoạn rẽ, cảm giác về khoảng cách giữa hai người bắt đầu trở nên khó chịu. Không phải vì họ đã tách nhau. Lucia vẫn ở trong tầm nhìn. Nhưng khi cô đi trước qua một khung cửa, tiếng bước chân của cô nhỏ đi nhanh hơn khoảng cách thực tế đáng lẽ cho phép.

Kai giơ tay.

Lucia dừng ngay.

Họ chỉ cách nhau chưa tới tám mét.

Kai gọi tên cô ở âm lượng nói bình thường.

Lucia nghe thấy, nhưng giọng hắn đến với cô mỏng và xa như từ cuối một hành lang dài.

Cô đáp lại. Kai nhận được cùng hiệu ứng.

Không ai đặt tên cho nguyên nhân.

Họ rút khoảng cách xuống còn bốn mét.

Âm thanh trở lại gần với những gì mắt đang thấy.

Từ đó, quy tắc di chuyển đổi thêm một lần: giữ nhau trong tầm nhìn là chưa đủ. Khi tín hiệu âm thanh không còn tương ứng với khoảng cách quan sát được, họ thu đội hình lại cho tới khi cả hai kênh thông tin cùng ổn định.

Một căn phòng phía trước có ba vách đã hoàn thiện và vách thứ tư chỉ là khung kim loại. Trên nền có bụi xây dựng thành một lớp mỏng. Lucia đánh một dấu nhỏ lên chân khung, đủ thấp để không nhầm với dấu chỉ đường cũ.

Họ đi vòng qua hai phòng liền kề để kiểm tra xem tuyến có nối trở lại hay không.

Khi quay về, dấu vẫn ở đó.

Nhưng một tấm vách trắng giờ che kín khoảng giữa hai thanh khung bên cạnh.

Lucia đứng yên.

Kai lấy ảnh trước đó ra đối chiếu.

Trong ảnh, khoảng ấy trống.

Hiện tại, nó kín.

Không có tiếng khoan. Không tiếng kéo vật liệu. Không ai trong hai người chứng kiến quá trình thay đổi.

“State mismatch,” Kai nói.

Lucia gật. “Không gọi nó tự xây.”

Họ ghi lại cả hai trạng thái.

Không tháo tấm vách mới. Không gõ để khiêu khích phản ứng. Không biến một thay đổi chưa quan sát thành bằng chứng về Entity, Async hay bất kỳ chủ thể nào khác.

Tuyến bên phải dẫn vào một khu văn phòng hẹp hơn. Các bàn chưa lắp hoàn chỉnh nằm sát tường. Một chiếc ghế chỉ có ba chân được dựng cạnh thùng vật liệu rỗng. Kai kiểm tra thùng nhưng không lấy gì; nhãn đã rách và bên trong chỉ còn bụi.

Lucia nhìn qua khung cửa kế tiếp rồi dừng trước khi bước.

“Không thấy mép sàn.”

Kai đổi góc. Từ vị trí của hắn, một dải bê tông tối hơn hiện ra ngay sau ngưỡng cửa. Hắn dùng thanh dò chạm xuống. Nền vẫn tồn tại, nhưng thấp hơn đáng kể.

Hai góc nhìn cho hai lượng thông tin khác nhau.

Họ không tranh xem ai nhìn đúng.

Họ ghép dữ liệu.

Kai giữ ánh sáng ở mép. Lucia hạ người xuống trước, kiểm tra điểm đặt chân rồi ra hiệu. Kai theo sau. Không ai phải cứu ai; quy trình hoạt động trước khi sai lầm xảy ra.

Đó là thay đổi quan trọng hơn một khoảnh khắc anh hùng.

Sự tin cậy giữa họ giờ thể hiện ở việc mỗi người có thể dừng bước chỉ bằng một cảnh báo ngắn của người kia, trong khi quyền tự kiểm chứng vẫn được giữ nguyên.

Không phải tin mù quáng.

Là tin rằng cảnh báo đáng để kiểm tra.

Khu vực sau bậc thấp có mùi ẩm nặng hơn. Một số mảng tường đổi sang vàng-xanh tối và bề mặt bắt đầu xuống cấp thay vì chỉ dang dở. Thảm xuất hiện trở lại ở vài đoạn, nhưng sẫm màu và ẩm.

Kai ghi nhận chuyển đổi vật liệu.

Lucia đánh dấu thời điểm.

Không ai gọi nó là bệnh. Không ai tuyên bố đã sang khu vực khác chỉ dựa trên vẻ ngoài.

Họ tiếp tục cho tới khi các dấu hiệu xuống cấp lặp lại đủ nhiều để việc kiểm tra khu vực kế tiếp trở thành lựa chọn hợp lý.

Half Finished không cho họ một lời hứa về lối ra.

Nó chỉ dạy thêm một điều: trong một nơi không hoàn chỉnh, thứ nguy hiểm nhất không phải khoảng trống nhìn thấy được.

Mà là phần bộ não tự động điền vào khoảng trống ấy.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'exploration:{sublevelId:"SUBLEVEL.00.22",difficulty:{rating:2,source:"PROJECT_DESIGNED",wikiClass:"UNVERIFIED"}}':
        'exploration:{sublevelId:"SUBLEVEL.00.23",difficulty:{rating:3,source:"WIKI_DIRECT",wikiClass:"CLASS 3"}}',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.22.COMPLETE",nextBeat:"STORY.LEVEL0.23.ENTRY",completed:[':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.23.COMPLETE",nextBeat:"STORY.LEVEL0.41.ENTRY",completed:[',
    '"STORY.LEVEL0.22.ENTRY","STORY.LEVEL0.22.COMPLETE"]}':
        '"STORY.LEVEL0.22.ENTRY","STORY.LEVEL0.22.COMPLETE","STORY.LEVEL0.23.ENTRY","STORY.LEVEL0.23.COMPLETE"]}',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.22",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}':
        'luciaEncounter:{status:"joined",level:0,sublevelId:"SUBLEVEL.00.23",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.22",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Fully Remodeled, salvaging only verified portable construction materials and refusing to treat apparently useful infrastructure as stable or safe."}]':
        '{id:"EVT.1.LEVEL0.22",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Fully Remodeled, salvaging only verified portable construction materials and refusing to treat apparently useful infrastructure as stable or safe."},{id:"EVT.1.LEVEL0.23",turn:1,type:"story-beat",fact:"Kai and Lucia traversed Half Finished by tightening formation when sensory distance diverged, verifying unfinished edges from multiple viewpoints, and recording structural state mismatches without assigning an unobserved cause."}]',
    '{id:"KNOW.STORY.LEVEL0.22",factId:"STORY.LEVEL0.22.COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        '{id:"KNOW.STORY.LEVEL0.22",factId:"STORY.LEVEL0.22.COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.23",factId:"STORY.LEVEL0.23.COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    '{id:"THREAD.MAIN.LEVEL0.22",status:"resolved",turn:1,fact:"Traverse Fully Remodeled while distinguishing verified portable salvage from infrastructure whose persistence, safety and origin remain unknown."}]':
        '{id:"THREAD.MAIN.LEVEL0.22",status:"resolved",turn:1,fact:"Traverse Fully Remodeled while distinguishing verified portable salvage from infrastructure whose persistence, safety and origin remain unknown."},{id:"THREAD.MAIN.LEVEL0.23",status:"resolved",turn:1,fact:"Traverse Half Finished while countering isolation with close formation and multi-view verification, preserving uncertainty about structural changes and their cause."}]',
    '{role:"assistant",content:level022Story}':
        '{role:"assistant",content:level022Story},{role:"assistant",content:level023Story}'
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level 0.23 replacement expected exactly one match, found {count}: {old[:100]}")
    html = html.replace(old, new, 1)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level 0.23 story/state continuation.")
