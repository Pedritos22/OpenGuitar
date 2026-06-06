package com.openguitar.game;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * Proste tłumaczenia UI z plików {@code i18n/messages_xx.properties} (UTF-8).
 * Brakujące klucze — fallback na angielski, potem sam klucz.
 */
public final class I18n {

    private static final String PATH_PREFIX = "/i18n/messages_";
    private static final String PATH_SUFFIX = ".properties";
private static final String FALLBACK_TAG = "en";
private static final String DEFAULT_TAG = GameSettings.LOCALE_DEFAULT;

private static final Properties FALLBACK = load(FALLBACK_TAG);
private static Properties active = load(DEFAULT_TAG);
private static Locale activeLocale = Locale.forLanguageTag(DEFAULT_TAG);

private I18n() {}

/** Ustawia aktywny język (tag BCP 47, np. {@code pl}, {@code en}). */
public static void setLocaleTag(String tag) {
    String normalized = GameSettings.normalizeLocaleTag(tag);
    Properties loaded = load(normalized);
    if (loaded.isEmpty()) {
        active = FALLBACK;
        activeLocale = Locale.forLanguageTag(FALLBACK_TAG);
    } else {
        active = loaded;
        activeLocale = Locale.forLanguageTag(normalized);
    }
}

public static String get(String key) {
    String value = active.getProperty(key);
    if (value == null) {
        value = FALLBACK.getProperty(key);
    }
    return value != null ? value : key;
}

public static String format(String key, Object... args) {
    MessageFormat fmt = new MessageFormat(get(key), activeLocale);
    return fmt.format(args);
}

    private static Properties load(String tag) {
        Properties p = new Properties();
        String path = PATH_PREFIX + tag + PATH_SUFFIX;
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) {
                return p;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(reader);
            }
        } catch (Exception ex) {
            return new Properties();
        }
        return p;
    }
}
