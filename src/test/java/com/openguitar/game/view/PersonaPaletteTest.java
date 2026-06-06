package com.openguitar.game.view;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class PersonaPaletteTest {

    @Test
    void comboColorShouldEscalateWithTier() {
        assertSame(PersonaPalette.MUTED, PersonaPalette.comboColor(0));
        assertSame(PersonaPalette.COMBO, PersonaPalette.comboColor(5));
        assertSame(PersonaPalette.TEAL, PersonaPalette.comboColor(10));
        assertSame(PersonaPalette.ELECTRIC, PersonaPalette.comboColor(20));
        assertSame(PersonaPalette.AQUA_BRIGHT, PersonaPalette.comboColor(30));
        assertSame(PersonaPalette.COMBO_HOT, PersonaPalette.comboColor(50));
        assertSame(PersonaPalette.COMBO_LEGEND, PersonaPalette.comboColor(100));
    }

    @Test
    void multiplierColorShouldMatchBand() {
        assertSame(PersonaPalette.MUTED, PersonaPalette.multiplierColor(1));
        assertSame(PersonaPalette.TEAL, PersonaPalette.multiplierColor(2));
        assertSame(PersonaPalette.AQUA_BRIGHT, PersonaPalette.multiplierColor(3));
        assertSame(PersonaPalette.COMBO_HOT, PersonaPalette.multiplierColor(4));
    }

    @Test
    void laneColorsShouldWrapAndStayDistinct() {
        assertEquals(PersonaPalette.lane(0), PersonaPalette.lane(4));
        assertEquals(PersonaPalette.lane(1), PersonaPalette.lane(-3));
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                int laneA = i;
                int laneB = j;
                assertFalse(PersonaPalette.lane(laneA).equals(PersonaPalette.lane(laneB)),
                        () -> "lanes " + laneA + " and " + laneB + " should differ");
            }
        }
    }

    @Test
    void alphaShouldPreserveRgb() {
        Color base = PersonaPalette.AQUA;
        Color faded = PersonaPalette.alpha(base, 0.5);
        assertEquals(base.getRed(), faded.getRed(), 1e-9);
        assertEquals(base.getGreen(), faded.getGreen(), 1e-9);
        assertEquals(base.getBlue(), faded.getBlue(), 1e-9);
        assertEquals(0.5, faded.getOpacity(), 1e-9);
    }
}
