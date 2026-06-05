package com.openguitar.game;

import com.openguitar.beatmap.BeatmapEngine;
import com.openguitar.beatmap.SongContext;
import com.openguitar.game.view.FullscreenScaler;
import com.openguitar.game.view.PersonaFonts;
import com.openguitar.game.view.PersonaMenuFx;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menu główne — lista utworów z {@code songs/}, generowanie beatmapy i start gry.
 */
public final class MenuScreen {

    private static final Logger LOG = Logger.getLogger(MenuScreen.class.getName());

    public static final int WIDTH  = GameScreen.CANVAS_WIDTH;
    public static final int HEIGHT = GameScreen.CANVAS_HEIGHT;

    private static final Insets PAD = new Insets(18, 20, 14, 20);
    private static final double LOGO_H = 48;
    private static final double INDEX_W = 28;

    /** Ile ostatnich podejść pokazujemy w historii. */
    private static final int HISTORY_LIMIT = 25;
    private static final java.time.format.DateTimeFormatter HISTORY_DATE =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
                    .withZone(java.time.ZoneId.systemDefault());

    private final Path songsDir;
    private final Consumer<SongContext> onSongSelected;
    private final Runnable onExit;
    private final StatsStore stats;

    private final Scene scene;
    private final StackPane root;
    private final VBox songsList;
    private final Label statusLabel;

    /** Nakładka z historią podejść (null = zamknięta). */
    private StackPane historyOverlay;

    /** Reużywalna nakładka ustawień (P3R) — wspólna z panelem startowym. */
    private final SettingsOverlay settings = new SettingsOverlay(WIDTH);

    /** Wiersze listy utworów — nawigacja klawiaturą. */
    private final List<RowHandle> navRows = new ArrayList<>();
    private int selectedIndex = -1;

    /** Panel statystyk — przypisany do aktualnie wybranej piosenki. */
    private final Label statCaption = new Label("STATYSTYKI");
    private final Label statPlays = statValueLabel();
    private final Label statBest = statValueLabel();
    private final Label statCombo = statValueLabel();

    public MenuScreen(Path songsDir, Consumer<SongContext> onSongSelected, Runnable onExit,
                      StatsStore stats) {
        this.songsDir = songsDir;
        this.onSongSelected = onSongSelected;
        this.onExit = onExit;
        this.stats = stats;

        MenuBackground background = new MenuBackground();
        background.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        songsList = new VBox(6);
        songsList.setFillWidth(true);
        // Zapas poziomy, żeby ukos (Shear) i wysuwanie wiersza nie wychodziły poza
        // widoczny obszar ScrollPane (który by je przyciął).
        songsList.setPadding(new Insets(2, 22, 2, 14));

        statusLabel = ellipsisLabel(" ");
        statusLabel.setStyle(PersonaMenuTheme.statusText());

        BorderPane content = new BorderPane();
        content.setPadding(PAD);
        content.setStyle(PersonaMenuTheme.rootOverlay());
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        content.setTop(buildHeader());
        content.setCenter(buildListArea());
        content.setBottom(buildFooter());

        this.root = new StackPane(background, content);
        this.scene = new Scene(FullscreenScaler.wrap(root, WIDTH, HEIGHT), WIDTH, HEIGHT);
        this.scene.setFill(Color.web(PersonaMenuTheme.BG_DEEP));
        this.scene.setOnKeyPressed(this::handleMenuKey);

        reload();
    }

    public Scene getScene() {
        return scene;
    }

    // ── layout ─────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        ImageView logo = createLogo();

        Label open = new Label("Open");
        open.setFont(PersonaMenuTheme.titleFont());
        open.setTextFill(Color.web(PersonaMenuTheme.TEXT));

        Label guitar = new Label("Guitar");
        guitar.setFont(PersonaMenuTheme.titleFont());
        guitar.setTextFill(Color.web(PersonaMenuTheme.ACCENT));

        HBox title = new HBox(open, guitar);
        title.setAlignment(Pos.CENTER_LEFT);
        PersonaMenuFx.slant(title, -0.18); // pochył „logo” w stylu P3R

