package com.luminamc.crash;

import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Captures non-zero game exits, writes a timestamped crash report to
 * {@code ~/.luminamc/logs}, and surfaces the captured log to the built-in
 * log viewer.
 */
public final class CrashReporter {

    public record Report(boolean crashed, int exitCode, Path file, String log) {}

    /** Treats any non-zero exit as a crash and persists the captured output. */
    public Report handleExit(Instance inst, int exitCode, List<String> capturedLog) {
        String log = String.join(System.lineSeparator(), capturedLog);
        boolean crashed = exitCode != 0;
        if (!crashed) return new Report(false, exitCode, null, log);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path file = LuminaPaths.logs().resolve("crash-" + inst.id + "-" + stamp + ".log");
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("LuminaMC crash report\n");
            sb.append("Instance: ").append(inst.name).append(" (").append(inst.id).append(")\n");
            sb.append("Minecraft: ").append(inst.mcVersion).append("  Loader: ").append(inst.loader).append('\n');
            sb.append("Exit code: ").append(exitCode).append('\n');
            sb.append("Time: ").append(stamp).append("\n\n");
            sb.append("──── Game output ────\n");
            sb.append(log);
            Files.writeString(file, sb.toString());
        } catch (IOException ignored) {}
        return new Report(true, exitCode, file, log);
    }

    /** Heuristic: pulls the most relevant error lines for a quick summary. */
    public static String summarize(List<String> log) {
        for (String line : log) {
            String l = line.toLowerCase();
            if (l.contains("exception") || l.contains("error") || l.contains("could not")
                    || l.contains("caused by")) {
                return line.trim();
            }
        }
        return log.isEmpty() ? "Game exited unexpectedly." : log.get(log.size() - 1);
    }
}
