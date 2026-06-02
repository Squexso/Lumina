package net.squxso.lumina.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

public class LuminaLogic {

    public static boolean enabled           = false;
    public static boolean autoWitherBlade   = false;
    public static boolean autoYetiSword     = false;
    public static boolean autoInkWand       = false;
    public static boolean autoTotem         = false;
    public static boolean autoSell          = false;
    public static boolean showCatchMessages = true;
    public static boolean softLook          = true;
    public static boolean humanPauses       = true;
    public static boolean showHud           = true;
    public static boolean fpsBoost          = false;
    public static boolean zoomEnabled       = true;

    public static int  delayMin    = 200;
    public static int  delayMax    = 400;
    public static int  delayMean   = 300;
    public static int  delayStdDev = 50;

    public static int  catchCount      = 0;
    private static long activeTime     = 0;
    private static long enabledSince   = -1;

    private static final Random RNG = new Random();

    private static long lastCastTime     = 0;
    private static long nextRecastDelay  = 600;
    private static long lastTotemTime    = 0;
    private static long lastSoftLookTime = 0;
    private static long lastPauseCheck   = 0;
    private static long pauseUntil       = 0;
    private static long nextPauseIn      = randomPauseInterval();

    private static long catchActionTime  = -1;
    private static long itemSwapTime     = -1;
    private static int  preCatchSlot     = -1;

    private static Vec3d lastBobberPos   = null;
    private static long  lastBobberMove  = 0;
    private static final long STUCK_TIMEOUT = 60_000;

    /**
     * Tracks the highest Y the bobber reached while floating (updated after a
     * stabilisation period post-cast). A sudden dip below this level signals
     * a bite even when the server doesn't produce a non-zero velocity packet.
     */
    private static double bobberHighY = Double.NEGATIVE_INFINITY;

    private static Vec3d lastPlayerPos       = null;
    private static final double TP_THRESHOLD  = 8.0;
    private static boolean sellPending       = false;
    private static float lastPlayerYaw        = Float.MAX_VALUE;

    public static final List<String> RARE_KEYWORDS = List.of(
            "Great White Shark", "Phantom Fisher", "Carrot King",
            "Sea Emperor", "Plhlegblast", "Skeleton Fish"
    );

    // Sea-creature names used in SkyBlock catch-notification messages.
    // When any of these appear in chat while a weapon toggle is on, the client
    // equips the weapon, looks down at the ground and right-clicks (cast ability).
    private static final List<String> SEA_CREATURES = List.of(
            "sea walker", "night squid", "sea guardian", "sea witch", "sea archer",
            "monster of the deep", "catfish", "carrot king", "sea leech",
            "guardian defender", "deep sea protector", "water hydra", "sea emperor",
            "skeleton fisher", "frozen steve", "grinch", "yeti", "nutcracker",
            "werewolf", "phantom fisher", "grim reaper", "great white shark",
            "blue shark", "tiger shark", "nurse shark", "plhlegblast", "lord jawbus",
            "flaming worm", "lava blaze", "lava pigman", "magma slug", "moogma",
            "pyroclastic worm", "fire eel", "thunder", "taurus", "scarecrow",
            "reindrake", "squid", "sea creature"
    );

    // ── Weapon-ability state machine ──────────────────────────────────────
    private static long abilityCastTime         = -1; // when to right-click the ability
    private static long abilityRestoreTime       = -1; // when to swap back to the rod
    private static int  abilityReturnSlot        = -1;
    private static float savedPitch              = 0f;
    /**
     * If the weapon was in the inventory (not hotbar) we temporarily swapped it
     * into hotbar slot 7.  This stores the original inventory slot (9-35) so we
     * can swap it back after the ability fires.  -1 means the weapon was already
     * in the hotbar and no swap is needed.
     */
    private static int  abilityInventorySlotUsed = -1;
    /**
     * True when the weapon ability requires looking straight down (melee weapons
     * like Yeti Sword / Hyperion). False for ranged/AOE weapons like Ink Wand
     * whose Absorb ability fires regardless of pitch.
     */
    private static boolean abilityNeedsLookDown  = false;

