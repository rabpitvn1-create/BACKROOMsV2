package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityCoreTest {
  @Test public void roamingEntitiesAreEligibleOnEveryCurrentLevel() {
    for (int level = 0; level <= 6; level++) {
      assertTrue("Level " + level + " must allow roaming Entities", EntityCore.roamingAllowedOn(level));
    }
  }

  @Test public void roamingPolicyDoesNotHardCodeCurrentLevelRange() {
    assertTrue(EntityCore.roamingAllowedOn(7));
    assertTrue(EntityCore.roamingAllowedOn(99));
    assertFalse(EntityCore.roamingAllowedOn(-1));
  }
}
