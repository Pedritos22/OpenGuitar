package com.openguitar.beatmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class TestAudioSupport {

    private TestAudioSupport() {
    }

    static Path encodeMp3(Path wav, Path mp3) throws IOException, InterruptedException {
        Files.deleteIfExists(mp3);
        int exit = runCommand(
                List.of(
                        "ffmpeg",
                        "-y",
                        "-hide_banner",
                        "-loglevel", "error",
                        "-i", wav.toAbsolutePath().toString(),
                        "-codec:a", "libmp3lame",
                        "-q:a", "4",
                        mp3.toAbsolutePath().toString()
                ),
                "ffmpeg"
        );

        if (exit != 0 || !Files.exists(mp3) || Files.size(mp3) == 0) {
            exit = runCommand(
                    List.of(
                            "lame",
                            "--silent",
                            "-V", "4",
                            wav.toAbsolutePath().toString(),
                            mp3.toAbsolutePath().toString()
                    ),
                    "lame"
            );
        }

        if (exit != 0 || !Files.exists(mp3) || Files.size(mp3) == 0) {
            throw new IOException("Nie udało się utworzyć MP3 z " + wav + " -> " + mp3);
        }
        return mp3;
    }

    private static int runCommand(List<String> command, String label)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        if (exit != 0 && !output.isBlank()) {
            System.err.printf(Locale.ROOT, "[%s] %s%n", label, output);
        }
        return exit;
    }
}
