package com.rabpit.backroom.core

data class CharacterSkillDefinition(
  val name: String,
  val kind: String,
  val trigger: String,
  val effect: String,
  val note: String? = null
)

object CompanionSkillCatalog {
  private fun s(name: String, kind: String, trigger: String, effect: String, note: String? = null) =
    CharacterSkillDefinition(name, kind, trigger, effect, note)

  private val iris = listOf(
    s("ARGUS Terrain Read", "PASSIVE", "Bắt đầu combat / tự làm mới", "Analyzed 3 turn: Iris khai thác góc bắn và điểm hở mục tiêu.", "Không nhìn xuyên tường, không tự biết bản thể thật."),
    s("Thousandfold Cognition", "PASSIVE", "Khi Iris bị nhắm", "Tăng tốc xử lý thông tin tối đa 1:1.000 để đọc quỹ đạo và phản ứng.", "Không làm cơ thể hoặc súng nhanh hơn 1.000 lần."),
    s("Twosome Time", "AUTO", "30% mỗi turn hợp lệ", "2 phát chéo góc, 155% Weapon DMG; 170% nếu mục tiêu đang Analyzed."),
    s("Rain Storm", "AUTO", "20% mỗi turn hợp lệ", "6 phát khi đổi góc trên không, tổng 145% Weapon DMG."),
    s("Honeycomb Fire", "AUTO", "20% mỗi turn hợp lệ", "8 phát tập trung, 185% Weapon DMG; Armor Break 20% trong 2 turn."),
    s("Charged Shot", "AUTO", "25% mỗi turn hợp lệ", "175% Weapon DMG, bỏ qua 35% Armor."),
    s("Dead Angle", "COUNTER", "15% sau khi Entity hụt phản công", "Ivory & Ebony phản kích tức thời, 120% Weapon DMG; không chiếm lượt chính."),
    s("ARGUS // Thousandfold Execution", "ULTIMATE", "Tự động mỗi 4 combat turn", "12 phát luân phiên, 300% Weapon DMG; Fully Exposed 2 turn làm giảm 25% Evasion và 20% Armor.", "Không tự phát hiện mục tiêu/bản thể không có dữ liệu.")
  )

  private val syvial = listOf(
    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Miễn cơ chế cạn Mana/Energy/Overheat nội tại; hồi 2% Max HP mỗi turn, 4% khi Devil Trigger.", "Không hồi từ 0 HP."),
    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định", "Đọc chuyển động và chuẩn bị phản đòn; hỗ trợ Counterphase."),
    s("Rift Sever", "AUTO", "30% mỗi turn hợp lệ", "Spatial Shift lệch trục phòng thủ rồi chém, 175% Weapon DMG, bỏ qua 20% Armor."),
    s("Crimson Guillotine", "AUTO", "20% mỗi turn hợp lệ", "190% Weapon DMG; Bleeding 3 turn, mỗi turn 4% Max HP."),
    s("Lucifer Breaker", "AUTO", "20% mỗi turn hợp lệ", "Chuỗi cận chiến + GodKiller 155% Weapon DMG; Stun phản ứng hiện tại của Entity."),
    s("Counterphase", "COUNTER", "30% sau khi Entity hụt phản công", "Spatial Shift vào góc chết và phản chém 125% Weapon DMG; không chiếm lượt chính."),
    s("GodKiller Recall", "PASSIVE", "Khi bị Disarm hợp lệ", "Gọi GodKiller trở lại ở đầu lượt kế tiếp nếu không có luật boss khóa triệu hồi."),
    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% outgoing DMG, +20% Evasion, -20% incoming DMG theo vai trò cá nhân; hồi phục Lucifer Core tăng lên 4% Max HP/turn.", "Không cooldown nội tại, không giới hạn thời gian canon."),
    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger", "Chuỗi Spatial Shift + GodKiller, 210% Weapon DMG; Disoriented -25% Accuracy trong 2 turn."),
    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Mỗi 3 combat turn khi Devil Trigger", "Dừng thời gian ngoại giới, đúng 24 nhát chém x 10 HP = 240 HP; bỏ qua Evasion.", "Không phải instant-kill tuyệt đối.")
  )

  private val anNhien = listOf(
    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên theo Party", "Giảm 25% xác suất hazard trên action vật lý hợp lệ."),
    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi SEARCH", "+10 điểm phần trăm vào generic loot roll hiện có.", "Không tạo loot roll thứ hai."),
    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống xấu", "Ưu tiên vị trí an toàn; không biến An Nhiên thành combatant."),
    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% mỗi combat turn khi ACTIVE trong Party", "Ném vật vô hại để đánh lạc hướng, Entity -25 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage, không dùng vũ khí."),
    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi SEARCH một Exit", "Nếu proc, +20 điểm phần trăm cho Exit probe của action đó."),
    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi kiểm tra nước/chất lỏng khả nghi", "Nếu proc, chặn hazard roll của action kiểm tra đó.", "Chỉ là kiểm tra nguy cơ, không tự biết toàn bộ bản chất vật thể."),
    s("Thôi Để Tôi Làm", "UTILITY", "Khi xử lý thao tác sinh tồn", "Đại diện lợi thế thực dụng trong narration/Game Master; không áp cho hack, phép thuật hoặc công nghệ ngoài khả năng."),
    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Mỗi 5 combat turn khi ACTIVE trong Party", "Tận dụng địa hình: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage.")
  )

  private val kai = listOf(
    s("The Last Requiem", "AUTO", "30% mỗi turn hợp lệ", "4 phát vào khớp vai, 170% Weapon DMG; Bleeding 3 turn x 5% Max HP."),
    s("Silent Lullaby", "AUTO", "20% mỗi turn hợp lệ", "4 phát cùng điểm ngực, 130% Weapon DMG; Stun 1 turn."),
    s("Salvation", "AUTO", "20% mỗi turn hợp lệ", "Dịch chuyển ngắn theo vị trí súng, 2 phát, 147% Weapon DMG."),
    s("Quick Step", "AUTO", "30% mỗi turn hợp lệ", "+50 điểm % Evasion trong 3 turn đối với phản công thường."),
    s("Guilty Crown Override", "ULTIMATE", "Mỗi 3 combat turn", "Đúng 24 phát x 10 HP, Accuracy 200%, bỏ qua Evasion.")
  )

  private val lucia = listOf(
    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia ở trong Party", "+5 điểm phần trăm vào generic loot roll hiện có."),
    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cả Kai và Lucia cùng tấn công", "Lucia có resolution bắn M4A1 riêng và vẫn chịu Entity Evasion gate.")
  )

  fun forCharacter(characterId: String): List<CharacterSkillDefinition> = when (characterId) {
    KAI_ID -> kai
    IRIS_ID -> iris
    SYVIAL_ID -> syvial
    AN_NHIEN_ID -> anNhien
    LUCIA_ID -> lucia
    else -> emptyList()
  }
}
