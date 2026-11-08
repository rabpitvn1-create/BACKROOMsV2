package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameCoreRulesTest {
  @Test public void directPickupIsRejected() {
    assertTrue(GameCoreRules.isDirectPlayerPickupAction("Cao Minh nhặt chai Almond Water lên"));
    assertTrue(GameCoreRules.isDirectPlayerPickupAction("take the key"));
  }

  @Test public void storageWithdrawalIsNotTreatedAsPickup() {
    assertFalse(GameCoreRules.isDirectPlayerPickupAction("lấy thanh kiếm ra khỏi kho"));
  }

  @Test public void restoreLocksInventoryMutation() {
    assertTrue(GameCoreRules.inventoryMutationLocked("hoàn nguyên vật thể trong nhẫn"));
  }

  @Test public void normalActionDoesNotLockInventory() {
    assertFalse(GameCoreRules.inventoryMutationLocked("Cao Minh kiểm tra hành lang phía trước"));
  }

  @Test public void timeCostIsDeterministic() {
    assertEquals(30, GameCoreRules.estimateMinutes("Cao Minh ngủ một lúc"));
    assertEquals(10, GameCoreRules.estimateMinutes("Cao Minh nghỉ tại đây"));
    assertEquals(5, GameCoreRules.estimateMinutes("Cao Minh chạy sang phòng kế bên"));
    assertEquals(1, GameCoreRules.estimateMinutes("Cao Minh nhìn quanh"));
  }
}
