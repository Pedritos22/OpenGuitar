package com.openguitar.game;

import com.openguitar.game.view.PersonaFonts;
import com.openguitar.game.view.PersonaMenuFx;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Nakładka ustawień (P3R) — wspólna dla {@link TitleScreen} i {@link MenuScreen}.
 * Sekcje: sterowanie, dźwięk, rozgrywka, wyświetlanie. Zapis przez {@link GameSettings#save()}.
 */
final class SettingsOverlay {

    private final double width;

    private StackPane host;
    private StackPane node;

    private final Button[] keyCaps = new Button[GameScreen.LANES];
    /** Ścieżka oczekująca na przypisanie klawisza (-1 = brak rebindingu). */
    private int rebindingLane = -1;
    private Label countdownValue;
    private TextField reactionTimeValue;
    private Slider reactionTimeSlider;
    private TextField noteOffsetValue;
    private Slider noteOffsetSlider;
    private Button popupsToggle;
    private Button comboPopupsToggle;
    private Button countdownResumeToggle;
    private Button fullscreenToggle;
    private Button fpsCounterToggle;
    private Button hitSfxToggle;
    private Button muteUnfocusedToggle;
    private Label languageValue;

    private Runnable onLocaleChanged;

    SettingsOverlay(double width) {
        this.width = width;
    }

    void setOnLocaleChanged(Runnable callback) {
        onLocaleChanged = callback;
    }

    boolean isOpen() {
        return node != null;
    }

    /** Otwiera nakładkę nad przekazanym kontenerem (jeśli nie jest już otwarta). */
    void open(StackPane host) {
        if (node != null) {
            return;
        }
        this.host = host;
        rebindingLane = -1;
        node = build();
        host.getChildren().add(node);
        SoundManager.get().play(SoundManager.Sfx.CONFIRM);
    }

    /** Zapisuje ustawienia i zamyka nakładkę. */
    void close() {
        if (node == null) {
            return;
        }
        GameSettings.get().save();
        host.getChildren().remove(node);
        node = null;
        rebindingLane = -1;
        SoundManager.get().play(SoundManager.Sfx.BACK);
    }

    /** Podczas rebindingu pierwszy klawisz przypisujemy do ścieżki; inaczej ESC zamyka. */
    void handleKey(KeyEvent e) {
        if (rebindingLane >= 0) {
            if (e.getCode() != KeyCode.ESCAPE) {
                GameSettings.get().setLaneKey(rebindingLane, e.getCode());
                SoundManager.get().play(SoundManager.Sfx.CONFIRM);
            }
            rebindingLane = -1;
            refreshKeyCaps();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            close();
        }
        e.consume();
    }

    // ── budowa widoku ──────────────────────────────────────────────────────

    private StackPane build() {
        Label heading = new Label(I18n.get("settings.title"));
        heading.setFont(PersonaFonts.display(26));
        heading.setTextFill(Color.web(PersonaMenuTheme.ACCENT));
        PersonaMenuFx.slant(heading, -0.14);

        Label sub = new Label(I18n.get("settings.subtitle"));
        sub.setFont(PersonaFonts.body(11));
        sub.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));

        Label hint = new Label(I18n.get("settings.hint"));
        hint.setFont(PersonaFonts.body(10));
        hint.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        hint.setPadding(new Insets(4, 0, 0, 1));

        VBox head = new VBox(-4, heading, sub, hint);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(6,
                sectionBlock(I18n.get("settings.section.controls"), keyBindingsGrid()),
                sectionBlock(I18n.get("settings.section.audio"),
                        lobbyVolumeRow(),
                        songVolumeRow(),
                        uiSfxVolumeRow(),
                        hitSfxRow(),
                        muteUnfocusedRow()),
                sectionBlock(I18n.get("settings.section.gameplay"),
                        reactionTimeRow(),
                        noteOffsetRow(),
                        countdownRow(),
                        countdownResumeRow(),
                        popupsRow(),
                        comboPopupsRow()),
                sectionBlock(I18n.get("settings.section.display"),
                        languageRow(),
                        fullscreenRow(),
                        fpsCounterRow(),
                        resetDefaultsRow()));
        body.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle(PersonaMenuTheme.scrollPane());
        PersonaMenuTheme.applyScrollPaneTheme(scroll);
        scroll.setMinViewportHeight(0);
        double maxScrollH = GameScreen.CANVAS_HEIGHT - 210;
        scroll.setMaxHeight(maxScrollH);
        scroll.prefViewportHeightProperty().bind(
                javafx.beans.binding.Bindings.min(
                        javafx.beans.binding.Bindings.max(body.heightProperty(), 1),
                        maxScrollH));

        double panelW = Math.min(520, width - 140);
        VBox panel = new VBox(10, head, scroll);
        panel.setPadding(new Insets(14, 16, 14, 16));
        panel.setFillWidth(true);
        panel.setPrefWidth(panelW);
        panel.setMinWidth(panelW);
        panel.setMaxWidth(panelW);
        // StackPane rozciąga dzieci na całą wysokość okna — bez USE_PREF_SIZE panel ma pustą stopkę.
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.setMinHeight(Region.USE_PREF_SIZE);
        panel.setStyle(PersonaMenuTheme.settingsPanelCompact());

        StackPane card = new StackPane(panel);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane overlay = new StackPane(card);
        StackPane.setAlignment(card, Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(2, 6, 14, 0.62);");
        overlay.setOnMouseClicked(e -> close());
        panel.setOnMouseClicked(javafx.event.Event::consume);
        return overlay;
    }

    private VBox sectionBlock(String caption, javafx.scene.Node... rows) {
        Label cap = new Label(caption);
        cap.setStyle(PersonaMenuTheme.settingsSectionLabel());
        cap.setPadding(new Insets(2, 0, 0, 1));

        VBox group = new VBox(0, rows);
        group.setFillWidth(true);
        group.setStyle(PersonaMenuTheme.settingGroup());

        VBox block = new VBox(4, cap, group);
        block.setFillWidth(true);
        return block;
    }

    private GridPane keyBindingsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS);
        col.setMinWidth(0);
        col.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col, col);

        for (int lane = 0; lane < GameScreen.LANES; lane++) {
            grid.add(keyBindingRow(lane), lane % 2, lane / 2);
        }
        return grid;
    }

    private HBox keyBindingRow(int lane) {
        Region dot = new Region();
        dot.setMinSize(8, 8);
        dot.setPrefSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.setStyle("-fx-background-color: " + toHex(com.openguitar.game.view.PersonaPalette.lane(lane)) + ";");

        Label name = new Label(I18n.format("settings.lane", lane + 1));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));

        HBox left = new HBox(6, dot, name);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMinWidth(0);

        Button cap = new Button(GameSettings.get().laneKey(lane).getName());
        cap.setStyle(PersonaMenuTheme.keyCapCompact(false));
        cap.setMinSize(48, PersonaMenuTheme.SETTINGS_BTN_HEIGHT);
        cap.setPrefSize(48, PersonaMenuTheme.SETTINGS_BTN_HEIGHT);
        cap.setMaxSize(48, PersonaMenuTheme.SETTINGS_BTN_HEIGHT);
        final int laneIdx = lane;
        cap.setOnAction(e -> startRebind(laneIdx));
        keyCaps[lane] = cap;

        HBox row = new HBox(8, left, cap);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));
        HBox.setHgrow(left, Priority.ALWAYS);
        return row;
    }

    private void startRebind(int lane) {
        rebindingLane = lane;
        refreshKeyCaps();
        SoundManager.get().play(SoundManager.Sfx.CLICK_GLASS);
    }

    private void refreshKeyCaps() {
        for (int i = 0; i < keyCaps.length; i++) {
            Button cap = keyCaps[i];
            if (cap == null) {
                continue;
            }
            boolean listening = (i == rebindingLane);
            cap.setText(listening ? "..." : GameSettings.get().laneKey(i).getName());
            cap.setStyle(PersonaMenuTheme.keyCapCompact(listening));
        }
    }

    private VBox volumeSliderRow(String title, int initialPercent, java.util.function.IntConsumer onVolumeChange) {
        Label name = new Label(title);
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);

        Label pct = new Label(initialPercent + "%");
        pct.setFont(PersonaMenuTheme.labelFont(11));
        pct.setTextFill(Color.web(PersonaMenuTheme.ACCENT_GLOW));
        pct.setMinWidth(36);
        pct.setAlignment(Pos.CENTER_RIGHT);

        Slider slider = new Slider(GameSettings.VOLUME_MIN, GameSettings.VOLUME_MAX, initialPercent);
        slider.setBlockIncrement(1);
        slider.setSnapToTicks(false);
        slider.setStyle(PersonaMenuTheme.volumeSlider());
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.setPrefHeight(18);

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int v = newVal.intValue();
            pct.setText(v + "%");
            onVolumeChange.accept(v);
        });
        slider.setOnMouseReleased(e -> SoundManager.get().play(SoundManager.Sfx.NAV));

        HBox header = new HBox(8, name, pct);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(3, header, slider);
        row.setFillWidth(true);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private VBox lobbyVolumeRow() {
        GameSettings s = GameSettings.get();
        return volumeSliderRow(I18n.get("settings.volume.lobby"), s.lobbyMusicVolume(), v -> {
            s.setLobbyMusicVolume(v);
            SoundManager.get().refreshLobbyVolume();
        });
    }

    private VBox songVolumeRow() {
        GameSettings s = GameSettings.get();
        return volumeSliderRow(I18n.get("settings.volume.songs"), s.songMusicVolume(), s::setSongMusicVolume);
    }

    private VBox uiSfxVolumeRow() {
        GameSettings s = GameSettings.get();
        return volumeSliderRow(I18n.get("settings.volume.ui"), s.uiSfxVolume(), v -> {
            s.setUiSfxVolume(v);
            SoundManager.get().play(SoundManager.Sfx.NAV);
        });
    }

    private HBox hitSfxRow() {
        Label name = new Label(I18n.get("settings.hit_sfx"));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);

        hitSfxToggle = stepper(null);
        hitSfxToggle.setMinWidth(48);
        hitSfxToggle.setPrefWidth(48);
        updateHitSfxToggle();
        hitSfxToggle.setOnAction(e -> {
            GameSettings s = GameSettings.get();
            s.setGameplayHitSfx(!s.gameplayHitSfx());
            updateHitSfxToggle();
            SoundManager.get().play(SoundManager.Sfx.NAV);
            if (s.gameplayHitSfx()) {
                SoundManager.get().playGameplay(SoundManager.Sfx.GREAT);
            }
        });

        HBox row = new HBox(8, name, hitSfxToggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private void updateHitSfxToggle() {
        setToggleText(hitSfxToggle, GameSettings.get().gameplayHitSfx());
    }

    private HBox countdownRow() {
        Label name = new Label(I18n.get("settings.countdown"));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);

        countdownValue = new Label();
        countdownValue.setFont(PersonaMenuTheme.labelFont(11));
        countdownValue.setTextFill(Color.web(PersonaMenuTheme.ACCENT_GLOW));
        countdownValue.setMinWidth(40);
        countdownValue.setAlignment(Pos.CENTER);
        updateCountdownValue();

        Button minus = stepper("\u2212");
        minus.setOnAction(e -> changeCountdown(-1));
        Button plus = stepper("+");
        plus.setOnAction(e -> changeCountdown(1));

        HBox controls = new HBox(4, minus, countdownValue, plus);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(8, name, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private void changeCountdown(int delta) {
        GameSettings s = GameSettings.get();
        s.setCountdownSeconds(s.countdownSeconds() + delta);
        updateCountdownValue();
        SoundManager.get().play(SoundManager.Sfx.NAV);
    }

    private void updateCountdownValue() {
        int sec = GameSettings.get().countdownSeconds();
        countdownValue.setText(sec <= 0 ? I18n.get("settings.disabled") : I18n.format("settings.seconds", sec));
    }

    private VBox reactionTimeRow() {
        Label name = new Label(I18n.get("settings.reaction"));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);

        reactionTimeValue = new TextField();
        reactionTimeValue.setFont(PersonaMenuTheme.labelFont(11));
        reactionTimeValue.setStyle(PersonaMenuTheme.reactionTimeField());
        reactionTimeValue.setMinWidth(72);
        reactionTimeValue.setPrefWidth(72);
        reactionTimeValue.setMaxWidth(72);
        reactionTimeValue.setAlignment(Pos.CENTER_RIGHT);
        reactionTimeValue.setPromptText("0.05");
        reactionTimeValue.setOnAction(e -> commitReactionTimeValue());
        reactionTimeValue.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                commitReactionTimeValue();
            }
        });
        updateReactionTimeValue();

        Slider slider = new Slider(GameSettings.REACTION_TIME_MIN_MS,
                GameSettings.REACTION_TIME_MAX_MS, GameSettings.get().noteLookAheadMs());
        reactionTimeSlider = slider;
        slider.setBlockIncrement(GameSettings.REACTION_TIME_STEP_MS);
        slider.setMajorTickUnit(200);
        slider.setMinorTickCount(3);
        slider.setSnapToTicks(false);
        slider.setStyle(PersonaMenuTheme.volumeSlider());
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            GameSettings.get().setReactionTimeMs(newVal.intValue());
            if (!reactionTimeValue.isFocused()) {
                updateReactionTimeValue();
            }
        });
        slider.setOnMouseReleased(e -> SoundManager.get().play(SoundManager.Sfx.NAV));

        HBox header = new HBox(8, name, reactionTimeValue);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(3, header, slider);
        row.setFillWidth(true);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private void updateReactionTimeValue() {
        reactionTimeValue.setText(String.format(java.util.Locale.ROOT, "%.3f",
                GameSettings.get().noteLookAheadMs() / 1000.0));
    }

    private void commitReactionTimeValue() {
        String raw = reactionTimeValue.getText().trim().replace(',', '.');
        try {
            double seconds = Double.parseDouble(raw);
            if (!Double.isFinite(seconds)) {
                throw new NumberFormatException(raw);
            }
            GameSettings.get().setReactionTimeMs((int) Math.round(seconds * 1000.0));
            if (reactionTimeSlider != null) {
                reactionTimeSlider.setValue(GameSettings.get().noteLookAheadMs());
            }
            SoundManager.get().play(SoundManager.Sfx.NAV);
        } catch (NumberFormatException ex) {
            // Przy błędnym wpisie wracamy do ostatniej poprawnej wartości.
        }
        updateReactionTimeValue();
    }

    private VBox noteOffsetRow() {
        Label name = new Label(I18n.get("settings.note_offset"));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);

        noteOffsetValue = new TextField();
        noteOffsetValue.setFont(PersonaMenuTheme.labelFont(11));
        noteOffsetValue.setStyle(PersonaMenuTheme.reactionTimeField());
        noteOffsetValue.setMinWidth(72);
        noteOffsetValue.setPrefWidth(72);
        noteOffsetValue.setMaxWidth(72);
        noteOffsetValue.setAlignment(Pos.CENTER_RIGHT);
        noteOffsetValue.setPromptText("0");
        noteOffsetValue.setOnAction(e -> commitNoteOffsetValue());
        noteOffsetValue.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                commitNoteOffsetValue();
            }
        });
        updateNoteOffsetValue();

        Slider slider = new Slider(GameSettings.NOTE_OFFSET_MIN_MS,
                GameSettings.NOTE_OFFSET_MAX_MS, GameSettings.get().noteOffsetMs());
        noteOffsetSlider = slider;
        slider.setBlockIncrement(GameSettings.NOTE_OFFSET_STEP_MS);
        slider.setMajorTickUnit(100);
        slider.setMinorTickCount(4);
        slider.setSnapToTicks(false);
        slider.setStyle(PersonaMenuTheme.volumeSlider());
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            GameSettings.get().setNoteOffsetMs(newVal.intValue());
            if (!noteOffsetValue.isFocused()) {
                updateNoteOffsetValue();
            }
        });
        slider.setOnMouseReleased(e -> SoundManager.get().play(SoundManager.Sfx.NAV));

        HBox header = new HBox(8, name, noteOffsetValue);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(3, header, slider);
        row.setFillWidth(true);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private void updateNoteOffsetValue() {
        noteOffsetValue.setText(Integer.toString(GameSettings.get().noteOffsetMs()));
    }

    private void commitNoteOffsetValue() {
        String raw = noteOffsetValue.getText().trim();
        try {
            GameSettings.get().setNoteOffsetMs(Integer.parseInt(raw));
            if (noteOffsetSlider != null) {
                noteOffsetSlider.setValue(GameSettings.get().noteOffsetMs());
            }
            SoundManager.get().play(SoundManager.Sfx.NAV);
        } catch (NumberFormatException ex) {
            // Przy błędnym wpisie wracamy do ostatniej poprawnej wartości.
        }
        updateNoteOffsetValue();
    }

    private HBox countdownResumeRow() {
        HBox row = toggleRow(I18n.get("settings.countdown.resume"), b -> countdownResumeToggle = b, () -> {
            GameSettings s = GameSettings.get();
            s.setCountdownOnResume(!s.countdownOnResume());
            updateCountdownResumeToggle();
        });
        updateCountdownResumeToggle();
        return row;
    }

    private HBox muteUnfocusedRow() {
        HBox row = toggleRow(I18n.get("settings.mute_unfocused"), b -> muteUnfocusedToggle = b, () -> {
            GameSettings s = GameSettings.get();
            s.setMuteWhenUnfocused(!s.muteWhenUnfocused());
            updateMuteUnfocusedToggle();
            SoundManager.get().refreshLobbyVolume();
        });
        updateMuteUnfocusedToggle();
        return row;
    }

    private void updateMuteUnfocusedToggle() {
        setToggleText(muteUnfocusedToggle, GameSettings.get().muteWhenUnfocused());
    }

    private void updateCountdownResumeToggle() {
        setToggleText(countdownResumeToggle, GameSettings.get().countdownOnResume());
    }

    private HBox popupsRow() {
        Label name = new Label(I18n.get("settings.popups.hits"));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);

        popupsToggle = stepper(null);
        popupsToggle.setMinWidth(48);
        popupsToggle.setPrefWidth(48);
        updatePopupsToggle();
        popupsToggle.setOnAction(e -> {
            GameSettings s = GameSettings.get();
            s.setShowHitPopups(!s.showHitPopups());
            updatePopupsToggle();
            SoundManager.get().play(SoundManager.Sfx.NAV);
        });

        HBox row = new HBox(8, name, popupsToggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private void updatePopupsToggle() {
        setToggleText(popupsToggle, GameSettings.get().showHitPopups());
    }

    private HBox comboPopupsRow() {
        HBox row = toggleRow(I18n.get("settings.popups.combo"), b -> comboPopupsToggle = b, () -> {
            GameSettings s = GameSettings.get();
            s.setShowComboPopups(!s.showComboPopups());
            updateComboPopupsToggle();
        });
        updateComboPopupsToggle();
        return row;
    }

    private void updateComboPopupsToggle() {
        setToggleText(comboPopupsToggle, GameSettings.get().showComboPopups());
    }

    private HBox fullscreenRow() {
        HBox row = toggleRow(I18n.get("settings.fullscreen.start"), b -> fullscreenToggle = b, () -> {
            GameSettings s = GameSettings.get();
            s.setFullscreenOnStart(!s.fullscreenOnStart());
            updateFullscreenToggle();
        });
        updateFullscreenToggle();
        return row;
    }

    private void updateFullscreenToggle() {
        setToggleText(fullscreenToggle, GameSettings.get().fullscreenOnStart());
    }

    private HBox fpsCounterRow() {
        HBox row = toggleRow(I18n.get("settings.fps.counter"), b -> fpsCounterToggle = b, () -> {
            GameSettings s = GameSettings.get();
            s.setShowFpsCounter(!s.showFpsCounter());
            updateFpsCounterToggle();
        });
        updateFpsCounterToggle();
        return row;
    }

    private void updateFpsCounterToggle() {
        setToggleText(fpsCounterToggle, GameSettings.get().showFpsCounter());
    }

    private HBox resetDefaultsRow() {
        Label label = new Label(I18n.get("settings.reset"));
        label.setFont(PersonaMenuTheme.labelFont(11));
        label.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);

        Button reset = new Button(I18n.get("settings.reset.action"));
        reset.setStyle(PersonaMenuTheme.toolbarButton());
        reset.setMinHeight(PersonaMenuTheme.SETTINGS_BTN_HEIGHT);
        reset.setPrefHeight(PersonaMenuTheme.SETTINGS_BTN_HEIGHT);
        reset.setMaxHeight(PersonaMenuTheme.SETTINGS_BTN_HEIGHT);
        reset.setMinWidth(Region.USE_PREF_SIZE);
        reset.setOnAction(e -> {
            GameSettings.get().resetToDefaults();
            SoundManager.get().refreshLobbyVolume();
            SoundManager.get().play(SoundManager.Sfx.CONFIRM);
            rebuildOverlay();
        });

        HBox row = new HBox(8, label, reset);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private HBox toggleRow(String name, java.util.function.Consumer<Button> register,
                         Runnable onToggle) {
        Label label = new Label(name);
        label.setFont(PersonaMenuTheme.labelFont(11));
        label.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);

        Button toggle = stepper(null);
        toggle.setMinWidth(48);
        toggle.setPrefWidth(48);
        register.accept(toggle);
        toggle.setOnAction(e -> {
            onToggle.run();
            SoundManager.get().play(SoundManager.Sfx.NAV);
        });

        HBox row = new HBox(8, label, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private HBox languageRow() {
        Label name = new Label(I18n.get("settings.language"));
        name.setFont(PersonaMenuTheme.labelFont(11));
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);

        languageValue = new Label();
        languageValue.setFont(PersonaMenuTheme.labelFont(11));
        languageValue.setTextFill(Color.web(PersonaMenuTheme.ACCENT_GLOW));
        languageValue.setMinWidth(84);
        languageValue.setAlignment(Pos.CENTER);
        updateLanguageValue();

        Button minus = stepper("\u2212");
        minus.setOnAction(e -> changeLanguage(-1));
        Button plus = stepper("+");
        plus.setOnAction(e -> changeLanguage(1));

        HBox controls = new HBox(4, minus, languageValue, plus);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(8, name, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(PersonaMenuTheme.settingRowCompact());
        return row;
    }

    private void changeLanguage(int delta) {
        GameSettings.get().cycleLocale(delta);
        rebuildOverlay();
        SoundManager.get().play(SoundManager.Sfx.NAV);
    }

    private void updateLanguageValue() {
        if (languageValue != null) {
            languageValue.setText(GameSettings.get().localeLabel());
        }
    }

    /** Przebudowuje panel po zmianie języka (etykiety w nowym locale). */
    private void rebuildOverlay() {
        if (host == null || node == null) {
            return;
        }
        host.getChildren().remove(node);
        rebindingLane = -1;
        node = build();
        host.getChildren().add(node);
        if (onLocaleChanged != null) {
            onLocaleChanged.run();
        }
    }

    private static void setToggleText(Button toggle, boolean on) {
        toggle.setText(on ? I18n.get("settings.on") : I18n.get("settings.off"));
    }

    private static Button stepper(String text) {
        Button b = new Button(text == null ? "" : text);
        b.setStyle(PersonaMenuTheme.stepperButtonCompact());
        double h = PersonaMenuTheme.SETTINGS_BTN_HEIGHT;
        b.setMinSize(h, h);
        b.setPrefHeight(h);
        b.setMaxHeight(h);
        return b;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
