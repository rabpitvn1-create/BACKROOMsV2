package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatChoiceEngineTest {
  @Test public void attackUsesMaximumNeutralLegacyDamage() {
    assertEquals(28, CombatChoiceEngine.maxNormalDamage(30, 2));
    assertEquals(22, CombatChoiceEngine.maxNormalDamage(30, 8));
    assertEquals(1, CombatChoiceEngine.maxNormalDamage(5, 99));
  }

  @Test public void defendDoublesDefenseForIncomingDamage() {
    assertEquals(10, CombatChoiceEngine.normalIncomingDamage(20, 10));
    assertEquals(1, CombatChoiceEngine.defendedIncomingDamage(20, 10));
    assertEquals(8, CombatChoiceEngine.defendedIncomingDamage(22, 7));
  }

  @Test public void counterUsesHalfNormalAttackDamage() {
    assertEquals(10, CombatChoiceEngine.counterDamage(20));
    assertEquals(11, CombatChoiceEngine.counterDamage(21));
    assertEquals(1, CombatChoiceEngine.counterDamage(1));
  }

  @Test public void battleAcceptsOnlyFixedAbcTokens() {
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_A));
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_B));
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_C));
    assertFalse(CombatChoiceEngine.isCombatAction("Kai chạy sang trái"));
  }

  @Test public void guiltyCrownOverrideUsesFortyPercentProcAndExactDamage() {
    assertEquals(40, CombatChoiceEngine.configuredProcPercent("kai", "Guilty Crown Override"));
    assertEquals(240, CombatChoiceEngine.exactDamageForSkill("Guilty Crown Override"));
    assertEquals(0, CombatChoiceEngine.exactDamageForSkill("The Last Requiem"));
  }

  @Test public void knownEntityCatalogPreservesLegacyProfiles() {
    assertTrue(CombatChoiceEngine.isKnownEntity("hound"));
    assertTrue(CombatChoiceEngine.isKnownEntity("jeff_the_killer"));
    assertTrue(CombatChoiceEngine.isKnownEntity("slenderman"));
    assertFalse(CombatChoiceEngine.isKnownEntity("not_a_real_entity"));
  }
}
