from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")
marker = "LEVEL 0 / THE LOBBY — FIRST CONTACT"
if marker in html:
    print("Main campaign Level 0 arrival already installed.")
    raise SystemExit(0)

prologue_anchor = "Turn đầu tiên bắt đầu ở đó.`;\n\nconst initial={"
if html.count(prologue_anchor) != 1:
    raise RuntimeError(f"Level 0 story insertion: expected one prologue anchor, found {html.count(prologue_anchor)}")

level0 = r'''Turn đầu tiên bắt đầu ở đó.`;

const level0Arrival=`LEVEL 0 / THE LOBBY — FIRST CONTACT

Kai bước qua ô cửa.

Căn phòng kế tiếp gần như giống căn phòng trước, nhưng chỉ gần như. Mảng giấy dán phía trái ngả sang một màu vàng xỉn hơn. Hai bóng đèn ở cuối trần lệch nhau vài phân. Ổ cắm mà hắn đã dùng làm mốc không còn nằm ở góc có thể nhìn thấy từ đây.

Hắn quay lại.

Ô cửa vẫn ở sau lưng. Vết sẫm dưới chân tường vẫn còn. Nhưng khoảng cách giữa nó và mép cửa đã dài hơn lúc hắn đứng bên kia.

Kai không vội gọi đó là chuyển động. Hắn đo lại bằng bước chân, nhìn các đường ghép trên trần rồi kiểm tra góc giữa hai bức tường. Kết quả không khớp với hình học vừa quan sát chưa đầy một phút trước.

Hắn ghi nhận điều đó như một dữ kiện, không phải lời giải thích.

Tiếng huỳnh quang bám theo mọi căn phòng.

Sau mười mấy phút, âm thanh ấy thôi còn giống tiếng của từng bóng đèn riêng lẻ. Nó trở thành một lớp áp lực đều đặn nằm sau suy nghĩ. Có lúc Kai tưởng nghe một nhịp chân ở phía bên phải. Hắn dừng lại. Nhịp chân cũng biến mất.

Không có dấu vết để xác nhận đã từng có người ở đó.

Hắn tiếp tục.

Kai thử một phương pháp đơn giản hơn bản đồ. Ở mỗi chỗ rẽ đáng chú ý, hắn ghi nhớ một cụm đặc điểm thay vì tin vào một tọa độ duy nhất: đường giấy dán bị rách, vị trí đèn chết, một tấm trần lệch, mùi ẩm mạnh hơn. Khi gặp lại một cụm gần giống sau đó, ba chi tiết khớp nhưng chi tiết thứ tư sai.

Căn phòng đáng lẽ phải ở bên trái lại mở sang phải.

Một đoạn tường từng không có gì xuất hiện hai ổ cắm.

Vết rách trên giấy dán giống hệt, nhưng nằm cao hơn vai hắn.

Kai đứng trước nó một lúc rồi bật cười rất khẽ.

“Được. Vậy không chơi theo luật đó.”

Hắn bỏ ý định dựng bản đồ tuyệt đối.

Không phải vì mất phương hướng. Vì dữ liệu đã đủ để cho thấy cách đo đang dùng không đáng tin.

Hắn chuyển sang mục tiêu thực tế hơn: giữ khả năng quay lui trong phạm vi ngắn, tìm dấu hiệu tài nguyên, tìm bằng chứng có người khác và tránh tự biến tiếng động thành kẻ thù.

Một lúc sau, hắn tìm thấy chai nhựa nằm sát chân tường.

Kai không chạm vào ngay.

Chai còn nguyên nắp, bên trong là chất lỏng trong suốt. Không nhãn. Không có lý do nào để coi thứ trong đó là nước uống chỉ vì hình thức giống nước. Hắn quan sát vỏ chai, đáy chai và vùng thảm quanh nó rồi để nguyên tại chỗ.

Khát chưa phải vấn đề đủ lớn để đánh đổi bằng một chất lỏng chưa biết.

Hắn đi tiếp.

Mùi mốc thay đổi trước khi cảnh vật thay đổi. Nó đậm hơn ở một dãy phòng thấp trần, nơi thảm ẩm đến mức mỗi bước chân đều ép ra một tiếng rất nhỏ. Kai tránh phần sẫm màu nhất. Không có lý do để thử xem thứ đã ngấm vào đó là gì.

Tiếng ù trên đầu bất chợt hạ thấp.

Kai dừng lại.

Không phải mất điện. Đèn vẫn sáng. Nhưng trong vài giây, một khoảng tần số biến mất khỏi tiếng nền, đủ để một âm thanh khác lọt qua.

Cạch.

Rất xa.

Kim loại chạm vào vật cứng.

Kai xoay người về phía âm thanh. SRU Assault Rifle MK19 lên vai trong một chuyển động liền mạch, nòng súng giữ thấp hơn đường bắn trực tiếp khi chưa có mục tiêu xác nhận.

Hắn không gọi lớn.

Âm thanh có thể là người. Cũng có thể không phải.

Hai phòng sau, Kai thấy dấu đầu tiên không giống sản phẩm của kiến trúc.

Một vạch phấn trắng nằm ngang gần chân tường.

Hắn ngồi thấp xuống, không chạm vào nó. Bột phấn còn bám thành hạt nhỏ trên mép giấy dán. Vạch không đủ để nói ai tạo ra, nhưng nó quá có chủ ý để bị bỏ qua như một vết bẩn ngẫu nhiên.

Ở hành lang kế tiếp có thêm một vạch.

Rồi một vạch nữa.

Dấu thứ ba xuất hiện ở một nơi khiến Kai dừng hẳn.

Cùng kiểu nét, nhưng nằm trên bức tường mà theo chuỗi đường vừa đi, hắn không thể gặp lại nhanh đến thế.

Nếu cùng một người đã đánh dấu chúng, người đó cũng đang gặp vấn đề giống hắn.

Nếu không phải cùng một người, kết luận còn ít chắc chắn hơn.

Kai không đi theo dấu phấn một cách mù quáng. Hắn dùng chúng như một nguồn dữ liệu phụ, giữ khoảng cách với các góc khuất và thay đổi tuyến khi dấu bắt đầu lặp vô lý.

Đến vạch thứ bảy, hắn thấy một chấm sáng xanh quét qua mép cửa phía trước.

Chỉ một lần.

Rất thấp.

Kai lùi nửa bước khỏi đường nhìn trực tiếp.

Chấm sáng trở lại, lướt trên chân tường rồi dừng.

Laser.

Không phải bằng chứng về danh tính người cầm nó. Nhưng ít nhất đó là một thiết bị có mục đích rõ ràng.

Kai giữ súng ở tư thế sẵn sàng và đổi góc tiếp cận.

Phía bên kia bức tường, một tiếng vải cọ nhẹ vào giấy dán. Sau đó im lặng.

Người kia cũng đã phát hiện có thứ gì đó ở gần.

Kai không lao qua góc.

“Có người ở bên này.”

Không có câu trả lời ngay.

Một giọng nữ vang lên sau vài giây, đủ rõ để định hướng nhưng không đủ lớn để vọng xa qua nhiều phòng.

“Đứng nguyên đó.”

Kai dừng.

“Được.”

Một khoảng im lặng khác.

“Đặt súng xuống.”

Kai nhìn khẩu MK19 trong tay rồi nhìn mép cửa.

“Không.”

Không có tiếng quát lại. Chỉ một tiếng dịch chân rất nhẹ.

Kai nói tiếp trước khi sự im lặng bị kéo thành một cuộc thi thần kinh vô ích.

“Có thể hạ nòng. Bỏ súng thì không.”

Lần này câu trả lời đến nhanh hơn.

“Hạ nòng.”

Kai hạ nòng súng xuống sàn nhưng giữ tay trên vũ khí.

Một cô gái bước ra khỏi góc tường sau đó. Trẻ. Tóc đen dài buộc cao. M4A1 nằm chắc trong tay, nòng cũng đã hạ nhưng chưa rời khỏi hướng có thể nâng lên ngay. Trên người là trang bị quân sự đã có dấu sử dụng. Một laser xanh gắn trên súng tắt đi khi cô rời khỏi góc.

Kai nhìn vị trí ngón tay trên cò, cách cô giữ khoảng cách với tường và góc đứng không chắn đường rút của chính mình.

Không phải người chưa từng được huấn luyện.

Cô cũng nhìn bộ giáp SRU và khẩu súng của hắn lâu hơn mức nhìn một người đi lạc bình thường.

Không ai nổ súng.

Đó là dữ kiện quan trọng nhất trong vài giây đầu.

“Lucia.”

Cô nói tên trước, mắt vẫn không rời tay Kai.

“Kai.”

“Đi một mình?”

“Hiện tại.”

Lucia liếc qua phía sau hắn. “Có nghe tiếng gì bám theo không?”

“Có tiếng giống bước chân. Chưa có gì xác nhận.”

Ánh mắt cô dừng lại một nhịp, rồi trở về hành lang.

“Cũng nghe thấy.”

Kai không hỏi cô đã ở đây bao lâu. Không hỏi vì sao có mặt ở Backrooms. Những câu đó có thể chờ. Trước mắt, cả hai đang đứng trong một nơi làm sai khoảng cách và lặp lại dấu đánh dấu.

Hắn chỉ vào vạch phấn gần nhất.

“Dấu này?”

Lucia gật đầu. “Đánh để kiểm đường. Nhưng có vài dấu quay lại trước khi tuyến đi đủ vòng.”

“Vậy cùng kết quả.”

Cô nhìn hắn. “Đã thử lập bản đồ?”

“Bỏ rồi.”

“Khôn đấy.”

Không phải lời khen thân mật. Chỉ là một đánh giá ngắn giữa hai người vừa xác nhận cùng một vấn đề.

Một tiếng cạch vang lên ở rất xa.

Cả hai cùng quay đầu.

Không ai nói đó là sinh vật.

Lucia dịch khỏi giữa hành lang trước. Kai cũng đổi vị trí để không đứng cùng một đường bắn với cô. Hai người không cần thống nhất một đội hình phức tạp; việc tránh chắn nòng súng của nhau là đủ rõ đối với những người đã biết dùng vũ khí.

Tiếng động không lặp lại.

Kai nhìn những dấu phấn trên tường.

“Đang tìm gì?”

“Đường sang khu khác. Nếu may mắn thì ra khỏi chỗ này.”

“Có bằng chứng về lối ra?”

“Chưa.”

Câu trả lời khiến Kai tin phần nào vào cách cô xử lý thông tin hơn bất kỳ lời giới thiệu dài nào.

Không biến hy vọng thành dữ kiện.

Hắn gật nhẹ về phía hành lang chưa được đánh dấu.

“Đi cùng một đoạn. Hai góc quan sát tốt hơn một.”

Lucia không đồng ý ngay. Cô nhìn khẩu MK19, bộ giáp, rồi nhìn lại hành lang phía sau mình.

“Không đi sát.”

“Không cần.”

“Và nếu dấu đường bắt đầu lặp, dừng lại kiểm trước khi chọn hướng.”

“Được.”

Thỏa thuận chỉ có vậy.

Không lời hứa. Không tin tưởng vô điều kiện. Không ai giao mạng mình cho người vừa gặp.

Hai người bắt đầu di chuyển với khoảng cách đủ để không vướng nhau. Lucia tiếp tục dùng phấn ở những điểm cần thiết, nhưng thưa hơn trước. Kai ghi nhớ cụm dấu hiệu môi trường và kiểm các góc mà cô không thể nhìn cùng lúc.

Sau một quãng, tiếng đèn lại thay đổi.

Không tắt.

Chỉ lệch nhịp.

Ở cuối dãy phòng phía trước, trần cao lên một chút. Những mảng tường vàng vẫn còn, nhưng hình dạng căn phòng bắt đầu mất đi sự đều đặn quen thuộc. Một bậc nền xuất hiện ở nơi đáng lẽ phải bằng phẳng. Xa hơn nữa, có thứ giống một lối xuống thấp chạy dưới phần tường bị chia cắt.

Lucia dừng bên cạnh một dấu phấn cũ.

“Dấu này không phải vừa đánh.”

Kai nhìn nó.

Nét phấn đúng kiểu của cô, nhưng bột đã bị ẩm làm nhòe ở mép.

Không ai kết luận ngay rằng họ đã quay lại.

Không gian phía trước đơn giản đã cho họ thêm một dữ kiện xấu: cấu trúc đang thay đổi nhanh hơn những gì hai người có thể tin vào dấu đường.

Kai nhìn vùng nền lệch và khoảng tối thấp phía xa.

Hắn chưa biết nó dẫn đi đâu.

Lucia cũng không.

Nhưng đứng yên trong tiếng huỳnh quang không làm câu trả lời xuất hiện.

Hai người đổi đội hình và tiến về phía cấu trúc bất thường, vẫn giữ đủ khoảng cách để mỗi người có đường rút riêng.

Lần đầu gặp nhau ở Level 0 kết thúc như thế: không phải bằng niềm tin, mà bằng một quyết định nhỏ có thể kiểm chứng — tạm thời không để người còn lại biến mất khỏi tầm quan sát.`;

const initial={'''
html = html.replace(prologue_anchor, level0, 1)

