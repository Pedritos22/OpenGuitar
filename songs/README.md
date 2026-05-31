# Folder `songs/`

Tutaj wrzucasz pliki audio (`.mp3`, `.wav`, `.aiff`, `.flac`).

Po wygenerowaniu beatmapy obok każdego utworu pojawi się plik `.json`:

```
songs/
├── mojautwor.mp3        # twój plik audio (NIE commitowany)
├── mojautwor.json       # wygenerowana beatmapa (można commitować)
├── inna-piosenka.wav
└── inna-piosenka.json
```

## Szybki start

Z roota repozytorium:

```bash
./play.sh                    # menu — wybierz utwór i kliknij Graj
./play.sh songs/utwor.mp3    # parsuje + uruchamia grę
./play.sh songs/utwor.json   # gra z gotową mapą
./play.sh --list             # lista plików audio
```

Więcej informacji: [README.md](../README.md) i [DOCUMENTATION.md](../DOCUMENTATION.md).
