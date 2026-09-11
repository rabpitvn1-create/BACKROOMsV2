from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER"
if marker in html:
    print("Main campaign Level 0 Lucia decision already installed.")
    raise SystemExit(0)

# This patch runs after patch-main-campaign-level0.py. Keep the parent Level 0 identity;
# the next run owns the actual transition into Level epsilon.
anchor = "const initial={"
if html.count(anchor) != 1:
    raise RuntimeError(f"Lucia decision insertion: expected one initial-state anchor, found {html.count(anchor)}")

beat = r'''const level0LuciaDecision=`LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER

Cấu trúc bất thường phía trước không dẫn xuống một tầng khác.

Ít nhất, Kai chưa có bằng chứng để gọi nó như vậy.

Phần nền thấp dần qua ba bậc không đều, luồn dưới một mảng tường bị cắt ngang rồi mở ra một căn phòng rộng hơn. Trần ở đây cao hơn những khu họ vừa đi qua. Hai hàng đèn huỳnh quang chạy lệch nhau, một hàng sáng trắng, hàng còn lại ngả vàng và chớp rất nhẹ ở đầu ống.

Lucia dừng trước bậc đầu tiên.

Kai không vượt qua cô. Hắn nhìn mép thảm, chân tường và phần trần thấp phía dưới khoảng cắt. Không có dấu sập, không có dây, không có vệt kéo hay dấu chân đủ rõ để xác nhận một cái bẫy.

“Không thấy dấu tác động.”

Lucia hạ mắt xuống phần nền. “Cũng không có nghĩa là an toàn.”

“Ừ.”

Hai người đổi góc quan sát rồi đi xuống.

Căn phòng phía dưới có ba lối mở.

Lucia đánh một vạch phấn nhỏ ở mặt trong lối họ vừa đi qua. Kai không phản đối. Dấu phấn không còn đáng tin như bản đồ, nhưng vẫn có giá trị nếu được coi là một phép thử thay vì một lời bảo đảm.

Họ chọn lối bên trái trước.

Sáu phòng sau, họ trở lại căn phòng ba cửa.

Không phải từ lối đã đi vào.

Vạch phấn vẫn nằm ở cửa cũ, phía bên kia phòng.

Lucia nhìn nó một lúc. “Không đủ quãng để vòng lại.”

Kai quay đầu nhìn chuỗi phòng phía sau. “Và hướng vào không khớp.”

Không ai tranh luận với kết quả.

Họ thử lối giữa.

Lần này Kai giữ vị trí tại căn phòng ba cửa trong khi Lucia kiểm tra hai phòng đầu, luôn ở trong khoảng có thể nghe được giọng nói bình thường. Cô báo từng góc rẽ, không phải để dựng một bản đồ hoàn chỉnh mà để xem quan hệ giữa các phòng có giữ ổn định trong vài phút hay không.

“Cửa thứ hai. Rẽ phải.”

Kai nhìn lối mở trước mặt.

Một giây trước nó còn cho thấy ánh đèn vàng của căn phòng kế tiếp.

Bây giờ phía sau khung cửa là một bức tường cách chưa đầy hai mét.

“Dừng.”

Lucia im ngay.

“Đường nhìn vừa đổi.”

Không có câu hỏi thừa. Tiếng bước chân của cô quay lại gần như lập tức.

Nhưng Lucia không xuất hiện ở lối giữa.

Cô bước ra từ lối bên phải.

Cả hai cùng đứng yên.

Lucia nhìn cửa sau lưng mình, rồi nhìn lối giữa nơi cô đã đi vào.

“Không có ngã rẽ nối sang đây.”

“Biết.”

Kai đi tới mép cửa nhưng không bước qua. Hắn kiểm tra mặt tường, góc trần, khoảng cách giữa các đèn. Không có cơ chế chuyển động nào hắn có thể xác nhận.

Hắn chỉ có kết quả.

Khoảng cách ngắn không bảo đảm hai người sẽ giữ được cùng một tuyến.

Lucia xóa một phần vạch phấn bằng cạnh găng tay rồi đánh thêm một nét chéo bên cạnh.

“Đánh dấu này nghĩa là tuyến đã đổi.”

Kai gật đầu. “Giữ lại. Nếu gặp lần nữa thì biết ít nhất nó từng không ổn định.”

Họ không thử tách nhau lần thứ hai.

Trong quãng tiếp theo, cách di chuyển tự hình thành mà không cần ai nhận vai chỉ huy tuyệt đối. Người đi trước kiểm tra góc gần. Người sau giữ đường vừa qua và quan sát những thay đổi mà người trước không nhìn thấy. Khi một người dừng, người kia dừng theo trước khi hỏi lý do.

Không phải vì tin nhau.

Vì phương pháp đó tạo ra kết quả tốt hơn đi một mình.

Đến một căn phòng có cột vuông đứng lệch giữa lối, Kai phát hiện một dải giấy dán tường bị bong ở sát nền. Hắn đã thấy kiểu bong tương tự hai khu trước, nhưng lần này phía dưới lớp giấy không phải lớp tường sáng màu.

Chỉ là vật liệu tối hơn.

Hắn không cạy nó ra.

Lucia cũng không đề nghị.

Không có lý do để biến một khác biệt chưa hiểu thành một cánh cửa chỉ vì họ muốn có lối ra.

Họ vòng qua cột.

“Đang tìm hai người?” Lucia hỏi sau một lúc.

Kai nhìn cô.

“Nghe lúc nãy.”

Hắn nhớ mình đã thử gọi Iris và Syvial vài lần trên kênh nội bộ khi kiểm tra nhiễu. Không có gì cần giấu trong dữ kiện đó.

“Iris và Syvial. Cùng vào với tôi. Bị tách khi qua cổng.”

Lucia không hỏi cánh cổng thuộc về ai. “Biết họ ở đâu không?”

“Không.”

“Có tín hiệu?”

“Không.”

Cô gật một lần. Không an ủi. Không nói họ chắc chắn còn sống. Kai đánh giá cao việc đó.

“Vậy nếu gặp dấu người, kiểm tra trước.”

“Đó đã là kế hoạch.”

Lucia liếc hắn. “Giờ có hai người kiểm tra.”

Câu nói nằm lại giữa tiếng đèn.

Kai không trả lời ngay.

Từ lúc gặp nhau, họ đã có đủ cơ hội để gây nguy hiểm cho nhau. Lucia không cố lấy trang bị của hắn, không bịa ra một lối thoát để buộc hắn đi theo, và khi cấu trúc đổi cô quay lại ngay thay vì tiếp tục một mình. Đổi lại, Kai đã không ép cô giao vũ khí, không giành quyền quyết định tuyến đường và không dùng việc tìm đồng đội làm lý do kéo cô vào một nhiệm vụ không phải của cô.

Đó chưa phải lòng tin sâu.

Nhưng nó đã vượt qua một cuộc đình chiến ở góc hành lang.

Phía trước, tiếng huỳnh quang đổi tông lần nữa.

Không gian mở rộng thành những phòng có tỷ lệ kém đều hơn: một trần nhà cao quá mức cần thiết, một đoạn nền nhô lên như bục, rồi một khoảng thấp chạy sâu dưới dãy tường vàng. Cấu trúc không còn chỉ lặp lại những căn phòng phẳng ban đầu.

Kai dừng trước ranh giới đó.

“Đi tiếp cùng nhau?”

Lucia nhìn vùng kiến trúc méo phía trước, rồi nhìn lại những cửa họ vừa đi qua.

“Cho tới khi có lý do tốt để tách.”

“Được.”

Không có bắt tay.

Không có lời hứa sẽ bảo vệ nhau bằng mọi giá.

Hai người thống nhất ba điều thực tế: không tự ý biến mất khỏi tầm kiểm soát khi chưa báo; không coi dấu đường, tiếng động hay vật thể lạ là bằng chứng cho thứ chưa xác nhận; và tài nguyên của ai vẫn thuộc người đó trừ khi chủ động chia sẻ.

Lucia kiểm tra băng đạn của mình rồi khóa lại túi đựng. Kai rà nhanh trạng thái trang bị. Không ai trao tài nguyên chỉ để chứng minh thiện chí.

Sự hợp tác của họ bắt đầu bằng giới hạn rõ ràng như thế.

Kai bước vào vùng phòng cao trước. Lucia giữ khoảng cách phía sau vừa đủ để có góc quan sát khác hắn.

Ở nơi mà một cánh cửa có thể thôi dẫn tới căn phòng nó vừa nối với, có thêm một người nhìn thấy sự thay đổi không bảo đảm sống sót.

Nhưng đó là lợi thế có thật.

Và hiện tại, thế là đủ.`;

const initial={'''
html = html.replace(anchor, beat, 1)