        Button songsBtn = toolbarButton("Songs");
        songsBtn.setOnAction(e -> openSongsFolder());
        HBox.setMargin(songsBtn, new Insets(0, 0, 0, 16));

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setMaxWidth(Double.MAX_VALUE);
        if (logo != null) {
            top.getChildren().add(logo);
        }
        top.getChildren().addAll(title, songsBtn);

        Region divider = new Region();
        divider.setStyle(PersonaMenuTheme.divider());
        divider.setMaxWidth(Double.MAX_VALUE);

        VBox header = new VBox(12, top, divider, buildStatsBar());
        BorderPane.setMargin(header, new Insets(0, 0, 14, 0));
        return header;
    }

    private VBox buildStatsBar() {
        statCaption.setStyle(PersonaMenuTheme.sectionLabel());

        HBox chips = new HBox(8,
                statChip("PODEJŚCIA", statPlays),
                statChip("NAJLEPSZY WYNIK", statBest),
                statChip("MAX COMBO", statCombo));
        chips.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(6, statCaption, chips);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Label statValueLabel() {
        Label v = new Label("—");
        v.setFont(PersonaFonts.display(24));
        v.setTextFill(Color.web(PersonaMenuTheme.TEXT));
        return v;
    }

    private static Region statChip(String label, Label valueLabel) {
        Label l = new Label(label);
        l.setFont(PersonaFonts.label(10));
        l.setTextFill(Color.web(PersonaMenuTheme.ACCENT));

        VBox box = new VBox(-2, l, valueLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(6, 12, 6, 12));
        box.setStyle("-fx-background-color: rgba(10, 29, 58, 0.7);"
                + "-fx-border-color: " + PersonaMenuTheme.BORDER + " " + PersonaMenuTheme.BORDER
                + " " + PersonaMenuTheme.BORDER + " " + PersonaMenuTheme.ACCENT + ";"
                + "-fx-border-width: 1 1 1 3;");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Aktualizuje panel statystyk pod kątem aktualnie wybranego wiersza. */
    private void updateStatsPanel(RowHandle h) {
        statCaption.setText("STATYSTYKI · " + (h.title != null ? h.title : "—"));
        var stat = (h.songId != null) ? stats.forSong(h.songId) : java.util.Optional.<StatsStore.SongStat>empty();
        statPlays.setText(stat.map(s -> String.valueOf(s.plays)).orElse("0"));
        statBest.setText(stat.map(s -> formatNumber(s.bestScore)).orElse("0"));
        statCombo.setText(stat.map(s -> String.valueOf(s.maxCombo)).orElse("0"));
    }

    private static String formatNumber(int n) {
        if (n < 1000) return String.valueOf(n);
        return String.format("%,d", n).replace(',', ' ');
    }

    private VBox buildListArea() {
        Label caption = new Label("Utwory");
        caption.setStyle(PersonaMenuTheme.sectionLabel());

        ScrollPane scroll = new ScrollPane(songsList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(PersonaMenuTheme.scrollPane());
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox block = new VBox(10, caption, scroll);
        VBox.setVgrow(block, Priority.ALWAYS);
        return block;
    }

    private VBox buildFooter() {
        Button settingsBtn = gearButton();
        settingsBtn.setOnAction(e -> openSettings());
        Button history = toolbarButton("Historia");
        history.setOnAction(e -> openHistoryForSelection());
        Button refresh = toolbarButton("Odśwież");
        refresh.setOnAction(e -> {
            SoundManager.get().play(SoundManager.Sfx.NAV);
            reload();
        });
        Button exit = toolbarButton("Powrót");
        exit.setOnAction(e -> {
            SoundManager.get().play(SoundManager.Sfx.BACK);
            onExit.run();
        });

        HBox actions = new HBox(8, settingsBtn, history, refresh, exit);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(actions, Priority.NEVER);

        HBox bar = new HBox(10, statusLabel, actions);
        bar.setAlignment(Pos.CENTER_LEFT);
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        Region divider = new Region();
        divider.setStyle(PersonaMenuTheme.divider());
        divider.setMaxWidth(Double.MAX_VALUE);

        VBox footer = new VBox(12, divider, bar);
        footer.setStyle(PersonaMenuTheme.statusBar());
        footer.setMaxWidth(Double.MAX_VALUE);
        BorderPane.setMargin(footer, new Insets(14, 0, 0, 0));
        return footer;
    }

    // ── wiersze listy ──────────────────────────────────────────────────────

    private HBox buildRow(SongEntry entry, int index) {
        boolean ready = entry.hasBeatmap();

        Label num = new Label(String.format("%02d", index));
        num.setStyle(PersonaMenuTheme.indexLabel());
        num.setMinWidth(INDEX_W);
        num.setPrefWidth(INDEX_W);
        num.setMaxWidth(INDEX_W);
        num.setAlignment(Pos.CENTER);

        StackPane rank = rankTile(ready ? entry.context().songId() : null);

        Label name = ellipsisLabel(entry.title());
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT));
        name.setFont(PersonaMenuTheme.labelFont(13));

        Label meta = ellipsisLabel(metaLine(entry, ready ? entry.context() : null));
        meta.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));
        meta.setFont(PersonaMenuTheme.bodyFont(10));
        if (ready) {
            stats.forSong(entry.context().songId()).ifPresent(st ->
                    meta.setText(meta.getText() + "   ·   BEST " + formatNumber(st.bestScore)));
        }

        Label badge = new Label(ready ? "Gotowe" : "Brak mapy");
        badge.setStyle(ready ? PersonaMenuTheme.badgeReady() : PersonaMenuTheme.badgePending());
        badge.setAlignment(Pos.CENTER);
        badge.setMinHeight(PersonaMenuTheme.BTN_HEIGHT);
        badge.setPrefHeight(PersonaMenuTheme.BTN_HEIGHT);
        badge.setMaxHeight(PersonaMenuTheme.BTN_HEIGHT);
        badge.setMinWidth(Region.USE_PREF_SIZE);

        VBox info = new VBox(3, name, meta);
        info.setMinWidth(0);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button action = rowButton(ready ? "Graj" : "Generuj", !ready);

        HBox actions = new HBox(8, badge, action);
        actions.setAlignment(Pos.CENTER);

        final SongContext[] ctx = new SongContext[1];
        if (ready) {
            ctx[0] = withAbsoluteAudio(entry.context(), entry.audioPath());
        }

        HBox row = new HBox(10, num, rank, info, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 14, 0, 12));
        row.setMinHeight(54);
        row.setPrefHeight(54);
        row.setMaxWidth(Double.MAX_VALUE);

        // Ukos w stylu P3R; klikalność zachowana (transformacja na węźle sceny).
        PersonaMenuFx.slant(row, -0.10);
        PersonaMenuFx.SlideControl slide = PersonaMenuFx.hoverSlide(row, 14);

        final int rowIndex = navRows.size();
        String songId = ready ? entry.context().songId() : null;
        navRows.add(new RowHandle(row, action, ready, slide, songId, entry.title()));

        applyRowStyle(row, ready, false);
        // Zaznaczenie ustawia WYŁĄCZNIE klik — dzięki temu można wybrać utwór, a potem
        // przejechać kursorem po innych wierszach (np. do przycisku „Historia") bez
        // gubienia wyboru. Najechanie daje tylko chwilowe podświetlenie.
        row.setOnMouseClicked(e -> select(rowIndex));
        row.setOnMouseEntered(e -> hoverRow(rowIndex, true));
        row.setOnMouseExited(e -> hoverRow(rowIndex, false));

        action.setOnAction(e -> {
            if (ctx[0] != null) {
                startGame(ctx[0], action);
            } else {
                generateBeatmap(entry, action, row, badge, meta, ctx);
            }
        });

        return row;
    }

    /**
     * Kafelek rangi (litera w foncie Bebas Neue, kolor wg {@link Rank}). Gdy utwór
     * nie był jeszcze grany — neutralny myślnik. Czyta wyłącznie {@link StatsStore}.
     */
    private StackPane rankTile(String songId) {
        Label rk = new Label("–");
        rk.setFont(PersonaFonts.display(30));
        rk.setAlignment(Pos.CENTER);
        rk.setMaxWidth(Double.MAX_VALUE);
        rk.setMaxHeight(Double.MAX_VALUE);
        rk.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        if (songId != null) {
            stats.forSong(songId).filter(s -> s.plays > 0).ifPresent(s -> {
                Rank r = s.rank();
                rk.setText(r.label());
                rk.setTextFill(r.color());
            });
        }

        StackPane box = new StackPane(rk);
        box.setMinSize(34, 54);
        box.setPrefSize(34, 54);
        box.setMaxSize(34, 54);
        StackPane.setAlignment(rk, Pos.CENTER);
        return box;
    }

    // ── przyciski (jeden rozmiar wszędzie) ─────────────────────────────────

    private static Button rowButton(String text, boolean warn) {
        Button b = new Button(text);
        b.setStyle(warn ? PersonaMenuTheme.rowActionWarn() : PersonaMenuTheme.rowActionPrimary());
        b.setMinSize(PersonaMenuTheme.BTN_WIDTH_ROW, PersonaMenuTheme.BTN_HEIGHT);
        b.setPrefSize(PersonaMenuTheme.BTN_WIDTH_ROW, PersonaMenuTheme.BTN_HEIGHT);
        b.setMaxSize(PersonaMenuTheme.BTN_WIDTH_ROW, PersonaMenuTheme.BTN_HEIGHT);
        return b;
    }

    private static Button toolbarButton(String text) {
        Button b = new Button(text);
        b.setStyle(PersonaMenuTheme.toolbarButton());
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setMinHeight(PersonaMenuTheme.BTN_HEIGHT);
        b.setPrefHeight(PersonaMenuTheme.BTN_HEIGHT);
        b.setMaxHeight(PersonaMenuTheme.BTN_HEIGHT);
        return b;
    }

    /** Przycisk z ikoną zębatki (⚙) otwierający ustawienia. */
    private static Button gearButton() {
        Button b = new Button("\u2699");
        b.setStyle(PersonaMenuTheme.gearButton());
        b.setMinSize(PersonaMenuTheme.BTN_HEIGHT, PersonaMenuTheme.BTN_HEIGHT);
        b.setPrefSize(PersonaMenuTheme.BTN_HEIGHT, PersonaMenuTheme.BTN_HEIGHT);
        b.setMaxSize(PersonaMenuTheme.BTN_HEIGHT, PersonaMenuTheme.BTN_HEIGHT);
        return b;
    }

    private static ImageView createLogo() {
        var url = MenuScreen.class.getResource("/images/menu-logo.png");
        if (url == null) {
            LOG.warning("Brak zasobu /images/menu-logo.png");
            return null;
        }
        ImageView logo = new ImageView(new Image(url.toExternalForm(), true));
        logo.setFitHeight(LOGO_H);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(PersonaMenuTheme.ACCENT, 0.3));
        glow.setRadius(6);
        logo.setEffect(glow);
        return logo;
    }

    // ── logika ─────────────────────────────────────────────────────────────

    public void reload() {
        songsList.getChildren().clear();
        navRows.clear();
        selectedIndex = -1;
        try {
            List<SongEntry> entries = new SongLibrary(songsDir).scan();
            if (entries.isEmpty()) {
                Label empty = new Label("Brak utworów.\nKliknij Songs i wrzuć pliki .mp3 lub .wav.");
                empty.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
                empty.setFont(PersonaMenuTheme.bodyFont(12));
                empty.setWrapText(true);
                empty.setStyle(PersonaMenuTheme.emptyCard());
                songsList.getChildren().add(empty);
                setStatus("Pusty folder songs");
                return;
            }
            int i = 1;
            for (SongEntry e : entries) {
                HBox row = buildRow(e, i);
                // Asymetryczne wcięcie (nachodzące bloki w stylu P3R), przez margines
                // layoutu — nie koliduje z animacją wysuwania (translateX).
                VBox.setMargin(row, new Insets(0, 0, 0, (i % 2 == 0) ? 18 : 0));
                songsList.getChildren().add(row);
                i++;
            }
            if (!navRows.isEmpty()) {
                select(0);
            }
            int ready = (int) entries.stream().filter(SongEntry::hasBeatmap).count();
            setStatus(entries.size() + " utworów · " + ready + " gotowych");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Skanowanie songs/ nie powiodło się", ex);
            setStatus("Błąd: " + ex.getMessage());
        }
    }

    private void openSongsFolder() {
        try {
            Files.createDirectories(songsDir);
            if (!Desktop.isDesktopSupported()) {
                setStatus("Otwieranie folderu niedostępne");
                return;
            }
            Desktop.getDesktop().open(songsDir.toFile());
            setStatus("Otwarto folder songs");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się otworzyć folderu songs/", ex);
            setStatus("Nie udało się otworzyć songs/");
        }
    }

    private void startGame(SongContext ctx, Button button) {
        button.setDisable(true);
        onSongSelected.accept(ctx);
    }

    private void applyReadyRow(
            HBox row, Label badge, Label meta, Button action,
            SongContext[] ctx, SongEntry entry, SongContext generated) {
        ctx[0] = generated;
        action.setDisable(false);
        action.setText("Graj");
        action.setStyle(PersonaMenuTheme.rowActionPrimary());
        badge.setText("Gotowe");
        badge.setStyle(PersonaMenuTheme.badgeReady());
        meta.setText(metaLine(entry, generated));
        for (RowHandle h : navRows) {
            if (h.node == row) {
                h.ready = true;
                h.songId = generated.songId();
                break;
            }
        }
        boolean selectedNow = selectedIndex >= 0 && selectedIndex < navRows.size()
                && navRows.get(selectedIndex).node == row;
        if (selectedNow) {
            RowHandle h = navRows.get(selectedIndex);
            applySelectedStyle(h);
            updateStatsPanel(h);
        } else {
            applyRowStyle(row, true, false);
        }
    }

    private void generateBeatmap(
            SongEntry entry, Button button, HBox row,
            Label badge, Label meta, SongContext[] ctx) {
        button.setDisable(true);
        button.setText("…");
        setStatus("Analiza: " + entry.audioPath().getFileName());

        Task<SongContext> task = new Task<>() {
            @Override
            protected SongContext call() throws Exception {
                BeatmapEngine engine = new BeatmapEngine();
                SongContext g = engine.generateAndSave(
                        entry.audioPath(), entry.beatmapPath(),
                        entry.title(), entry.title());
                return withAbsoluteAudio(g, entry.audioPath());
            }
        };
        task.setOnSucceeded(ev -> {
            setStatus("Gotowe — kliknij Graj");
            applyReadyRow(row, badge, meta, button, ctx, entry, task.getValue());
        });
        task.setOnFailed(ev -> {
            Throwable t = task.getException();
            LOG.log(Level.WARNING, "Generacja beatmapy nie powiodła się", t);
            button.setDisable(false);
            button.setText("Ponów");
            setStatus("Błąd: " + (t != null ? t.getMessage() : "?"));
        });
        Thread thread = new Thread(task, "BeatmapGen-" + entry.title());
        thread.setDaemon(true);
        thread.start();
    }

    private static void applyRowStyle(HBox row, boolean ready, boolean hover) {
        row.setStyle(hover ? PersonaMenuTheme.cardRowHover(ready) : PersonaMenuTheme.cardRow(ready));
    }

    /** Świecąca poświata wokół zaznaczonego wiersza (jeden wiersz na raz). */
    private static final DropShadow SELECTION_GLOW = makeSelectionGlow();

    private static DropShadow makeSelectionGlow() {
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(PersonaMenuTheme.ACCENT));
        glow.setRadius(18);
        glow.setSpread(0.28);
        return glow;
    }

    private void applySelectedStyle(RowHandle h) {
        h.node.setStyle(PersonaMenuTheme.cardRowSelected(h.ready));
        h.node.setEffect(SELECTION_GLOW);
    }

    private void applyUnselectedStyle(RowHandle h) {
        h.node.setStyle(PersonaMenuTheme.cardRow(h.ready));
        h.node.setEffect(null);
    }

    /** Chwilowe podświetlenie najechanego wiersza — nie zmienia zaznaczenia. */
    private void hoverRow(int index, boolean on) {
        if (index < 0 || index >= navRows.size() || index == selectedIndex) {
            return;
        }
        RowHandle h = navRows.get(index);
        applyRowStyle(h.node, h.ready, on);
    }

    // ── nawigacja ───────────────────────────────────────────────────────────

    private void handleMenuKey(KeyEvent e) {
        // Gdy otwarte są ustawienia, klawisze obsługuje wyłącznie nakładka ustawień.
        if (settings.isOpen()) {
            settings.handleKey(e);
            return;
        }
        // Gdy otwarta jest historia, klawisze sterują tylko nią.
        if (historyOverlay != null) {
            if (e.getCode() == KeyCode.ESCAPE) {
                closeHistory();
            }
            e.consume();
            return;
        }
        switch (e.getCode()) {
            case ESCAPE -> {
                SoundManager.get().play(SoundManager.Sfx.BACK);
                onExit.run();
            }
            case DOWN, RIGHT -> moveSelection(1);
            case UP, LEFT -> moveSelection(-1);
            case ENTER, SPACE -> activateSelected();
            case H -> openHistoryForSelection();
            default -> { /* ignorujemy */ }
        }
    }

    private void moveSelection(int delta) {
        if (navRows.isEmpty()) {
            return;
        }
        int next = (selectedIndex < 0)
                ? (delta > 0 ? 0 : navRows.size() - 1)
                : Math.floorMod(selectedIndex + delta, navRows.size());
        select(next, SoundManager.Sfx.NAV);
    }

    private void select(int index) {
        select(index, SoundManager.Sfx.CLICK_GLASS);
    }

    private void select(int index, SoundManager.Sfx sfx) {
        if (index < 0 || index >= navRows.size() || index == selectedIndex) {
            return;
        }
        deselectCurrent();
        selectedIndex = index;
        RowHandle h = navRows.get(index);
        applySelectedStyle(h);
        h.slide.set(true);
        updateStatsPanel(h);
        SoundManager.get().play(sfx);
    }

    private void deselectCurrent() {
        if (selectedIndex < 0 || selectedIndex >= navRows.size()) {
            selectedIndex = -1;
            return;
        }
        RowHandle h = navRows.get(selectedIndex);
        applyUnselectedStyle(h);
        h.slide.set(false);
        selectedIndex = -1;
    }

    private void activateSelected() {
        if (selectedIndex >= 0 && selectedIndex < navRows.size()) {
            SoundManager.get().play(SoundManager.Sfx.CONFIRM);
            navRows.get(selectedIndex).action.fire();
        }
    }

    // ── historia podejść (nakładka) ──────────────────────────────────────────

    private void openHistoryForSelection() {
        if (historyOverlay != null) {
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= navRows.size()) {
            setStatus("Najpierw wybierz utwór");
            return;
        }
        RowHandle h = navRows.get(selectedIndex);
        if (h.songId == null) {
            setStatus("Najpierw wygeneruj mapę dla tego utworu");
            return;
        }
        List<StatsStore.PlayRecord> records = stats.history(h.songId, HISTORY_LIMIT);
        historyOverlay = buildHistoryOverlay(h.title, records);
        root.getChildren().add(historyOverlay);
        SoundManager.get().play(SoundManager.Sfx.CONFIRM);
    }

    private void closeHistory() {
        if (historyOverlay != null) {
            root.getChildren().remove(historyOverlay);
            historyOverlay = null;
            SoundManager.get().play(SoundManager.Sfx.BACK);
        }
    }

    private StackPane buildHistoryOverlay(String title, List<StatsStore.PlayRecord> records) {
        Label heading = new Label("HISTORIA");
        heading.setFont(PersonaFonts.display(34));
        heading.setTextFill(Color.web(PersonaMenuTheme.ACCENT));

        Label sub = ellipsisLabel(title != null ? title : "—");
        sub.setFont(PersonaFonts.label(13));
        sub.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));

        VBox head = new VBox(-2, heading, sub);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(6);
        list.setFillWidth(true);
        if (records.isEmpty()) {
            Label empty = new Label("Brak podejść — zagraj ten utwór, by zobaczyć historię.");
            empty.setWrapText(true);
            empty.setFont(PersonaMenuTheme.bodyFont(12));
            empty.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
            list.getChildren().add(empty);
        } else {
            int attempt = records.size();
            for (StatsStore.PlayRecord rec : records) {
                list.getChildren().add(historyRow(rec, attempt));
                attempt--;
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(PersonaMenuTheme.scrollPane());
        scroll.setPrefHeight(HEIGHT * 0.5);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label hint = new Label("ESC — zamknij");
        hint.setFont(PersonaFonts.body(12));
        hint.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));

        VBox panel = new VBox(12, head, scroll, hint);
        panel.setPadding(new Insets(20, 22, 16, 22));
        panel.setMaxWidth(WIDTH - 56);
        panel.setMaxHeight(HEIGHT - 90);
        panel.setStyle(PersonaMenuTheme.historyPanel());
        PersonaMenuFx.slant(heading, -0.16);

        StackPane overlay = new StackPane(panel);
        StackPane.setAlignment(panel, Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(2, 6, 14, 0.78);");
        // Kliknięcie poza panelem zamyka; kliknięcie w panel — nie.
        overlay.setOnMouseClicked(e -> closeHistory());
        panel.setOnMouseClicked(javafx.event.Event::consume);
        return overlay;
    }

    private HBox historyRow(StatsStore.PlayRecord rec, int attempt) {
        Rank r = rec.rank();

        Label rank = new Label(r.label());
        rank.setFont(PersonaFonts.display(26));
        rank.setTextFill(r.color());
        rank.setAlignment(Pos.CENTER);
        rank.setMaxWidth(Double.MAX_VALUE);
        rank.setMaxHeight(Double.MAX_VALUE);

        StackPane rankBox = new StackPane(rank);
        rankBox.setMinSize(34, Region.USE_PREF_SIZE);
        rankBox.setPrefSize(34, Region.USE_PREF_SIZE);
        rankBox.setMaxSize(34, Region.USE_PREF_SIZE);
        StackPane.setAlignment(rank, Pos.CENTER);

        Label score = new Label("#" + attempt + "   " + formatNumber(rec.score) + " pkt");
        score.setFont(PersonaMenuTheme.labelFont(13));
        score.setTextFill(Color.web(PersonaMenuTheme.TEXT));

        Label detail = new Label(String.format(
                "PERFECT %d · GREAT %d · MISS %d · COMBO %d · %.1f%%",
                rec.perfect, rec.great, rec.misses, rec.maxCombo, rec.accuracy * 100.0));
        detail.setFont(PersonaMenuTheme.bodyFont(10));
        detail.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));

        VBox info = new VBox(2, score, detail);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label date = new Label(HISTORY_DATE.format(java.time.Instant.ofEpochMilli(rec.playedAt)));
        date.setFont(PersonaMenuTheme.bodyFont(10));
        date.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));

        HBox row = new HBox(12, rankBox, info, date);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle(PersonaMenuTheme.historyRow(toHex(r.color())));
        return row;
    }

    private void openSettings() {
        settings.open(root);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    /** Widokowy uchwyt wiersza dla nawigacji (węzeł, akcja, stan, animacja, statystyki). */
    private static final class RowHandle {
        final HBox node;
        final Button action;
        boolean ready;
        final PersonaMenuFx.SlideControl slide;
        String songId;
        final String title;

        RowHandle(HBox node, Button action, boolean ready, PersonaMenuFx.SlideControl slide,
                  String songId, String title) {
            this.node = node;
            this.action = action;
            this.ready = ready;
            this.slide = slide;
            this.songId = songId;
            this.title = title;
        }
    }

    private static Label ellipsisLabel(String text) {
        Label l = new Label(text);
        l.setMinWidth(0);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setEllipsisString("...");
        return l;
    }

    private static String metaLine(SongEntry entry, SongContext ctx) {
        String m = entry.audioPath().getFileName().toString();
        if (ctx != null) {
            m += " · " + ctx.notes().size() + " nut · " + ctx.bpm() + " BPM";
        }
        return m;
    }

    private static SongContext withAbsoluteAudio(SongContext ctx, Path audio) {
        return new SongContext(
                ctx.songId(), ctx.title(), ctx.bpm(),
                audio.toAbsolutePath().toString(), ctx.notes());
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }
}
