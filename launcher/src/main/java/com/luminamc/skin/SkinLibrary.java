package com.luminamc.skin;

import com.luminamc.config.Json;
import com.luminamc.config.LuminaPaths;

import java.util.ArrayList;
import java.util.List;

/**
 * A saved collection of skins (persisted to {@code ~/.luminamc/skins.json}) so
 * the user can keep a library and re-apply any of them anytime.
 */
public final class SkinLibrary {

    public static final class Entry {
        public String name;
        public String uuid;
        public String url;
        public boolean slim;
        public Entry() {}
        public Entry(String name, String uuid, String url, boolean slim) {
            this.name = name; this.uuid = uuid; this.url = url; this.slim = slim;
        }
    }

    public List<Entry> skins = new ArrayList<>();

    public static SkinLibrary load() {
        return Json.read(LuminaPaths.root().resolve("skins.json"), SkinLibrary.class, new SkinLibrary());
    }

    public void save() {
        Json.write(LuminaPaths.root().resolve("skins.json"), this);
    }

    /** Adds (or moves to front) a skin entry, de-duplicated by UUID. */
    public void add(Entry e) {
        skins.removeIf(s -> s.uuid != null && s.uuid.equals(e.uuid));
        skins.add(0, e);
        save();
    }

    public void remove(Entry e) {
        skins.remove(e);
        save();
    }
}
