package com.openguitar.game;

/** Progi combo, przy których pokazujemy popup (10, 20, …, 25, 50, 100). */
final class ComboMilestones {

    private ComboMilestones() {}

    static boolean popupAt(int combo, int prevCombo) {
        if (combo < 10 || combo <= prevCombo) {
            return false;
        }
        if (combo % 10 == 0) {
            return true;
        }
        return combo == 25 || combo == 50 || combo == 100;
    }
}