    /** Cooldown — prevents re-triggering when multiple sea creatures spawn rapidly. */
    private static long lastAbilityTime          = 0L;
    private static final long ABILITY_COOLDOWN   = 15_000L; // 15 s

    /** Status text shown on-screen during scan / countdown. Set from this class, read by LuminaHud. */
    public  static String abilityStatusText      = "";
    public  static long   abilityStatusUntil     = 0L;

    // ── Session time (only counts when enabled) ───────────────────────────
    public static long getActiveSeconds() {
        long total = activeTime;
        if (enabled && enabledSince >= 0)
            total += System.currentTimeMillis() - enabledSince;
        return total / 1000;
    }

    public static void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (lastPlayerPos != null && pos.distanceTo(lastPlayerPos) > TP_THRESHOLD)
            emergencyStop(mc, "Teleport detected — Emergency Stop!");
        lastPlayerPos = pos;

        // Stop if the server suddenly spins the player >150° in one tick (anti-cheat kick).
        float currentYaw = mc.player.getYaw();
        if (lastPlayerYaw != Float.MAX_VALUE) {
            float delta = Math.abs(currentYaw - lastPlayerYaw) % 360f;
            if (delta > 180f) delta = 360f - delta;
            if (delta > 150f) emergencyStop(mc, "Sudden rotation detected — Emergency Stop!");
        }
        lastPlayerYaw = currentYaw;

