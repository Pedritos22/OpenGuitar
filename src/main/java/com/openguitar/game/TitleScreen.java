package com.openguitar.game;

import java.util.ArrayList;
import java.util.List;

import com.openguitar.game.view.FullscreenScaler;
import com.openguitar.game.view.PersonaFonts;
import com.openguitar.game.view.PersonaMenuFx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Ekran tytułowy: GRAJ, USTAWIENIA, WYJŚCIE (styl P3R). */
public final class TitleScreen {

    public static final int WIDTH  = GameScreen.CANVAS_WIDTH;
    public static final int HEIGHT = GameScreen.CANVAS_HEIGHT;

    private static final double LOGO_H = 92;

    private final Runnable onExit;

    private final Scene scene;
    private final StackPane root;
    private final SettingsOverlay settings = new SettingsOverlay(WIDTH);

    private final List<Item> items = new ArrayList<>();
    private int selectedIndex = 0;

    public TitleScreen(Runnable onPlay, Runnable onExit) {
        this.onExit = onExit;

        MenuBackground background = new MenuBackground();
        background.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox content = new VBox(34, buildBrand(), buildMenu(onPlay));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(56, 64, 56, 72));
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        this.root = new StackPane(background, content);
        this.scene = new Scene(FullscreenScaler.wrap(root, WIDTH, HEIGHT), WIDTH, HEIGHT);
        this.scene.setFill(Color.web(PersonaMenuTheme.BG_DEEP));
        this.scene.setOnKeyPressed(this::handleKey);

