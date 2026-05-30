# Folder `songs/`

Tutaj wrzucasz pliki audio (`.mp3`, `.wav`, ewentualnie `.aiff`).

Po wygenerowaniu beatmapy obok każdego utworu pojawi się plik `.json`:

```
songs/
├── mojautwor.mp3        # twój plik audio (NIE jest commitowany)
├── mojautwor.json       # wygenerowana beatmapa (commitowana, jeśli chcesz)
├── inna-piosenka.wav
└── inna-piosenka.json
```

## Szybki start

Z roota repozytorium:

```bash
./play.sh songs/mojautwor.mp3        # parsuje + uruchamia grę
./play.sh songs/mojautwor.json       # uruchamia grę (beatmapa już istnieje)
./play.sh --list                     # listuje dostępne utwory
```

Patrz `README.md` w roocie po szczegóły.
