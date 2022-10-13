package net.oldscape.patcher;

import java.nio.file.Path;

public record PatcherOptions(Path srcJar, Path outJar) {

}