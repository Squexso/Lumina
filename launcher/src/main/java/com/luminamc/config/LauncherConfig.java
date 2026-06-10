package com.luminamc.config;

/**
 * Global launcher settings persisted to {@code ~/.luminamc/launcher.json}.
 * Per-instance settings live on {@link com.luminamc.instance.Instance} instead.
 */
public final class LauncherConfig {

    /** Global Java override; when null/blank the per-instance or auto-detected JDK is used. */
    public String defaultJavaPath = null;

    public int defaultRamMinMb = 1024;
    public int defaultRamMaxMb = 4096;

    /** Number of concurrent download threads. */
    public int downloadThreads = 8;

    /** Accent color (hex) for the UI; spec default is violet. */
    public String accentColor = "#7C3AED";

    /** Background preset id (see {@code Theme.BACKGROUNDS}); default is the violet nebula. */
    public String backgroundTheme = "nebula";

    /** Whether the animated star field is drawn over the background. */
    public boolean showStars = true;

    public boolean closeLauncherOnLaunch = false;
    public boolean keepLauncherOpenLogs  = true;

    /** Default in-game overlay key applied to newly-created instances. */
    public String defaultOverlayKey = "RIGHT_SHIFT";

    public String  lastInstanceId  = null;
    public String  activeAccountId = null;

    /** Set true once the first-launch wizard finishes. */
    public boolean setupComplete = false;

    /** LuminaMC release feed for the self-updater (served by your update server). */
    public String updateFeedUrl = DEFAULT_FEED_URL;

    /** Check for updates automatically on launch. */
    public boolean autoUpdate = true;

    /** GitHub Releases feed — always resolves to the newest published version. */
    public static final String DEFAULT_FEED_URL =
            "https://github.com/Squexso/Lumina/releases/latest/download/latest.json";

    /**
     * Microsoft Azure AD Application (client) ID used for sign-in.
     * Null = not configured yet. Once set, the device-code flow works
     * without any further setup.
     */
    public String msClientId = null;

    /** Total time (ms) Minecraft has been played through this launcher. */
    public long totalPlayMillis = 0L;

    /** True once a desktop shortcut has been created on first run (packaged Windows app). */
    public boolean desktopShortcutCreated = false;

    // ── Discord Rich Presence ──────────────────────────────────────────────
    /** Show a Discord status (logo + text) while the launcher is open. */
    public boolean discordRichPresence = true;
    /** Discord application id from the Developer Portal. Empty = presence disabled. */
    public String discordClientId = "";
    /** First line of the Discord status. */
    public String discordDetails = "Developing the launcher";
    /** Second line of the Discord status. */
    public String discordState = "LuminaMC Launcher";
    /** Invite shown as a Rich-Presence button (visible to others). Empty = no button. */
    public String discordInviteUrl = "https://discord.gg/hZurxFCgJt";

    // ── Lumina Tokens economy ──────────────────────────────────────────────
    /** Spendable Lumina Token balance. Earned by playtime; topped up later via PayPal. */
    public long luminaTokens = 0L;
    /** Playtime (ms) already converted into earned tokens; advances as you keep playing. */
    public long tokensAccruedMillis = 0L;
    /** True once the one-time welcome bonus has been granted. */
    public boolean luminaWelcomeGiven = false;
    /** Cosmetic ids the user has purchased from the shop. */
    public java.util.List<String> ownedCosmetics = new java.util.ArrayList<>();
    /** Currently equipped cape id ({@code null} = none). */
    public String equippedCape = null;
    /** Currently equipped accessory id ({@code null} = none). */
    public String equippedAccessory = null;
    /** Creator codes already redeemed (only enforced for non-repeatable codes). */
    public java.util.List<String> redeemedCodes = new java.util.ArrayList<>();
    /** History of every code redemption (newest appended last), for the shop log. */
    public java.util.List<com.luminamc.shop.RedemptionEntry> redemptionLog = new java.util.ArrayList<>();

    // ── Daily login streak ─────────────────────────────────────────────────
    /** Current consecutive daily-claim count (0 = never claimed). */
    public int streakDay = 0;
    /** Date of the last daily claim in Europe/Berlin (ISO {@code yyyy-MM-dd}); null = none. */
    public String lastClaimDate = null;

    // ── Persistence ────────────────────────────────────────────────────────

    public static LauncherConfig load() {
        LauncherConfig c = Json.read(LuminaPaths.config(), LauncherConfig.class, new LauncherConfig());
        // Migrate old/placeholder/local feed URLs to the working GitHub default.
        if (c.updateFeedUrl == null || c.updateFeedUrl.isBlank()
                || c.updateFeedUrl.contains("luminamc.example")
                || c.updateFeedUrl.contains("localhost:8770")) {
            c.updateFeedUrl = DEFAULT_FEED_URL;
        }
        // Fresh install: size the default max heap to this machine (≈ half its RAM).
        if (!c.setupComplete && c.defaultRamMaxMb == 4096) {
            c.defaultRamMaxMb = com.luminamc.javart.SystemSpecs.recommendedMaxRamMb();
        }
        return c;
    }

    public void save() {
        Json.write(LuminaPaths.config(), this);
    }
}
