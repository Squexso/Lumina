package com.luminamc.game;

import com.luminamc.auth.Account;
import com.luminamc.instance.Instance;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Substitutes Mojang/loader {@code ${...}} placeholders in JVM and game argument templates. */
public final class ArgumentBuilder {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, String> ctx = new HashMap<>();

    public ArgumentBuilder(Instance inst, ResolvedVersion rv, Account account, Path gameDir) {
        String cp = String.join(File.pathSeparator,
                rv.classpath.stream().map(Path::toString).toList());

        put("auth_player_name", account != null ? account.username : "Player");
        put("version_name", rv.id);
        put("game_directory", gameDir.toString());
        put("assets_root", rv.assetsRoot != null ? rv.assetsRoot.toString() : "");
        put("game_assets", rv.assetsRoot != null ? rv.assetsRoot.resolve("virtual").resolve("legacy").toString() : "");
        put("assets_index_name", rv.assetIndexId != null ? rv.assetIndexId : "");
        put("auth_uuid", account != null ? account.mcUuid : "0");
        put("auth_access_token", account != null ? account.mcAccessToken : "0");
        put("auth_session", account != null ? "token:" + account.mcAccessToken + ":" + account.mcUuid : "0");
        put("clientid", "");
        put("auth_xuid", "");
        put("user_type", "msa");
        put("user_properties", "{}");
        put("version_type", "release");
        put("natives_directory", rv.nativesDir != null ? rv.nativesDir.toString() : "");
        put("launcher_name", "LuminaMC");
        put("launcher_version", "0.1.0");
        put("classpath", cp);
        put("classpath_separator", File.pathSeparator);
        put("library_directory", gameDir.resolve("libraries").toString());
    }

    private void put(String k, String v) {
        ctx.put(k, v != null ? v : "");
    }

    /** Resolves every placeholder in a single argument token. */
    public String resolve(String template) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(ctx.getOrDefault(key, m.group(0))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public List<String> resolveAll(List<String> templates) {
        List<String> out = new ArrayList<>(templates.size());
        for (String t : templates) out.add(resolve(t));
        return out;
    }
}