replacements = {
    'party:[],':
        'party:[{id:"lucia",name:"Lucia"}],',
    'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.FIRST_CONTACT_COMPLETE",nextBeat:"STORY.LEVEL0.LUCIA_DECISION",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE"]},luciaEncounter:{status:"met",level:0,sublevelId:"",partyEligible:true,joinPending:true,knowledge:"human_survivor_confirmed",relationship:"initial_tactical_trust"}':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",nextBeat:"STORY.LEVEL0.EPSILON.ENTRY",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE","STORY.LEVEL0.LUCIA_DECISION_COMPLETE"]},luciaEncounter:{status:"joined",level:0,sublevelId:"",partyEligible:true,joinPending:false,knowledge:"human_survivor_confirmed",relationship:"earned_tactical_trust",romance:"none"}',
    '{id:"EVT.1.LEVEL0.LUCIA_CONTACT",turn:1,type:"story-beat",fact:"Kai met Lucia at Level 0; both confirmed only a limited tactical cooperation and Lucia has not yet joined the party."}]':
        '{id:"EVT.1.LEVEL0.LUCIA_CONTACT",turn:1,type:"story-beat",fact:"Kai met Lucia at Level 0; both confirmed only a limited tactical cooperation and Lucia has not yet joined the party."},{id:"EVT.1.LEVEL0.LUCIA_JOIN",turn:1,type:"party-join",actor:"lucia",fact:"After independently verifying that short routes can disconnect, Kai and Lucia mutually chose to travel together under explicit tactical boundaries."}]',
    'knowledge:[{id:"KNOW.STORY.PROLOGUE.ENTRY",factId:"STORY.PROLOGUE.ENTRY_COMPLETE",turn:1,knownBy:["kai"]},{id:"KNOW.STORY.LEVEL0.GEOMETRY",factId:"STORY.LEVEL0.ARRIVAL",turn:1,knownBy:["kai"]},{id:"KNOW.STORY.LEVEL0.LUCIA",factId:"STORY.LEVEL0.FIRST_CONTACT_COMPLETE",turn:1,knownBy:["kai","lucia"]}]':
        'knowledge:[{id:"KNOW.STORY.PROLOGUE.ENTRY",factId:"STORY.PROLOGUE.ENTRY_COMPLETE",turn:1,knownBy:["kai"]},{id:"KNOW.STORY.LEVEL0.GEOMETRY",factId:"STORY.LEVEL0.ARRIVAL",turn:1,knownBy:["kai"]},{id:"KNOW.STORY.LEVEL0.LUCIA",factId:"STORY.LEVEL0.FIRST_CONTACT_COMPLETE",turn:1,knownBy:["kai","lucia"]},{id:"KNOW.STORY.LEVEL0.LUCIA_DECISION",factId:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    'relationshipChanges:[]':
        'relationshipChanges:[{id:"RELDELTA.1.LUCIA",turn:1,actor:"lucia",fact:"Relationship advanced from stranger contact to earned tactical trust only; no romantic state is established."}]',
    '{role:"gm",text:"LƯỢT 1\\n\\nKai đã gặp Lucia tại Level 0. Hai người mới chỉ xác lập hợp tác chiến thuật tối thiểu; Lucia chưa tự động gia nhập Party. Liên lạc với Iris, Syvial, SRU và Frontrooms vẫn ngoại tuyến. Chưa có Entity cư trú, lối thoát hay dấu vết Async nào được xác nhận. Story beat kế tiếp là quyết định đồng hành trước khi tiến sâu hơn vào Level 0."}':
        '{role:"gm",text:"LEVEL 0 — QUYẾT ĐỊNH ĐỒNG HÀNH\\n\\n"+level0LuciaDecision},{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã tự nguyện chọn tiếp tục di chuyển cùng nhau sau khi kiểm chứng lợi ích chiến thuật của việc có hai góc quan sát. Lucia hiện ở Party với quan hệ earned tactical trust; chưa có tình cảm lãng mạn. Liên lạc với Iris, Syvial, SRU và Frontrooms vẫn ngoại tuyến. Không có lối thoát, Entity cư trú hay dấu vết Async nào được xác nhận từ beat này. Story beat kế tiếp là tiến vào Level ε — Incessant Hum-Buzz; beat hiện tại không tự chuyển sublevel."}'
}
for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia decision state anchor expected exactly one match, found {count}: {old[:90]}")
    html = html.replace(old, new, 1)