        // ── Weapon-ability state machine (runs regardless of enabled state) ──
        long now = System.currentTimeMillis();
        if (abilityCastTime > 0 && now >= abilityCastTime) {
            // Only pitch-down for melee weapons (Yeti Sword, Hyperion, etc.).
            // Ink Wand Absorb is AOE — pitch changes would cause the right-click
            // to target the ground instead of activating the ability.
            if (abilityNeedsLookDown) mc.player.setPitch(85f);
            if (mc.interactionManager != null)
                mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
            abilityCastTime  = -1;
            abilityRestoreTime = now + gaussianDelay(300, 60);
        }
        if (abilityRestoreTime > 0 && now >= abilityRestoreTime) {
            // If weapon came from inventory (slot 9-35), swap it back there first.
            if (abilityInventorySlotUsed >= 0 && mc.interactionManager != null) {
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        abilityInventorySlotUsed, 7, SlotActionType.SWAP, mc.player);
                abilityInventorySlotUsed = -1;
            }
            if (abilityReturnSlot >= 0) { sendSlotPacket(mc, abilityReturnSlot); abilityReturnSlot = -1; }
            if (abilityNeedsLookDown) mc.player.setPitch(savedPitch);
            abilityRestoreTime = -1;
        }

        if (catchActionTime > 0 && System.currentTimeMillis() >= catchActionTime) {
            executeCatch(mc);
            catchActionTime = -1;
            if (preCatchSlot >= 0) itemSwapTime = System.currentTimeMillis() + gaussianDelay(300, 60);
        }

        if (itemSwapTime > 0 && System.currentTimeMillis() >= itemSwapTime) {
            if (preCatchSlot >= 0) { sendSlotPacket(mc, preCatchSlot); preCatchSlot = -1; }
            itemSwapTime = -1;
        }

        if (!enabled) return;

        if (humanPauses) {
            now = System.currentTimeMillis();
            if (now - lastPauseCheck > nextPauseIn) {
                lastPauseCheck = now;
                nextPauseIn    = randomPauseInterval();
                long dur = 15_000 + (long)(RNG.nextDouble() * 30_000);
                pauseUntil = now + dur;
                sendChat(mc, "Auto-Pause: resting for " + (dur / 1000) + "s...");
            }
            if (System.currentTimeMillis() < pauseUntil) return;
        }

        if (isInventoryFull(mc)) {
            if (autoSell && !sellPending) triggerAutoSell(mc);
            else if (!autoSell) { sendChat(mc, "Inventory full — stopping."); toggle(); return; }
        }

        if (softLook && System.currentTimeMillis() - lastSoftLookTime > 120_000) {
            applySoftLook(mc); lastSoftLookTime = System.currentTimeMillis();
        }

        if (autoTotem && System.currentTimeMillis() - lastTotemTime > 300_000)
            lastTotemTime = System.currentTimeMillis();

        FishingBobberEntity bobber = mc.player.fishHook;
        if (bobber != null) {
            boolean inFluid  = bobber.isTouchingWater() || bobber.isInLava();
            long    elapsed  = System.currentTimeMillis() - lastCastTime;
            boolean waited   = elapsed > 1_000;   // minimum wait before any detection
            boolean stabWait = elapsed > 1_800;   // extra time for bobber to settle (position method)
            Vec3d bPos       = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());

            // Stuck detection.
            if (lastBobberPos == null || bPos.distanceTo(lastBobberPos) > 0.01) {
                lastBobberPos = bPos; lastBobberMove = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastBobberMove > STUCK_TIMEOUT) {
                sendChat(mc, "⚠ Bobber stuck — recasting...");
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                lastCastTime = System.currentTimeMillis();
                lastBobberPos = null;
                bobberHighY   = Double.NEGATIVE_INFINITY;
                return;
            }

            // Track the bobber's high-water mark after it has stabilised.
            // Private servers may not send velocity packets on bite, so we also
            // detect a bite by watching the bobber dip below its resting level.
            if (inFluid && stabWait && bPos.y > bobberHighY) {
                bobberHighY = bPos.y;
            }

            // Bite detection:
            //   • velocity method — works on vanilla servers & most plugins
            //   • position method — works even when the server sends no velocity
            boolean velocityBite = bobber.getVelocity().y < -0.02;
            boolean positionBite = stabWait
                    && bobberHighY > Double.NEGATIVE_INFINITY
                    && bPos.y < bobberHighY - 0.08;

            if (inFluid && waited && (velocityBite || positionBite) && catchActionTime < 0) {
                catchCount++;
                if (showCatchMessages) sendChat(mc, "✔ Catch #" + catchCount + " — pulling!");
                catchActionTime = System.currentTimeMillis() + gaussianDelay(delayMean, delayStdDev);
                lastBobberPos   = null;
                bobberHighY     = Double.NEGATIVE_INFINITY; // reset after each catch
            }
        } else {
            lastBobberPos = null;
            bobberHighY   = Double.NEGATIVE_INFINITY;   // reset when bobber is gone / recasting
            if (System.currentTimeMillis() - lastCastTime > nextRecastDelay) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                lastCastTime = System.currentTimeMillis();
                nextRecastDelay = rollRecastDelay();
            }
        }
    }

    /**
     * Picks the next recast wait as a Gaussian centred in the configured range
     * and clamped to [delayMin, delayMax]. Drawing a fresh value each cast (rather
     * than re-rolling every tick) keeps the timing humanlike instead of firing the
     * instant the minimum elapses.
     */
    private static long rollRecastDelay() {
        long v = (long) (delayMean + RNG.nextGaussian() * delayStdDev);
        return Math.max(delayMin, Math.min(delayMax, v));
    }

    public static long gaussianDelay(int mean, int stdDev) {
        long v = (long)(mean + RNG.nextGaussian() * stdDev);
        return Math.max(mean - 2L * stdDev, Math.min(mean + 2L * stdDev, v));
    }

    private static void executeCatch(MinecraftClient mc) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            lastCastTime = System.currentTimeMillis();
        }
    }

    private static void swapToItem(MinecraftClient mc, String... keywords) {
        if (mc.player == null) return;
        int current = mc.player.getInventory().selectedSlot;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String name = cleanItemName(s);
            for (String kw : keywords) {
                if (name.contains(kw.toLowerCase())) { preCatchSlot = current; sendSlotPacket(mc, i); return; }
            }
        }
    }

    private static String cleanItemName(ItemStack stack) {
        // Strip colour codes only; preserve all words so that multi-word names
        // like "Yeti Sword" are matched correctly even with a reforge prefix
        // (e.g. "Heroic Yeti Sword" still contains "yeti sword").
        return stack.getName().getString().replaceAll("§[0-9a-fk-or]", "").trim().toLowerCase();
    }

    private static void sendSlotPacket(MinecraftClient mc, int slot) {
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.player.getInventory().selectedSlot = slot;
    }

    private static void applySoftLook(MinecraftClient mc) {
        if (mc.player == null) return;
        float dp = (float)((RNG.nextDouble() * 0.4 + 0.1) * (RNG.nextBoolean() ? 1 : -1));
        float dy = (float)((RNG.nextDouble() * 0.4 + 0.1) * (RNG.nextBoolean() ? 1 : -1));
        mc.player.setPitch(mc.player.getPitch() + dp);
        mc.player.setYaw(mc.player.getYaw() + dy);
    }

    private static void emergencyStop(MinecraftClient mc, String reason) {
        if (enabled) { activeTime += System.currentTimeMillis() - enabledSince; enabledSince = -1; }
        enabled = false; catchActionTime = -1; itemSwapTime = -1; preCatchSlot = -1;
        bobberHighY = Double.NEGATIVE_INFINITY;
        sendChat(mc, reason);
    }

    private static boolean isInventoryFull(MinecraftClient mc) {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        return true;
    }

    private static void triggerAutoSell(MinecraftClient mc) {
        sellPending = true;
        mc.player.networkHandler.sendChatCommand("bz");
        sendChat(mc, "Auto-Sell triggered...");
    }

    public static void onChatReceived(MinecraftClient mc, String message) {
        // This method is called from the NETWORK thread (LuminaChatMixin intercepts
        // ClientPlayNetworkHandler.onGameMessage). All MC operations (sendMessage,
        // interactionManager, inventory) must run on the RENDER/MAIN thread.
        // mc.execute() queues the work safely.
        mc.execute(() -> {
            if (mc.player == null) return;
            String lower = message.toLowerCase();

            if (lower.contains(mc.player.getName().getString().toLowerCase())) playAlert(mc, 1.0f);

            for (String kw : RARE_KEYWORDS) {
                if (lower.contains(kw.toLowerCase())) { playAlert(mc, 2.0f); sendChat(mc, "⚡ RARE: " + kw + "!"); break; }
            }

            // Trigger weapon ability when a sea creature is announced in chat.
            if (autoWitherBlade || autoYetiSword || autoInkWand) {
                for (String sc : SEA_CREATURES) {
                    if (lower.contains(sc)) {
                        triggerWeaponAbility(mc);
                        break;
                    }
                }
            }

            TrophyFishTracker.onChatReceived(mc, message);
        });
    }

    private static void triggerWeaponAbility(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();

        // ── Cooldown ─────────────────────────────────────────────────────────
        // Only fire once per 15 s so a burst of sea-creature messages doesn't
        // spam the ability.
        if (now - lastAbilityTime < ABILITY_COOLDOWN) return;

        // ── Weapon selection ─────────────────────────────────────────────────
        String[] keywords;
        String   label;
        boolean  lookDown;
        if (autoWitherBlade) {
            keywords = new String[]{"hyperion", "astraea", "scylla", "valkyrie"};
            label    = "Hyperion"; lookDown = true;
        } else if (autoYetiSword) {
            keywords = new String[]{"yeti sword"};
            label    = "Yeti Sword"; lookDown = true;
        } else {
            keywords = new String[]{"ink wand"};
            label    = "Ink Wand"; lookDown = false; // AOE — no pitch aim needed
        }

        // ── Show "scanning" on HUD immediately ───────────────────────────────
        abilityStatusText  = "§e🔍 " + label + " wird gesucht...";
        abilityStatusUntil = now + 5_000;

        // ── 1. Hotbar scan (slots 0-8) ────────────────────────────────────────
        int    slot      = findHotbarSlot(mc, keywords);
        String foundInfo = null;
        if (slot >= 0) {
            abilityInventorySlotUsed = -1;
            foundInfo = "Hotbar-Slot " + (slot + 1);
        }

        // ── 2. Inventory scan (slots 9-35) if not in hotbar ──────────────────
        if (slot < 0) {
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;
                String name = cleanItemName(s);
                boolean match = false;
                for (String kw : keywords) if (name.contains(kw)) { match = true; break; }
                if (match) {
                    // SWAP screen-slot i with hotbar-slot 7 (8th hotbar slot).
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            i, 7, SlotActionType.SWAP, mc.player);
                    slot      = 7;
                    abilityInventorySlotUsed = i;
                    foundInfo = "Inventar-Slot " + i + " → Hotbar 8";
                    break;
                }
            }
        }

        // ── Not found ─────────────────────────────────────────────────────────
        if (slot < 0) {
            sendChat(mc, "⚠ " + label + " nicht gefunden (weder Hotbar noch Inventar)!");
            abilityStatusText  = "§c✗ " + label + " nicht gefunden!";
            abilityStatusUntil = now + 4_000;
            return;
        }

        // ── Found ─────────────────────────────────────────────────────────────
        long delayMs = lookDown
                ? gaussianDelay(120, 30)   // melee: fire almost immediately
                : 3_000 + gaussianDelay(0, 200); // Ink Wand: wait 3 s first

        sendChat(mc, "✔ " + label + " gefunden: " + foundInfo
                + (lookDown ? " — Ability sofort!" : " — Ability in 3s!"));
        abilityStatusText  = "§a✔ " + label + " [" + foundInfo + "]"
                + (lookDown ? "" : " §e— 3s...");
        abilityStatusUntil = now + delayMs + 1_500;

        lastAbilityTime      = now;
        abilityReturnSlot    = mc.player.getInventory().selectedSlot;
        savedPitch           = mc.player.getPitch();
        abilityNeedsLookDown = lookDown;
        sendSlotPacket(mc, slot);
        abilityCastTime      = now + delayMs;
    }

    private static int findHotbarSlot(MinecraftClient mc, String[] keywords) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String name = cleanItemName(s);
            for (String kw : keywords)
                if (name.contains(kw.toLowerCase())) return i;
        }
        return -1;
    }

    private static void playAlert(MinecraftClient mc, float pitch) {
        if (mc.player != null) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, pitch);
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            enabledSince = System.currentTimeMillis();
            lastPauseCheck = System.currentTimeMillis();
            nextPauseIn    = randomPauseInterval();
            pauseUntil     = 0;
            lastPlayerYaw  = Float.MAX_VALUE; // reset so first tick doesn't false-fire
        } else {
            if (enabledSince >= 0) { activeTime += System.currentTimeMillis() - enabledSince; enabledSince = -1; }
        }
        sendChat(MinecraftClient.getInstance(), enabled ? "▶ Auto Fisher ENABLED" : "■ Auto Fisher DISABLED");
    }

    public static void setDelay(int min, int max) {
        delayMin = min; delayMax = max;
        delayMean = (min + max) / 2; delayStdDev = (max - min) / 4;
    }

    public static int  getDelayMin()     { return delayMin; }
    public static int  getDelayMax()     { return delayMax; }
    public static int  getCatchCount()   { return catchCount; }
    public static void resetCatchCount() { catchCount = 0; }
    public static void logEvent(String msg) { sendChat(MinecraftClient.getInstance(), msg); }

    public static void sendChat(MinecraftClient mc, String msg) {
        if (mc != null && mc.player != null)
            mc.player.sendMessage(Text.literal("§5[Lumina] §d" + msg), false);
    }

    private static long randomPauseInterval() {
        return 20 * 60_000L + (long)(RNG.nextDouble() * 10 * 60_000L);
    }
}