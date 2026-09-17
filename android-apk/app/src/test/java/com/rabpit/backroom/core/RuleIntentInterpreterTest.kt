package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RuleIntentInterpreterTest {
  private val parser = RuleIntentInterpreter()
  private val context = GameContext(GameState.initial())

  private fun parse(text: String) = parser.interpretSync(text, context)

  @Test fun deterministicCommandsStayLocal() {
    assertEquals(GameIntent.PICKUP_ITEM, parse("Kai nhặt chai nước").candidates.single().intent)
    assertEquals(GameIntent.PARTY_JOIN_REQUEST, parse("Iris vào party").candidates.single().intent)
    assertFalse(parse("Kai nhặt chai nước").requiresFallback)
  }

  @Test fun retiredOmnivaultPhrasesNoLongerResolveToLocalCommands() {
    val store = parse("Bỏ khẩu súng vào nhẫn")
    assertEquals(GameIntent.UNKNOWN, store.candidates.single().intent)
    assertTrue(store.requiresFallback)

    val copy = parse("Tạo thêm 3 vỏ chai nước rỗng")
    assertEquals(GameIntent.UNKNOWN, copy.candidates.single().intent)
    assertTrue(copy.requiresFallback)
  }

  @Test fun splitsMultipleActionsWithoutRevivingRetiredVaultIntent() {
    val result = parse("Kai lấy hai chai nước ra khỏi nhẫn rồi đưa Iris một chai")
    assertEquals(listOf(GameIntent.UNKNOWN, GameIntent.TRANSFER_ITEM), result.candidates.map { it.intent })
    assertTrue(result.requiresFallback)
  }

  @Test fun narrativeMemoryNegationAndQuotesDoNotExecute() {
    val samples = listOf(
      "Kai nhìn Iris lấy chai nước",
      "Kai nhớ lần trước mình bỏ súng vào nhẫn",
      "Kai không nhặt chai nước",
      "Iris nói: “nhặt chai nước lên”"
    )
    samples.forEach { assertEquals(GameIntent.NO_ACTION, parse(it).candidates.single().intent) }
  }

  @Test fun unknownRequiresFallback() {
    val result = parse("Kai cân nhắc tình hình kỳ lạ trước mặt")
    assertEquals(GameIntent.UNKNOWN, result.candidates.single().intent)
    assertTrue(result.requiresFallback)
  }
}