migration_anchor = '''const campaignLevel0Signature="LEVEL 0 / THE LOBBY — FIRST CONTACT";
if(state.turn===1&&Array.isArray(state.log)){
  const storyText=state.log.map(x=>String(x&&x.text||"")).join("\\n");
  if(!storyText.includes(campaignLevel0Signature)){
    state=JSON.parse(JSON.stringify(initial));
    localStorage.setItem("backroom-apk-state",JSON.stringify(state));
  }
}
'''
if html.count(migration_anchor) != 1:
    raise RuntimeError("Lucia decision migration anchor missing")
migration = migration_anchor + '''
const campaignLevel0LuciaDecisionSignature="LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER";
if(state.turn===1&&Array.isArray(state.log)){
  const storyText=state.log.map(x=>String(x&&x.text||"")).join("\\n");
  if(!storyText.includes(campaignLevel0LuciaDecisionSignature)){
    state=JSON.parse(JSON.stringify(initial));
    localStorage.setItem("backroom-apk-state",JSON.stringify(state));
  }
}
'''
html = html.replace(migration_anchor, migration, 1)

for required in (
    marker,
    'currentBeat:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE"',
    'nextBeat:"STORY.LEVEL0.EPSILON.ENTRY"',
    'party:[{id:"lucia",name:"Lucia"}]',
    'luciaEncounter:{status:"joined",level:0,sublevelId:"",partyEligible:true,joinPending:false',
    'relationship:"earned_tactical_trust",romance:"none"',
    'beat hiện tại không tự chuyển sublevel',
):
    if required not in html:
        raise RuntimeError("Lucia decision campaign marker missing after patch: " + required)

# This beat is still parent Level 0. Entering epsilon belongs to the next story module.
if 'exploration:{sublevelId:""}' not in html:
    raise RuntimeError("Lucia decision must preserve parent Level 0 sublevel identity")

INDEX.write_text(html, encoding="utf-8")
print("Installed Level 0 Lucia mutual party decision; next beat is Level epsilon entry.")
