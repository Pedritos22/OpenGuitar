package com.openguitar.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class GameLogTest {

    private static final Logger LOG = Logger.getLogger("com.openguitar.game.GameLogTest");

    private final List<LogRecord> records = new ArrayList<>();
    private Handler capture;
    private Level previousLevel;

    @BeforeEach
    void attachCapture() {
        previousLevel = LOG.getLevel();
        LOG.setLevel(Level.ALL);
        capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        LOG.addHandler(capture);
    }

    @AfterEach
    void detachCapture() {
        LOG.removeHandler(capture);
        LOG.setLevel(previousLevel);
        records.clear();
    }

    @Test
    void eventLogsAtInfoWithComponentPrefix() {
        GameLog.event(LOG, "game", "start() — test");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals(Level.INFO, r.getLevel());
        assertTrue(r.getMessage().contains("[game]"));
        assertTrue(r.getMessage().contains("[BG]"), "testy JUnit działają poza wątkiem FX");
        assertTrue(r.getMessage().contains("start() — test"));
    }

    @Test
    void fineAndWarnUseExpectedLevels() {
        GameLog.fine(LOG, "sound", "createPlayer() — OK");
        GameLog.warn(LOG, "game", "stall przy 1200ms");

        assertEquals(2, records.size());
        assertEquals(Level.FINE, records.get(0).getLevel());
        assertEquals(Level.WARNING, records.get(1).getLevel());
        assertTrue(records.get(0).getMessage().contains("[sound]"));
        assertTrue(records.get(1).getMessage().contains("stall"));
    }

    @Test
    void warnWithThrowableAttachesException() {
        IllegalStateException err = new IllegalStateException("boom");
        GameLog.warn(LOG, "app", "nieobsłużony wyjątek", err);

        assertEquals(1, records.size());
        assertSame(err, records.get(0).getThrown());
        assertTrue(records.get(0).getMessage().contains("[app]"));
    }

    @Test
    void respectsLoggerLevel() {
        LOG.setLevel(Level.WARNING);
        GameLog.event(LOG, "game", "powinno być wyciszone");
        GameLog.fine(LOG, "game", "też wyciszone");
        GameLog.warn(LOG, "game", "widoczne");

        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
    }
}
