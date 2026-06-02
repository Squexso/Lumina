package net.squxso.lumina;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class Lumina {

    public Lumina(IEventBus modBus, Dist dist) {
        Constants.LOG.info("LuminaMC bootstrapping on NeoForge");
        CommonClass.init();
        // Client-only hooks (keybind, HUD, tick, lifecycle).
        if (dist.isClient()) {
            LuminaNeoForgeClient.init(modBus);
        }
    }
}