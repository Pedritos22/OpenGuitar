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
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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

    private final Path songsDir;
    private final Consumer<SongContext> onSongSelected;
    private final Runnable onExit;
    private final StatsStore stats;

    private final Scene scene;
    private final VBox songsList;
    private final Label statusLabel;

    /** Uchwyty wierszy do nawigacji klawiaturą (czysto widokowe). */
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

        StackPane root = new StackPane(background, content);
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
        Button refresh = toolbarButton("Odśwież");
        refresh.setOnAction(e -> reload());
        Button exit = toolbarButton("Wyjście");
        exit.setOnAction(e -> onExit.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8, refresh, exit);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(10, statusLabel, spacer, actions);
        bar.setAlignment(Pos.CENTER_LEFT);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        VBox footer = new VBox(bar);
        footer.setStyle(PersonaMenuTheme.statusBar());
        footer.setMaxWidth(Double.MAX_VALUE);
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

        HBox row = new HBox(10, num, info, actions);
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
        row.setOnMouseEntered(e -> select(rowIndex));
        row.setOnMouseExited(e -> {
            if (selectedIndex == rowIndex) deselectCurrent();
        });

        action.setOnAction(e -> {
            if (ctx[0] != null) {
                startGame(ctx[0], action);
            } else {
                generateBeatmap(entry, action, row, badge, meta, ctx);
            }
        });

        return row;
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
        b.setMinHeight(PersonaMenuTheme.BTN_HEIGHT);
        b.setPrefHeight(PersonaMenuTheme.BTN_HEIGHT);
        b.setMaxHeight(PersonaMenuTheme.BTN_HEIGHT);
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
        applyRowStyle(row, true, selectedNow);
        if (selectedNow) {
            updateStatsPanel(navRows.get(selectedIndex));
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

    // ── nawigacja (czysto widokowa) ─────────────────────────────────────────

    private void handleMenuKey(KeyEvent e) {
        switch (e.getCode()) {
            case ESCAPE -> onExit.run();
            case DOWN, RIGHT -> moveSelection(1);
            case UP, LEFT -> moveSelection(-1);
            case ENTER, SPACE -> activateSelected();
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
        select(next);
    }

    private void select(int index) {
        if (index < 0 || index >= navRows.size() || index == selectedIndex) {
            return;
        }
        deselectCurrent();
        selectedIndex = index;
        RowHandle h = navRows.get(index);
        applyRowStyle(h.node, h.ready, true);
        h.slide.set(true);
        updateStatsPanel(h);
    }

    private void deselectCurrent() {
        if (selectedIndex < 0 || selectedIndex >= navRows.size()) {
            selectedIndex = -1;
            return;
        }
        RowHandle h = navRows.get(selectedIndex);
        applyRowStyle(h.node, h.ready, false);
        h.slide.set(false);
        selectedIndex = -1;
    }

    private void activateSelected() {
        if (selectedIndex >= 0 && selectedIndex < navRows.size()) {
            navRows.get(selectedIndex).action.fire();
        }
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
