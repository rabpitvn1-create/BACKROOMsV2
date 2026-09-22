package com.rabpit.backroom.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmNarratorContractTest {

  @Test public void promptContextEstablishesHybridGenreAndCoreRules() {
    String prompt = GmNarratorContract.promptContext();

    assertTrue(prompt.contains("ngôi thứ ba hạn định"));
    assertTrue(prompt.contains("Cao Minh"));
    assertTrue(prompt.contains("Xianxia là lăng kính, Backrooms là thực tại"));
    assertTrue(prompt.contains("thần thức"));
    assertTrue(prompt.contains("ma nguyên"));
    assertTrue(prompt.contains("pháp tắc"));
    assertTrue(prompt.contains("Không tự tiện đổi tên hay gán nhãn vật thể Backrooms"));
    assertTrue(prompt.contains("cánh cửa Backrooms không tự thành cấm chế"));
    assertTrue(prompt.contains("Entity không tự thành yêu thú"));
    assertTrue(prompt.contains("BẢO TỒN QUYỀN ĐIỀU KHIỂN CỦA NGƯỜI CHƠI"));
  }

  @Test public void caoMinhCardProvidesCompactRuntimeFacts() {
    String card = GmNarratorContract.caoMinhNarrativeCard();

    assertTrue(card.contains("Cao Minh / Vạn Giới Ma Tôn"));
    assertTrue(card.contains("Ma Đạo Kiếm Tu"));
    assertTrue(card.contains("thần thức"));
    assertTrue(card.contains("Giới hạn tri thức"));
    assertTrue(card.contains("Không tự quyết định hành động"));
    // Compact card should be less than 800 characters
    assertTrue(card.length() < 800);
  }
}
