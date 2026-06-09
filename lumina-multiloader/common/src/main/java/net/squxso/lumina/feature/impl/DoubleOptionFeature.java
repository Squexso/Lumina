package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

import java.util.function.Function;

/** Forces a vanilla double option to a value while enabled; restores it when disabled. */
public final class DoubleOptionFeature extends Feature {

    private final Function<Options, OptionInstance<Double>> opt;
    private final double value;
    private Double saved;

    public DoubleOptionFeature(String id, String name, String desc, FeatureCategory cat,
                               Function<Options, OptionInstance<Double>> opt, double value) {
        super(id, name, desc, cat, false);
        this.opt = opt; this.value = value;
    }

    @Override public void onClientTick(Minecraft mc) {
        if (mc.options == null) return;
        OptionInstance<Double> o = opt.apply(mc.options);
        if (saved == null) saved = o.get();
        o.set(value);
    }

    @Override protected void onDisabled() {
        Minecraft mc = Minecraft.getInstance();
        if (saved != null && mc.options != null) opt.apply(mc.options).set(saved);
        saved = null;
    }
}
