package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameCoreRulesTest {
  @Test public void directPickupIsRejected() {
    assertTrue(GameCoreRules.isDirectPlayerPickupAction("Kai nhặt chai Almond Water lên"));
    assertTrue(GameCoreRules.isDirectPlayerPickupAction("take the key"));
  }

  @Test public void omnivaultWithdrawalIsNotTreatedAsPickup() {
    assertFalse(GameCoreRules.isDirectPlayerPickupAction("lấy W.W Magnum ra khỏi Omnivault"));
  }

  @Test public void restoreLocksInventoryMutation() {
    assertTrue(GameCoreRules.inventoryMutationLocked("hoàn nguyên vật thể trong nhẫn"));
  }

  @Test public void normalActionDoesNotLockInventory() {
    assertFalse(GameCoreRules.inventoryMutationLocked("Kai kiểm tra hành lang phía trước"));
  }

  @Test public void timeCostIsDeterministic() {
    assertEquals(30, GameCoreRules.estimateMinutes("Kai ngủ một lúc"));
    assertEquals(10, GameCoreRules.estimateMinutes("Kai nghỉ tại đây"));
    assertEquals(5, GameCoreRules.estimateMinutes("Kai chạy sang phòng kế bên"));
    assertEquals(1, GameCoreRules.estimateMinutes("Kai nhìn quanh"));
  }

  @Test public void levelIsInferredFromLocation() {
    assertEquals(0, GameCoreRules.levelFromLocation("Level 0 / The Lobby — khu phòng vàng"));
    assertEquals(5, GameCoreRules.levelFromLocation("Level 5 / Terror Hotel"));
    assertEquals(-1, GameCoreRules.levelFromLocation("Unknown corridor"));
  }

  @Test public void levelTransitionsFollowConnectedGraph() {
    assertTrue(GameCoreRules.levelTransitionAllowed(0, 1));
    assertTrue(GameCoreRules.levelTransitionAllowed(1, 0));
    assertTrue(GameCoreRules.levelTransitionAllowed(3, 6));
    assertTrue(GameCoreRules.levelTransitionAllowed(5, 6));
    assertTrue(GameCoreRules.levelTransitionAllowed(4, 4));
    assertFalse(GameCoreRules.levelTransitionAllowed(0, 5));
    assertFalse(GameCoreRules.levelTransitionAllowed(1, 6));
    assertFalse(GameCoreRules.levelTransitionAllowed(-1, 0));
  }
}
