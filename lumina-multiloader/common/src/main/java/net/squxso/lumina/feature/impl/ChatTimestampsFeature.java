package net.squxso.lumina.feature.impl;

import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/** Prefixes every chat message with a [HH:MM] timestamp (applied by LuminaChatMixin). */
public final class ChatTimestampsFeature extends Feature {

    private static ChatTimestampsFeature instance;

    public ChatTimestampsFeature() {
        super("chat_timestamps", "Chat Timestamps", "Show [HH:MM] before each chat message.", FeatureCategory.CHAT, false);
        instance = this;
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }
}
