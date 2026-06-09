package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

import java.util.function.Function;

/** Forces a vanilla boolean option to a value while enabled; restores it when disabled. */
public final class BoolOptionFeature extends Feature {

    private final Function<Options, OptionInstance<Boolean>> opt;
    private final boolean value;
    private Boolean saved;

    public BoolOptionFeature(String id, String name, String desc, FeatureCategory cat,
                             Function<Options, OptionInstance<Boolean>> opt, boolean value) {
        super(id, name, desc, cat, false);
        this.opt = opt; this.value = value;
    }

    @Override public void onClientTick(Minecraft mc) {
        if (mc.options == null) return;
        OptionInstance<Boolean> o = opt.apply(mc.options);
        if (saved == null) saved = o.get();
        o.set(value);
    }

    @Override protected void onDisabled() {
        Minecraft mc = Minecraft.getInstance();
        if (saved != null && mc.options != null) opt.apply(mc.options).set(saved);
        saved = null;
    }
}
