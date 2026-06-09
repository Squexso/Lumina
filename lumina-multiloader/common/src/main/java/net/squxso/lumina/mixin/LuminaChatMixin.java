package net.squxso.lumina.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.squxso.lumina.feature.impl.ChatTimestampsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Prefixes incoming chat messages with a [HH:MM] timestamp when the feature is on. */
@Mixin(ChatComponent.class)
public class LuminaChatMixin {

    private static final DateTimeFormatter LUMINA$FMT = DateTimeFormatter.ofPattern("HH:mm");

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private Component lumina$timestamp(Component msg) {
        if (!ChatTimestampsFeature.active()) return msg;
        return Component.literal("§7[" + LocalTime.now().format(LUMINA$FMT) + "]§r ").append(msg);
    }
}
