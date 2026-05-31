package com.openguitar.game;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Nakładka ustawień w głównym menu.
 *
 * <p>Umożliwia zmianę keybindów ścieżek i zapis do pliku konfiguracyjnego.</p>
 */
public final class SettingsOverlay {

    private final StackPane root = new StackPane();
    private final Label status = new Label("Wybierz pole i naciśnij nowy klawisz");
    private final Button[] keyButtons = new Button[GameSettings.LANES];
    private final Consumer<GameSettings> onSave;
    private final Runnable onClose;

    private GameSettings settings;
    private int captureLane = -1;

    public SettingsOverlay(GameSettings initial, Consumer<GameSettings> onSave, Runnable onClose) {
        this.settings = Objects.requireNonNull(initial, "initial");
        this.onSave = Objects.requireNonNull(onSave, "onSave");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        buildUi();
        refresh();
    }

    public Node root() {
        return root;
    }

    public void focusFirstField() {
        if (keyButtons.length > 0) {
            keyButtons[0].requestFocus();
        }
    }

    public boolean handleKeyPressed(KeyEvent e) {
        if (captureLane >= 0) {
            if (e.getCode() == KeyCode.ESCAPE) {
                captureLane = -1;
                status.setText("Zmiana klawisza anulowana");
                e.consume();
                return true;
            }
            if (isAssignable(e.getCode())) {
                tryAssign(e.getCode());
            } else {
                status.setText("Ten klawisz nie może być użyty");
            }
            e.consume();
            return true;
        }

        if (e.getCode() == KeyCode.ESCAPE) {
            close();
            e.consume();
            return true;
        }
        if (e.getCode() == KeyCode.ENTER) {
            saveAndClose();
            e.consume();
            return true;
        }
        return false;
    }

    public void setWorkingSettings(GameSettings updated) {
        this.settings = Objects.requireNonNull(updated, "updated");
        refresh();
    }

    private void buildUi() {
        root.setStyle("-fx-background-color: rgba(2, 6, 14, 0.82);");
        root.setPickOnBounds(true);
        root.setOnMouseClicked(e -> close());

        Label title = new Label("USTAWIENIA");
        title.setFont(PersonaMenuTheme.titleFont());
        title.setTextFill(Color.web(PersonaMenuTheme.ACCENT));

        Label subtitle = new Label("Zmień keybindy dla czterech ścieżek i zapisz konfigurację");
        subtitle.setFont(PersonaMenuTheme.bodyFont(12));
        subtitle.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
        subtitle.setWrapText(true);
        subtitle.setTextAlignment(TextAlignment.LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        for (int lane = 0; lane < GameSettings.LANES; lane++) {
            Label laneLabel = new Label("Ścieżka " + (lane + 1));
            laneLabel.setFont(PersonaMenuTheme.labelFont(13));
            laneLabel.setTextFill(Color.web(PersonaMenuTheme.TEXT));

            Button btn = new Button();
            btn.setPrefWidth(128);
            final int laneIndex = lane;
            btn.setOnAction(e -> beginCapture(laneIndex));
            keyButtons[lane] = btn;

            grid.add(laneLabel, 0, lane);
            grid.add(btn, 1, lane);
        }

        Button save = new Button("Zapisz");
        save.setStyle(PersonaMenuTheme.rowActionPrimary());
        save.setOnAction(e -> saveAndClose());

        Button reset = new Button("Przywróć domyślne");
        reset.setStyle(PersonaMenuTheme.toolbarButton());
        reset.setOnAction(e -> {
            settings = GameSettings.defaults();
            captureLane = -1;
            refresh();
            status.setText("Przywrócono domyślne keybindy");
        });

        Button cancel = new Button("Anuluj");
        cancel.setStyle(PersonaMenuTheme.rowActionWarn());
        cancel.setOnAction(e -> close());

        HBox actions = new HBox(10, save, reset, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(14, title, subtitle, grid, status, actions);
        panel.setPadding(new Insets(22, 24, 20, 24));
        panel.setMaxWidth(560);
        panel.setStyle(PersonaMenuTheme.historyPanel());

        StackPane.setAlignment(panel, Pos.CENTER);
        panel.setOnMouseClicked(javafx.event.Event::consume);
        root.getChildren().add(panel);
    }

    private void refresh() {
        for (int lane = 0; lane < GameSettings.LANES; lane++) {
            Button btn = keyButtons[lane];
            if (btn == null) {
                continue;
            }
            btn.setText(settings.laneKeyName(lane));
            btn.setStyle(buttonStyle(lane, captureLane == lane));
        }
        status.setFont(PersonaMenuTheme.bodyFont(12));
        status.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
    }

    private String buttonStyle(int lane, boolean capturing) {
        String accent = capturing ? PersonaMenuTheme.ACCENT_GLOW : PersonaMenuTheme.ACCENT;
        return "-fx-background-color: rgba(0, 212, 255, 0.08);"
             + "-fx-text-fill: " + PersonaMenuTheme.TEXT + ";"
             + "-fx-font-family: 'Rajdhani';"
             + "-fx-font-size: 14px;"
             + "-fx-font-weight: bold;"
             + "-fx-padding: 8 14 8 14;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + accent + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }

    private void beginCapture(int lane) {
        captureLane = lane;
        status.setText("Naciśnij nowy klawisz dla ścieżki " + (lane + 1));
        refresh();
        keyButtons[lane].requestFocus();
    }

    private void tryAssign(KeyCode key) {
        if (captureLane < 0) {
            return;
        }
        if (key == null || !isAssignable(key)) {
            status.setText("Ten klawisz nie może być użyty");
            return;
        }
        if (isDuplicate(key, captureLane)) {
            status.setText("Ten klawisz jest już przypisany do innej ścieżki");
            return;
        }
        settings = settings.withLaneKey(captureLane, key);
        status.setText("Przypisano: ścieżka " + (captureLane + 1) + " → " + key.getName());
        captureLane = -1;
        refresh();
    }

    private boolean isDuplicate(KeyCode key, int ignoreLane) {
        for (int lane = 0; lane < GameSettings.LANES; lane++) {
            if (lane == ignoreLane) {
                continue;
            }
            if (settings.laneKey(lane) == key) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssignable(KeyCode key) {
        return key != null && key != KeyCode.ESCAPE && key != KeyCode.ENTER && key != KeyCode.TAB;
    }

    private void saveAndClose() {
        try {
            onSave.accept(settings);
            status.setText("Zapisano konfigurację");
            onClose.run();
        } catch (Exception ex) {
            status.setText("Nie udało się zapisać konfiguracji: " + ex.getMessage());
        }
    }

    private void close() {
        captureLane = -1;
        onClose.run();
    }
}
