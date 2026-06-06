package com.openguitar.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
class I18nTest {

    @AfterEach
    void restoreLocale() {
        I18n.setLocaleTag(GameSettings.LOCALE_DEFAULT);
        GameSettings.resetForTests(null);
    }

    @Test
    void shouldReturnPolishStrings() {
        I18n.setLocaleTag("pl");
        assertEquals("GRAJ", I18n.get("title.play"));
        assertEquals("USTAWIENIA", I18n.get("settings.title"));
    }

    @Test
    void shouldReturnEnglishStrings() {
        I18n.setLocaleTag("en");
        assertEquals("PLAY", I18n.get("title.play"));
        assertEquals("SETTINGS", I18n.get("settings.title"));
    }

    @Test
    void shouldReturnGermanSpanishAndFrenchStrings() {
        I18n.setLocaleTag("de");
        assertEquals("SPIELEN", I18n.get("title.play"));
        I18n.setLocaleTag("es");
        assertEquals("JUGAR", I18n.get("title.play"));
        I18n.setLocaleTag("fr");
        assertEquals("JOUER", I18n.get("title.play"));
        I18n.setLocaleTag("it");
        assertEquals("GIOCA", I18n.get("title.play"));
    }

    @Test
    void formatShouldSubstituteArguments() {
        I18n.setLocaleTag("en");
        assertEquals("3 songs · 2 ready", I18n.format("menu.status.song_count", 3, 2));
    }

    @Test
    void formatShouldUseActiveLocaleTemplate() {
        I18n.setLocaleTag("pl");
        assertEquals("5 utworów · 2 gotowych", I18n.format("menu.status.song_count", 5, 2));
        I18n.setLocaleTag("it");
        assertEquals("#3   400 pt", I18n.format("menu.history.row", 3, 400));
    }

    @Test
    void unknownLocaleTagShouldFallBackToPolishBundle() {
        I18n.setLocaleTag("xx");
        assertEquals("GRAJ", I18n.get("title.play"));
    }

    @Test
    void nullOrBlankLocaleTagShouldFallBackToPolishBundle() {
        I18n.setLocaleTag(null);
        assertEquals("GRAJ", I18n.get("title.play"));
        I18n.setLocaleTag("  ");
        assertEquals("USTAWIENIA", I18n.get("settings.title"));
    }

    @Test
    void unknownKeyShouldReturnKeyName() {
        I18n.setLocaleTag("pl");
        assertEquals("totally.unknown.key", I18n.get("totally.unknown.key"));
    }

    @Test
    void localeNameKeysShouldResolveInEveryActiveLocale() {
        for (String tag : GameSettings.LOCALE_TAGS) {
            I18n.setLocaleTag(tag);
            for (String nameTag : GameSettings.LOCALE_TAGS) {
                String label = I18n.get("locale.name." + nameTag);
                assertFalse(label.isBlank());
                assertFalse(label.startsWith("locale.name."));
            }
        }
    }

    @Test
    void gameStringsShouldStayConsistentAcrossLocales() {
        for (String tag : GameSettings.LOCALE_TAGS) {
            I18n.setLocaleTag(tag);
            assertEquals("GO!", I18n.get("game.countdown.go"));
            assertEquals("PERFECT!", I18n.get("game.judgment.perfect"));
            assertEquals("COMBO BREAK", I18n.get("game.popup.combo_break"));
        }
    }
}
