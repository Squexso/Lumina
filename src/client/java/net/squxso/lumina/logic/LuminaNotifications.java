package net.squxso.lumina.logic;

import java.util.ArrayList;
import java.util.List;

public class LuminaNotifications {

    public record Notification(String message, long expiresAt) {}

    private static final List<Notification> active = new ArrayList<>();
    private static final long DURATION_MS = 2500;

    public static synchronized void push(String message) {
        active.removeIf(n -> System.currentTimeMillis() > n.expiresAt());
        if (active.size() >= 4) active.remove(0);
        active.add(new Notification(message, System.currentTimeMillis() + DURATION_MS));
    }

    public static synchronized List<Notification> getActiveNotifications() {
        active.removeIf(n -> System.currentTimeMillis() > n.expiresAt());
        return new ArrayList<>(active);
    }
}