replacements = {
    'storyArc:{current:"MAIN.PROLOGUE",currentBeat:"STORY.PROLOGUE.ENTRY_COMPLETE",nextBeat:"STORY.LEVEL0.ARRIVAL",completed:["STORY.PROLOGUE.ENTRY_COMPLETE"]}':
        'storyArc:{current:"MAIN.LEVEL0",currentBeat:"STORY.LEVEL0.FIRST_CONTACT_COMPLETE",nextBeat:"STORY.LEVEL0.LUCIA_DECISION",completed:["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE"]},luciaEncounter:{status:"met",level:0,sublevelId:"",partyEligible:true,joinPending:true,knowledge:"human_survivor_confirmed",relationship:"initial_tactical_trust"}',
    'events:[{id:"EVT.1.PROLOGUE.ENTRY",turn:1,type:"story-beat",fact:"Kai, Iris and Syvial voluntarily crossed the SRU-monitored Async-linked spatial gate and were separated; Kai arrived alone in Level 0."}]':
        'events:[{id:"EVT.1.PROLOGUE.ENTRY",turn:1,type:"story-beat",fact:"Kai, Iris and Syvial voluntarily crossed the SRU-monitored Async-linked spatial gate and were separated; Kai arrived alone in Level 0."},{id:"EVT.1.LEVEL0.ARRIVAL",turn:1,type:"story-beat",fact:"Kai verified that Level 0 geometry cannot be trusted as linear and adapted his navigation method."},{id:"EVT.1.LEVEL0.LUCIA_CONTACT",turn:1,type:"story-beat",fact:"Kai met Lucia at Level 0; both confirmed only a limited tactical cooperation and Lucia has not yet joined the party."}]',
    'knowledge:[{id:"KNOW.STORY.PROLOGUE.ENTRY",factId:"STORY.PROLOGUE.ENTRY_COMPLETE",turn:1,knownBy:["kai"]}]':
        'knowledge:[{id:"KNOW.STORY.PROLOGUE.ENTRY",factId:"STORY.PROLOGUE.ENTRY_COMPLETE",turn:1,knownBy:["kai"]},{id:"KNOW.STORY.LEVEL0.GEOMETRY",factId:"STORY.LEVEL0.ARRIVAL",turn:1,knownBy:["kai"]},{id:"KNOW.STORY.LEVEL0.LUCIA",factId:"STORY.LEVEL0.FIRST_CONTACT_COMPLETE",turn:1,knownBy:["kai","lucia"]}]',
    '{role:"gm",text:"LƯỢT 1\\n\\nKai đang ở một mình tại Level 0. Liên lạc với Iris, Syvial, SRU và Frontrooms đều ngoại tuyến. Không có lối thoát, Entity hay dấu vết Async nào được xác nhận chỉ từ phần mở đầu. Bạn điều khiển Kai từ đây."}':
        '{role:"gm",text:"LEVEL 0\\n\\n"+level0Arrival},{role:"gm",text:"LƯỢT 1\\n\\nKai đã gặp Lucia tại Level 0. Hai người mới chỉ xác lập hợp tác chiến thuật tối thiểu; Lucia chưa tự động gia nhập Party. Liên lạc với Iris, Syvial, SRU và Frontrooms vẫn ngoại tuyến. Chưa có Entity cư trú, lối thoát hay dấu vết Async nào được xác nhận. Story beat kế tiếp là quyết định đồng hành trước khi tiến sâu hơn vào Level 0."}'
}
for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Level 0 story state anchor expected exactly one match, found {count}: {old[:80]}")
    html = html.replace(old, new, 1)

