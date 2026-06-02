package net.squxso.lumina;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class Lumina {

    public Lumina() {
        Constants.LOG.info("LuminaMC bootstrapping on Forge");
        CommonClass.init();
        // Client-only hooks (keybind, HUD, tick); registered on the new eventbus-7 BUS fields.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LuminaForgeClient.init();
        }
    }
}