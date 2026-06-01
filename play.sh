#!/usr/bin/env bash
#
# OpenGuitar - jednolinijkowiec do uruchamiania gry.
#
# Użycie:
#   ./play.sh                          # menu (lista utworów z songs/)
#   ./play.sh songs/utwor.mp3          # bezpośrednio do gry (jeśli brak JSON, wygeneruje)
#   ./play.sh songs/utwor.json         # bezpośrednio do gry (beatmapa już istnieje)
#   ./play.sh --list                   # CLI listing folderu songs/
#   ./play.sh songs/utwor.mp3 --regen  # wymuś regenerację beatmapy
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SONGS_DIR="songs"

print_usage() {
    sed -n '3,11p' "$0" | sed 's/^# \{0,1\}//'
}

list_songs() {
    if [ ! -d "$SONGS_DIR" ]; then
        echo "Brak folderu $SONGS_DIR/"
        exit 1
    fi
    echo "Audio w $SONGS_DIR/ (z gotowymi beatmapami oznaczone '*'):"
    shopt -s nullglob nocaseglob
    local files=( "$SONGS_DIR"/*.mp3 "$SONGS_DIR"/*.wav "$SONGS_DIR"/*.aiff "$SONGS_DIR"/*.flac )
    shopt -u nullglob nocaseglob

    if [ ${#files[@]} -eq 0 ]; then
        echo "  (pusto - wrzuć .mp3 lub .wav do $SONGS_DIR/)"
        return
    fi
    for f in "${files[@]}"; do
        local base="${f%.*}"
        if [ -f "$base.json" ]; then
            echo "  * $f"
        else
            echo "    $f"
        fi
    done
}

# ----------------------------- main -----------------------------

if [ $# -eq 0 ]; then
    # Domyślnie - otwieramy menu (skanuje songs/, użytkownik klika w UI).
    exec mvn -q -B javafx:run \
        -Djava.util.logging.config.file="$SCRIPT_DIR/src/main/resources/logging.properties"
fi

case "$1" in
    -h|--help)
        print_usage
        exit 0
        ;;
    --list)
        list_songs
        exit 0
        ;;
esac

INPUT="$1"
shift || true

REGEN=0
for arg in "$@"; do
    case "$arg" in
        --regen) REGEN=1 ;;
        *) echo "Nieznany argument: $arg" >&2; exit 1 ;;
    esac
done

if [ ! -f "$INPUT" ]; then
    echo "Plik nie istnieje: $INPUT" >&2
    echo "Spróbuj: ./play.sh --list  albo  ./play.sh  (otworzy menu)" >&2
    exit 1
fi

# Jeśli --regen i to plik audio - usuwamy istniejący JSON, GameApp wygeneruje od nowa.
case "$INPUT" in
    *.mp3|*.wav|*.aiff|*.flac|*.MP3|*.WAV|*.AIFF|*.FLAC)
        if [ "$REGEN" -eq 1 ]; then
            BASE="${INPUT%.*}"
            JSON="$BASE.json"
            if [ -f "$JSON" ]; then
                echo ">> --regen: usuwam $JSON aby wymusić regenerację"
                rm -f "$JSON"
            fi
        fi
        ;;
esac

exec mvn -q -B javafx:run \
    -Djava.util.logging.config.file="$SCRIPT_DIR/src/main/resources/logging.properties" \
    -Djavafx.args="$INPUT"
