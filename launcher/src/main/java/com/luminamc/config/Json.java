package com.luminamc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Thin Gson helper used for all JSON persistence in the launcher. */
public final class Json {

    private Json() {}

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** Reads {@code path} into an instance of {@code type}, or returns {@code fallback} on any error. */
    public static <T> T read(Path path, Class<T> type, T fallback) {
        try {
            if (!Files.exists(path)) return fallback;
            try (Reader r = Files.newBufferedReader(path)) {
                T value = GSON.fromJson(r, type);
                return value != null ? value : fallback;
            }
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Writes {@code obj} to {@code path} as pretty JSON, creating parent dirs. */
    public static void write(Path path, Object obj) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(obj, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to " + path, e);
        }
    }

    public static String toJson(Object o)           { return GSON.toJson(o); }
    public static <T> T fromJson(String s, Class<T> t) { return GSON.fromJson(s, t); }
}
