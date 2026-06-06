package com.openguitar.game;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weryfikuje kompletność plików {@code messages_xx.properties} na classpath. */
class I18nBundleTest {

    private static final List<String> ENGLISH_GAME_STRING_KEYS = List.of(
            "game.countdown.go",
            "game.judgment.perfect",
            "game.judgment.great",
            "game.judgment.miss",
            "game.popup.multiplier",
            "game.popup.combo_break"
    );

    @Test
    void eachSupportedLocaleShouldDefineAllEnglishKeys() throws Exception {
        Properties english = loadBundle("en");
        Set<String> keys = english.stringPropertyNames();
        assertFalse(keys.isEmpty());

        for (String tag : GameSettings.LOCALE_TAGS) {
            Properties locale = loadBundle(tag);
            for (String key : keys) {
                assertTrue(locale.containsKey(key),
                        () -> tag + " missing key: " + key);
                assertFalse(locale.getProperty(key).isBlank(),
                        () -> tag + " has blank value for: " + key);
            }
        }
    }

    @Test
    void eachBundleShouldDefineLocaleNamesForEverySupportedTag() throws Exception {
        for (String tag : GameSettings.LOCALE_TAGS) {
            Properties locale = loadBundle(tag);
            for (String nameTag : GameSettings.LOCALE_TAGS) {
                String key = "locale.name." + nameTag;
                assertTrue(locale.containsKey(key),
                        () -> tag + " missing " + key);
                assertFalse(locale.getProperty(key).isBlank(),
                        () -> tag + " has blank " + key);
            }
        }
    }

    @Test
    void eachLocaleShouldHaveSameKeyCountAsEnglish() throws Exception {
        Properties english = loadBundle("en");
        int expected = english.stringPropertyNames().size();

        for (String tag : GameSettings.LOCALE_TAGS) {
            Properties locale = loadBundle(tag);
            assertEquals(expected, locale.stringPropertyNames().size(),
                    () -> tag + " key count differs from English");
        }
    }

    @Test
    void rhythmGameStringsShouldStayEnglishInEveryBundle() throws Exception {
        Properties english = loadBundle("en");
        for (String key : ENGLISH_GAME_STRING_KEYS) {
            String expected = english.getProperty(key);
            assertFalse(expected.isBlank(), "English reference missing: " + key);

            for (String tag : GameSettings.LOCALE_TAGS) {
                Properties locale = loadBundle(tag);
                assertEquals(expected, locale.getProperty(key),
                        () -> tag + " should keep English game term for " + key);
            }
        }
    }

    @Test
    void messageResourceFilesShouldExistForEverySupportedLocale() throws Exception {
        for (String tag : GameSettings.LOCALE_TAGS) {
            String path = "/i18n/messages_" + tag + ".properties";
            try (InputStream in = I18nBundleTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "missing resource: " + path);
            }
        }
    }

    private static Properties loadBundle(String tag) throws Exception {
        Properties p = new Properties();
        String path = "/i18n/messages_" + tag + ".properties";
        try (InputStream in = I18nBundleTest.class.getResourceAsStream(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(reader);
        }
        return p;
    }
}
