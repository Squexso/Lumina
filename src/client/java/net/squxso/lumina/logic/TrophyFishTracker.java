package net.squxso.lumina.logic;

import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class TrophyFishTracker {

    public static int bronze  = 0;
    public static int silver  = 0;
    public static int gold    = 0;
    public static int diamond = 0;

    // SkyBlock trophy fish names (lowercase substrings that appear in catch messages).
    private static final List<String> TROPHY_NAMES = List.of(
            "steaming-hot flounder", "obese squid", "gusher", "blobfish",
            "phantom fish", "mana ray", "ridgeback moray", "riftstalker",
            "flyfish", "giant swordfish", "lava horse", "molten steed",
            "moustache king", "slug fish", "karate fish", "sulky mackerel",
            "steaming flounder", "tusk fish", "bone fish", "frozen ghost"
    );

    // Tier keywords that appear in SkyBlock trophy fish messages.
    // Messages look like: "You caught a §6Gold §fGusher!"
    public static void onChatReceived(MinecraftClient mc, String message) {
        String lower = message.toLowerCase();

        boolean isTrophy = TROPHY_NAMES.stream().anyMatch(lower::contains);
        if (!isTrophy) return;

        String tier;
        String color;
        if (lower.contains("diamond")) {
            diamond++; tier = "Diamond"; color = "§b";
        } else if (lower.contains("gold")) {
            gold++;    tier = "Gold";    color = "§6";
        } else if (lower.contains("silver")) {
            silver++;  tier = "Silver";  color = "§7";
        } else {
            bronze++;  tier = "Bronze";  color = "§8";
        }

        String fish = TROPHY_NAMES.stream()
                .filter(lower::contains)
                .findFirst()
                .map(n -> n.substring(0, 1).toUpperCase() + n.substring(1))
                .orElse("Trophy Fish");

        LuminaLogic.sendChat(mc, color + tier + " §dtrophy: §f" + fish
                + " §8[◆" + diamond + " §6●" + gold + " §7●" + silver + " §8●" + bronze + "§8]");
        LuminaNotifications.push(color + tier + " " + fish + "!");
    }

    public static void reset() {
        bronze = silver = gold = diamond = 0;
    }
}
