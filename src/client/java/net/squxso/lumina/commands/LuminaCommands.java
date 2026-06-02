package net.squxso.lumina.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.squxso.lumina.gui.LuminaScreen;
import net.squxso.lumina.logic.LuminaAccess;
import net.squxso.lumina.logic.LuminaLogic;
import net.squxso.lumina.logic.TrophyFishTracker;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class LuminaCommands {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        var lumina = literal("lumina")
                .executes(LuminaCommands::status)
                .then(literal("on")     .executes(ctx -> { enable();             return 1; }))
                .then(literal("off")    .executes(ctx -> { disable();            return 1; }))
                .then(literal("toggle") .executes(ctx -> { LuminaLogic.toggle(); return 1; }))
                .then(literal("gui")    .executes(LuminaCommands::openGui))
                .then(literal("status") .executes(LuminaCommands::status))
                .then(literal("trophies").executes(LuminaCommands::trophies))
                .then(literal("access")
                        .then(literal("reload").executes(ctx -> {
                            LuminaAccess.reload();
                            LuminaLogic.logEvent("Access list reloaded");
                            return 1;
                        })))
                .then(literal("reset")  .executes(ctx -> {
                    LuminaLogic.resetCatchCount();
                    LuminaLogic.logEvent("Catch counter reset");
                    return 1;
                }))
                .then(literal("delay")
                        .then(argument("min", IntegerArgumentType.integer(50))
                                .then(argument("max", IntegerArgumentType.integer(50))
                                        .executes(ctx -> {
                                            int min = IntegerArgumentType.getInteger(ctx, "min");
                                            int max = IntegerArgumentType.getInteger(ctx, "max");
                                            if (max <= min) { LuminaLogic.logEvent("max must be > min"); return 0; }
                                            LuminaLogic.setDelay(min, max);
                                            LuminaLogic.logEvent("Delay: " + min + "-" + max + "ms");
                                            return 1;
                                        }))));

        dispatcher.register(lumina);

        // /lu short alias — kept for convenience.
        dispatcher.register(literal("lu")
                .executes(LuminaCommands::status)
                .then(literal("on")    .executes(ctx -> { enable();             return 1; }))
                .then(literal("off")   .executes(ctx -> { disable();            return 1; }))
                .then(literal("gui")   .executes(LuminaCommands::openGui))
                .then(literal("toggle").executes(ctx -> { LuminaLogic.toggle(); return 1; })));
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private static void enable()  { if (!LuminaLogic.enabled) LuminaLogic.toggle(); }
    private static void disable() { if (LuminaLogic.enabled)  LuminaLogic.toggle(); }

    private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient.getInstance().send(() ->
                MinecraftClient.getInstance().setScreen(new LuminaScreen()));
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LuminaLogic.sendChat(mc, "Status:");
        LuminaLogic.sendChat(mc, "  Auto Fisher:  " + flag(LuminaLogic.enabled));
        LuminaLogic.sendChat(mc, "  Wither Blade: " + flag(LuminaLogic.autoWitherBlade));
        LuminaLogic.sendChat(mc, "  Yeti Sword:   " + flag(LuminaLogic.autoYetiSword));
        LuminaLogic.sendChat(mc, "  Ink Wand:     " + flag(LuminaLogic.autoInkWand));
        LuminaLogic.sendChat(mc, "  Delay: §d" + LuminaLogic.getDelayMin() + "§7-§d" + LuminaLogic.getDelayMax() + "ms");
        LuminaLogic.sendChat(mc, "  Catches: §d" + LuminaLogic.getCatchCount());
        return 1;
    }

    private static int trophies(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LuminaLogic.sendChat(mc, "§6Trophy Fish:");
        LuminaLogic.sendChat(mc, "  §c◆ Diamond: §d" + TrophyFishTracker.diamond);
        LuminaLogic.sendChat(mc, "  §6◈ Gold:    §d" + TrophyFishTracker.gold);
        LuminaLogic.sendChat(mc, "  §7◈ Silver:  §d" + TrophyFishTracker.silver);
        LuminaLogic.sendChat(mc, "  §8◈ Bronze:  §d" + TrophyFishTracker.bronze);
        return 1;
    }

    private static String flag(boolean b) { return b ? "§dON" : "§8OFF"; }
}
