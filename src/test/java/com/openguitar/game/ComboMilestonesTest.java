package com.openguitar.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboMilestonesTest {

    @Test
    void shouldTriggerOnEveryTenAndSpecialThresholds() {
        assertFalse(ComboMilestones.popupAt(9, 8));
        assertTrue(ComboMilestones.popupAt(10, 9));
        assertTrue(ComboMilestones.popupAt(20, 19));
        assertTrue(ComboMilestones.popupAt(25, 24));
        assertTrue(ComboMilestones.popupAt(50, 49));
        assertTrue(ComboMilestones.popupAt(100, 99));
    }

    @Test
    void shouldNotRepeatWhenComboUnchanged() {
        assertFalse(ComboMilestones.popupAt(30, 30));
        assertFalse(ComboMilestones.popupAt(50, 50));
    }

    @Test
    void shouldRejectLowOrRegressedCombo() {
        assertFalse(ComboMilestones.popupAt(0, 0));
        assertFalse(ComboMilestones.popupAt(5, 4));
        assertFalse(ComboMilestones.popupAt(15, 20));
    }

    @Test
    void shouldSkipNonMilestoneValues() {
        assertFalse(ComboMilestones.popupAt(11, 10));
        assertFalse(ComboMilestones.popupAt(24, 23));
        assertFalse(ComboMilestones.popupAt(26, 25));
        assertFalse(ComboMilestones.popupAt(99, 98));
    }
}
