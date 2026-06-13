package com.openguitar.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPathsTest {

    @Test
    void packagedMacBuildUsesApplicationSupport() {
        Path home = Path.of("/Users/tester");

        Path result = AppPaths.resolveDataDirectory(true, "Mac OS X", home, Path.of("/readonly/app"));

        assertEquals(home.resolve("Library/Application Support/OpenGuitar"), result);
    }

    @Test
    void developmentBuildKeepsWorkingDirectoryLayout() {
        Path workingDir = Path.of("/project/OpenGuitar");

        Path result = AppPaths.resolveDataDirectory(false, "Mac OS X", Path.of("/Users/tester"), workingDir);

        assertEquals(workingDir, result);
    }
}
