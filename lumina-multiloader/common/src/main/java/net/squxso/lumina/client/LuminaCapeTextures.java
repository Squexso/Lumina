package net.squxso.lumina.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the cape texture to render: the launcher recolours the equipped cape and
 * writes it to {@code config/lumina/cape.png} in the instance, so any of the shop's
 * capes shows in its real colours in-game. If that file is missing (e.g. launched
 * without the launcher) we fall back to the bundled violet Lumina cape.
 *
 * <p>The dynamic texture is registered once, lazily, on the render thread (the cape
 * file is written before launch, so it's fixed for the session).
 */
public final class LuminaCapeTextures {

    private LuminaCapeTextures() {}

    private static final Identifier BUNDLED =
            Identifier.fromNamespaceAndPath("lumina", "textures/entity/lumina_cape.png");
    private static final Identifier ACTIVE =
            Identifier.fromNamespaceAndPath("lumina", "textures/entity/cape_active");

    private static boolean resolved;
    private static Identifier current = BUNDLED;

    /** The cape texture id to use (the equipped recolour if present, else the bundled cape). */
    public static Identifier get() {
        if (resolved) return current;
        resolved = true;
        try {
            Path png = Minecraft.getInstance().gameDirectory.toPath().resolve("config/lumina/cape.png");
            if (Files.isRegularFile(png)) {
                NativeImage img;
                try (InputStream in = Files.newInputStream(png)) {
                    img = NativeImage.read(in);
                }
                Minecraft.getInstance().getTextureManager()
                        .register(ACTIVE, new DynamicTexture(() -> "lumina_cape_active", img));
                current = ACTIVE;
            }
        } catch (Exception ignored) {
            current = BUNDLED;   // any failure → bundled violet cape
        }
        return current;
    }
}