        select(0, false);
    }

    public Scene getScene() {
        return scene;
    }

    // ── widok ────────────────────────────────────────────────────────────────

    private VBox buildBrand() {
        Label open = new Label("Open");
        open.setFont(PersonaFonts.display(76));
        open.setTextFill(Color.web(PersonaMenuTheme.TEXT));

        Label guitar = new Label("Guitar");
        guitar.setFont(PersonaFonts.display(76));
        guitar.setTextFill(Color.web(PersonaMenuTheme.ACCENT));

        HBox word = new HBox(open, guitar);
        word.setAlignment(Pos.CENTER_LEFT);
        PersonaMenuFx.slant(word, -0.2);
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(PersonaMenuTheme.ACCENT, 0.45));
        glow.setRadius(22);
        glow.setSpread(0.18);
        word.setEffect(glow);

        HBox brandRow = new HBox(18);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        ImageView logo = createLogo();
        if (logo != null) {
            brandRow.getChildren().add(logo);
        }
        brandRow.getChildren().add(word);

        Label tagline = new Label("· CZY DOTRZYMASZ RYTMU?");
        tagline.setFont(PersonaFonts.label(16));
        tagline.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));
        tagline.setPadding(new Insets(2, 0, 0, 4));

        Region divider = new Region();
        divider.setStyle(PersonaMenuTheme.divider());
        divider.setMaxWidth(360);
        VBox.setMargin(divider, new Insets(8, 0, 0, 4));

        VBox brand = new VBox(4, brandRow, tagline, divider);
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    private VBox buildMenu(Runnable onPlay) {
        VBox menu = new VBox(12);
        menu.setAlignment(Pos.CENTER_LEFT);
        menu.getChildren().addAll(
                buildItem("GRAJ", "Wybierz utwór i zagraj", () -> {
                    SoundManager.get().play(SoundManager.Sfx.CONFIRM);
                    onPlay.run();
                }),
                buildItem("USTAWIENIA", "Sterowanie, dźwięk i rozgrywka",
                        () -> settings.open(root)),
                buildItem("WYJŚCIE", "Zamknij grę", () -> {
                    SoundManager.get().play(SoundManager.Sfx.BACK);
                    onExit.run();
                }));
        return menu;
    }

    private HBox buildItem(String text, String subtitle, Runnable action) {
        Region marker = new Region();
        marker.setMinSize(6, 40);
        marker.setPrefSize(6, 40);
        marker.setMaxSize(6, 40);
        marker.setStyle("-fx-background-color: " + PersonaMenuTheme.ACCENT + ";");

        Label label = new Label(text);
        label.setFont(PersonaFonts.display(40));
        label.setTextFill(Color.web(PersonaMenuTheme.TEXT));

        Label sub = new Label(subtitle);
        sub.setFont(PersonaFonts.body(12));
        sub.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));

        VBox texts = new VBox(-4, label, sub);
        texts.setAlignment(Pos.CENTER_LEFT);

        HBox node = new HBox(16, marker, texts);
        node.setAlignment(Pos.CENTER_LEFT);
        node.setPadding(new Insets(8, 26, 8, 16));
        node.setMinWidth(420);
        node.setMaxWidth(Region.USE_PREF_SIZE);
        PersonaMenuFx.slant(node, -0.14);
        PersonaMenuFx.SlideControl slide = PersonaMenuFx.hoverSlide(node, 22);

        final int index = items.size();
        Item item = new Item(node, label, marker, action, slide);
        items.add(item);
        applyItemStyle(item, false);

        node.setOnMouseEntered(e -> select(index, true));
        node.setOnMouseClicked(e -> {
            select(index, false);
            action.run();
        });
        return node;
    }

    private static ImageView createLogo() {
        var url = TitleScreen.class.getResource("/images/menu-logo.png");
        if (url == null) {
            return null;
        }
        ImageView logo = new ImageView(new Image(url.toExternalForm(), true));
        logo.setFitHeight(LOGO_H);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(PersonaMenuTheme.ACCENT, 0.35));
        glow.setRadius(10);
        logo.setEffect(glow);
        return logo;
    }

    // ── nawigacja ──────────────────────────────────────────────────────────

    private void handleKey(KeyEvent e) {
        if (settings.isOpen()) {
            settings.handleKey(e);
            return;
        }
        switch (e.getCode()) {
            case DOWN, RIGHT -> move(1);
            case UP, LEFT -> move(-1);
            case ENTER, SPACE -> activate();
            case ESCAPE -> {
                SoundManager.get().play(SoundManager.Sfx.BACK);
                onExit.run();
            }
            default -> { /* ignorujemy */ }
        }
        e.consume();
    }

    private void move(int delta) {
        if (items.isEmpty()) {
            return;
        }
        int next = Math.floorMod(selectedIndex + delta, items.size());
        select(next, true);
    }

    private void select(int index, boolean withSound) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        if (initialized && index == selectedIndex) {
            return;
        }
        if (initialized && selectedIndex >= 0 && selectedIndex < items.size()) {
            Item prev = items.get(selectedIndex);
            prev.slide.set(false);
            applyItemStyle(prev, false);
        }
        selectedIndex = index;
        initialized = true;
        Item cur = items.get(index);
        cur.slide.set(true);
        applyItemStyle(cur, true);
        if (withSound) {
            SoundManager.get().play(SoundManager.Sfx.NAV);
        }
    }

    private boolean initialized;

    private void activate() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            items.get(selectedIndex).action.run();
        }
    }

    private static void applyItemStyle(Item item, boolean selected) {
        item.label.setTextFill(Color.web(selected ? PersonaMenuTheme.ACCENT_GLOW : PersonaMenuTheme.TEXT));
        item.marker.setStyle("-fx-background-color: "
                + (selected ? PersonaMenuTheme.ACCENT_GLOW : PersonaMenuTheme.BORDER) + ";");
        item.node.setStyle(selected
                ? "-fx-background-color: linear-gradient(to right,"
                        + " rgba(0, 229, 255, 0.22), transparent);"
                : "-fx-background-color: transparent;");
    }

    /** Widokowy uchwyt pozycji menu tytułowego. */
    private static final class Item {
        final HBox node;
        final Label label;
        final Region marker;
        final Runnable action;
        final PersonaMenuFx.SlideControl slide;

        Item(HBox node, Label label, Region marker, Runnable action, PersonaMenuFx.SlideControl slide) {
            this.node = node;
            this.label = label;
            this.marker = marker;
            this.action = action;
            this.slide = slide;
        }
    }
}
