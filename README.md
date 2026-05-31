# OpenGuitar

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-FF6A00?logo=java&logoColor=white)](https://openjfx.io/)
[![Tests](https://img.shields.io/badge/Tests-36-25A162?logo=junit5&logoColor=white)](DOCUMENTATION.md#11-testy)
[![GitHub](https://img.shields.io/badge/GitHub-OpenGuitar-181717?logo=github)](https://github.com/Pedritos22/OpenGuitar)

Klon **Guitar Hero** na desktop — Java 21, Maven, JavaFX. Projekt akademicki składający się z dwóch modułów:

| Moduł | Pakiet | Odpowiedzialność |
|-------|--------|------------------|
| **Beatmapa** | `com.openguitar.beatmap` | Analiza audio (DSP), generowanie nut, zapis JSON |
| **Gra** | `com.openguitar.game` | Menu, rozgrywka, hit detection, statystyki, UI |

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
- **⚙ Ustawienia** — przypisanie klawiszy, odliczanie, komunikaty trafień.
- **Odśwież** — ponowny skan folderu `songs/`.

---

## Tryby uruchomienia

```bash
./play.sh                              # menu (domyślne)
./play.sh songs/utwor.mp3              # od razu gra (auto-generacja beatmapy)
./play.sh songs/utwor.json             # gra z gotową mapą
./play.sh songs/utwor.mp3 --regen      # wymuś regenerację beatmapy
./play.sh --list                       # lista plików audio w songs/
```

Równoważne komendy Maven:

```bash
mvn javafx:run
mvn javafx:run -Djavafx.args="songs/utwor.json"
mvn test                               # testy jednostkowe
```

---

## Pliki generowane w runtime

Te pliki powstają obok repozytorium podczas gry i **nie są commitowane** (patrz `.gitignore`):

| Plik | Opis |
|------|------|
| `stats.db` | Statystyki i historia podejść (SQLite) |
| `settings.properties` | Klawisze ścieżek, odliczanie, opcje UI |
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
├── src/main/resources/fonts/     # Bebas Neue, Rajdhani (OFL)
├── README.md                     # ten plik
└── DOCUMENTATION.md              # dokumentacja techniczna
```

---

## Licencje fontów

Fonty w `src/main/resources/fonts/` pochodzą z Google Fonts i są na licencji **SIL Open Font License** — szczegóły w plikach `OFL-*.txt`.