migration_anchor = '''if(state.turn===1&&Array.isArray(state.log)&&state.log[0]){
  const currentOpening=String(state.log[0].text||"");
  if(!currentOpening.includes(campaignPrologueSignature)){
    state=JSON.parse(JSON.stringify(initial));
    localStorage.setItem("backroom-apk-state",JSON.stringify(state));
  }
}
'''
if html.count(migration_anchor) != 1:
    raise RuntimeError("Level 0 migration anchor missing")
migration = migration_anchor + '''
const campaignLevel0Signature="LEVEL 0 / THE LOBBY — FIRST CONTACT";
if(state.turn===1&&Array.isArray(state.log)){
  const storyText=state.log.map(x=>String(x&&x.text||"")).join("\\n");
  if(!storyText.includes(campaignLevel0Signature)){
    state=JSON.parse(JSON.stringify(initial));
    localStorage.setItem("backroom-apk-state",JSON.stringify(state));
  }
}
'''
html = html.replace(migration_anchor, migration, 1)

for required in (
    marker,
    'current:"MAIN.LEVEL0"',
    'currentBeat:"STORY.LEVEL0.FIRST_CONTACT_COMPLETE"',
    'nextBeat:"STORY.LEVEL0.LUCIA_DECISION"',
    'luciaEncounter:{status:"met",level:0,sublevelId:"",partyEligible:true,joinPending:true',
    'Lucia chưa tự động gia nhập Party',
    'Chưa có Entity cư trú, lối thoát hay dấu vết Async nào được xác nhận',
):
    if required not in html:
        raise RuntimeError("Level 0 campaign marker missing after patch: " + required)

INDEX.write_text(html, encoding="utf-8")
print("Installed main campaign Level 0 arrival and Lucia first-contact beat.")
