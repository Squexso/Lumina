package com.luminamc.auth;

import com.luminamc.config.Json;
import com.luminamc.config.LuminaPaths;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists signed-in accounts and which one is active to
 * {@code ~/.luminamc/accounts.json}.
 */
public final class AuthStore {

    public List<Account> accounts = new ArrayList<>();
    public String activeId;

    public static AuthStore load() {
        return Json.read(LuminaPaths.accounts(), AuthStore.class, new AuthStore());
    }

    public void save() {
        Json.write(LuminaPaths.accounts(), this);
    }

    public Account active() {
        if (activeId == null) return accounts.isEmpty() ? null : accounts.get(0);
        return accounts.stream().filter(a -> a.id.equals(activeId)).findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.get(0));
    }

    public void addOrReplace(Account account) {
        accounts.removeIf(a -> a.mcUuid != null && a.mcUuid.equals(account.mcUuid));
        accounts.add(account);
        activeId = account.id;
        save();
    }

    /** Switches the active account. */
    public void setActive(Account account) {
        if (account != null) {
            activeId = account.id;
            save();
        }
    }

    public void remove(Account account) {
        accounts.remove(account);
        if (account.id.equals(activeId)) {
            activeId = accounts.isEmpty() ? null : accounts.get(0).id;
        }
        save();
    }
}
