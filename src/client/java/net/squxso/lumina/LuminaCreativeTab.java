package net.squxso.lumina;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.LightBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Adds a Lumina creative-inventory tab gathering the true "operator / command-only"
 * items — the handful of things you cannot pull from any vanilla creative tab and
 * normally have to {@code /give} yourself: command blocks, the barrier, every
 * light level, structure tools, the debug stick and the knowledge book.
 *
 * <p>Ordinary blocks that already appear in the vanilla creative menu (spawner,
 * bedrock, end portal frame, …) are intentionally excluded — they'd be redundant.
 */
public final class LuminaCreativeTab {

    private LuminaCreativeTab() {}

    public static final RegistryKey<ItemGroup> KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of("lumina", "operator_items"));

    public static void register() {
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.COMMAND_BLOCK))
                .displayName(Text.literal("✦ Lumina — Op Items"))
                .entries((displayContext, entries) -> {
                    // ── command blocks ──
                    entries.add(Items.COMMAND_BLOCK);
                    entries.add(Items.CHAIN_COMMAND_BLOCK);
                    entries.add(Items.REPEATING_COMMAND_BLOCK);
                    entries.add(Items.COMMAND_BLOCK_MINECART);

                    // ── build / structure tools ──
                    entries.add(Items.STRUCTURE_BLOCK);
                    entries.add(Items.STRUCTURE_VOID);
                    entries.add(Items.JIGSAW);
                    entries.add(Items.DEBUG_STICK);
                    entries.add(Items.KNOWLEDGE_BOOK);
                    entries.add(Items.BARRIER);

                    // ── every Light level (0–15) ──
                    for (int level = 0; level <= 15; level++) {
                        ItemStack light = new ItemStack(Items.LIGHT);
                        light.set(DataComponentTypes.BLOCK_STATE,
                                BlockStateComponent.DEFAULT.with(LightBlock.LEVEL_15, level));
                        entries.add(light);
                    }
                })
                .build();

        Registry.register(Registries.ITEM_GROUP, KEY, group);
    }
}
