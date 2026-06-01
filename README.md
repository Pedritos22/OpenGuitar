# OpenGuitar

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-FF6A00?logo=java&logoColor=white)](https://openjfx.io/)
[![Tests](https://img.shields.io/badge/Tests-65-25A162?logo=junit5&logoColor=white)](DOCUMENTATION.md#12-testy)
[![GitHub](https://img.shields.io/badge/GitHub-OpenGuitar-181717?logo=github)](https://github.com/Pedritos22/OpenGuitar)

Klon **Guitar Hero** na desktop — Java 21, Maven, JavaFX. Projekt akademicki składający się z dwóch modułów:

| Moduł | Pakiet | Odpowiedzialność |
|-------|--------|------------------|
| **Beatmapa** | `com.openguitar.beatmap` | Analiza audio (DSP), generowanie nut, zapis JSON |
| **Gra** | `com.openguitar.game` | Menu, rozgrywka, hit detection, statystyki, dźwięk, UI |

Interfejs inspirowany estetyką **Persona 3 Reload** (P3R): ukośne panele, neonowy błękit, fonty Bebas Neue + Rajdhani, kryształowe nuty na perspektywicznej autostradzie.

Szczegóły techniczne (architektura, synchronizacja audio, format plików, testy) → **[DOCUMENTATION.md](DOCUMENTATION.md)**.

---

## Wymagania

- **JDK 21** (JavaFX jest w zależnościach Maven — osobna instalacja JavaFX nie jest potrzebna)
- **Maven 3.9+**

---

## Szybki start

1. Wrzuć pliki audio do `songs/`:

   ```
   songs/moj-utwor.mp3
   songs/inny.wav
   ```

2. Uruchom grę:

   ```bash
   ./play.sh
   ```

3. W menu wybierz utwór i kliknij **Graj** (lub **Generuj**, jeśli brak beatmapy `.json`).

### Sterowanie w grze

| Akcja | Domyślnie |
|-------|-----------|
| Ścieżki 1–4 | **D** **F** **J** **K** (konfigurowalne w ustawieniach) |
| Pauza | **ESC** → menu pauzy (Wznów / Wyjdź do menu) |
| Pełny ekran | **F11** (zachowywany przy przejściu menu ↔ gra) |

Przed startem utworu (i po wznowieniu z pauzy) pojawia się **odliczanie 3→2→1→GO!** — czas na ustawienie palców. Długość można zmienić w ustawieniach (0 = wyłączone).

Po zakończeniu utworu wyświetla się **animowany ekran wyników** (PERFECT / GREAT / MISS, combo, celność, ranga S–E). **ENTER** wraca do menu.

### Menu główne

- **Lista utworów** — kliknij wiersz, aby zaznaczyć; strzałki + Enter też działają.
- **Statystyki** — panel u góry pokazuje dane wybranego utworu (podejścia, rekord, combo).
- **Ranga** — litera S–E obok tytułu (najlepszy wynik).
- **Historia** — ostatnie podejścia dla zaznaczonego utworu (przycisk lub klawisz **H**).
- **⚙ Ustawienia** — klawisze ścieżek, głośność, dźwięki trafień, odliczanie, komunikaty wizualne (panel przewijany).
- **Odśwież** — ponowny skan folderu `songs/`.

### Dźwięk

| Kontekst | Co słychać |
|----------|------------|
| **Menu (lobby)** | Losowa rotacja `song_lobby` / `song_ending` z płynnym crossfade'm |
| **Gra** | Odtwarzany utwór z folderu `songs/` (głośność w ustawieniach) |
| **Wyniki** | `song_ending` w pętli |
| **UI** | Szklane kliki, nawigacja, odliczanie, pauza |
| **Trafienia** | Dźwięki PERFECT/GREAT/MISS/combo (można wyłączyć w ustawieniach) |

Głośność lobby i piosenek regulujesz suwakami w **⚙ Ustawienia → DŹWIĘK**.

---

## Tryby uruchomienia

```bash
./play.sh                              # menu (domyślne)
./play.sh songs/utwor.mp3              # od razu gra (auto-generacja beatmapy)
./play.sh songs/utwor.json             # gra z gotową mapą
./play.sh songs/utwor.mp3 --regen      # wymuś regenerację beatmapy
./play.sh --list                       # lista plików audio w songs/
```

**Logi diagnostyczne** — `./play.sh` ładuje `src/main/resources/logging.properties`. W konsoli widać zdarzenia aplikacji, audio lobby i rozgrywki (prefiks `[komponent][FX|BG]`, np. `[game][FX] start()`). Szczegóły w [DOCUMENTATION.md §14](DOCUMENTATION.md#14-logi-diagnostyczne).

Równoważne komendy Maven:

```bash
mvn javafx:run
mvn javafx:run -Djavafx.args="songs/utwor.json"
mvn test                               # 65 testów jednostkowych (beatmap + gra)
```

### Ostrzeżenia JVM przy starcie (macOS / Java 21+)

Przy `./play.sh` lub `mvn javafx:run` mogą pojawić się żółte linie `WARNING: A restricted method in java.lang.System has been called` — to **nie błąd gry**, tylko informacja Javy o ładowaniu bibliotek natywnych (JavaFX, opcjonalnie SQLite przy statystykach). Gra powinna działać normalnie. Wyjaśnienie → [DOCUMENTATION.md §14](DOCUMENTATION.md#14-logi-diagnostyczne).

---

## Pliki generowane w runtime

Te pliki powstają obok repozytorium podczas gry i **nie są commitowane** (patrz `.gitignore`):

| Plik | Opis |
|------|------|
| `stats.db` | Statystyki i historia podejść (SQLite) |
| `settings.properties` | Klawisze, głośność, dźwięki, odliczanie, opcje UI |
| `songs/*.json` | Beatmapy — **można** commitować, jeśli chcesz się nimi dzielić |
| `songs/*.{mp3,wav,...}` | Audio użytkownika — **nie commituj** (rozmiar, prawa autorskie) |

> **Uwaga:** starszy format `stats.json` nie jest już używany — statystyki migrowały do `stats.db`. Jeśli masz lokalny `stats.json`, możesz go bezpiecznie usunąć.

---

## Struktura repozytorium (skrót)

```
OpenGuitar/
├── play.sh                 # wrapper uruchomieniowy
├── songs/                  # audio + beatmapy JSON
├── src/main/java/
│   ├── com/openguitar/beatmap/   # silnik analizy audio
│   └── com/openguitar/game/     # aplikacja JavaFX
├── src/main/resources/
│   ├── fonts/                    # Bebas Neue, Rajdhani (OFL)
│   ├── images/                   # logo menu
│   ├── logging.properties        # konfiguracja logów konsoli (INFO / FINE)
│   └── sound/                    # muzyka lobby + SFX (Kenney CC0)
├── src/test/java/                # testy JUnit 5 (beatmap + game)
├── README.md                     # ten plik
└── DOCUMENTATION.md              # dokumentacja techniczna
```

---

## Licencje zasobów

| Zasób | Licencja |
|-------|----------|
| Fonty (`src/main/resources/fonts/`) | **SIL Open Font License** — szczegóły w `OFL-*.txt` |
| Efekty UI (`src/main/resources/sound/sfx_*.wav`) | **CC0** — pakiet [Kenney UI Audio](https://kenney.nl/assets/ui-audio); patrz `KENNEY_UI_AUDIO_LICENSE.txt` |
| Muzyka lobby (`song_lobby.mp3`, `song_ending.mp3`) | Pliki projektu w repozytorium |
