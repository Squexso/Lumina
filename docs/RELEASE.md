# Releasing LuminaMC — GitHub + CI + Auto-Update + Code Signing

This is the one-time setup that turns the **already-built** auto-update system on, and
(optionally) removes the Windows "unknown publisher" warning by code-signing the `.exe`.

---

## 1. Put the project on GitHub (one time)

```bash
cd C:\Users\lochd\IdeaProjects\Lumina
git init
git add .
git commit -m "Initial commit"
gh repo create LuminaMC --private --source=. --push      # or create it on github.com and:
# git remote add origin https://github.com/<you>/LuminaMC.git
# git branch -M main && git push -u origin main
```

The CI workflow is already in `.github/workflows/release.yml` — nothing else to add.

---

## 2. Point the auto-updater at GitHub

In `~/.luminamc/launcher.json` (and the default in `LauncherConfig.java`) set the feed to
your repo's **latest release**:

```json
"updateFeedUrl": "https://github.com/<you>/LuminaMC/releases/latest/download/latest.json"
```

`publishRelease` already produces `latest.json` + the launcher jar; the CI uploads them as
release assets, so that URL always resolves to the newest version.

> Make sure `latest.json`'s `downloadUrl` points at the GitHub asset, e.g.
> `https://github.com/<you>/LuminaMC/releases/download/v0.2.0/launcher-0.2.0.jar`.
> If `publishRelease` writes a `localhost` URL, update that task (or set a `baseUrl`).

---

## 3. Cut a release

Bump the version, then push a tag — CI does the rest:

```bash
git tag v0.2.0
git push origin v0.2.0
```

GitHub Actions builds the `.exe` + the three client mods and publishes a Release.
The next time anyone opens LuminaMC, the built-in updater finds it and shows the
**"Update verfügbar"** popup → download → relaunch. ✅

---

## 4. Code-signing the `.exe` (removes the SmartScreen warning)

Windows shows *"Windows protected your PC / unknown publisher"* for unsigned apps.
To remove it you need a **code-signing certificate** — this part needs money and is on you:

| Option | Cost | SmartScreen result |
|--------|------|--------------------|
| **OV certificate** (Sectigo, DigiCert…) | ~€150–300/yr | Warning fades as downloads build reputation |
| **EV certificate** (hardware token) | ~€300–500/yr | **Trusted immediately**, no warning |
| **Self-signed** | free | Only trusted on machines that import your cert — *not* for public distribution |

Once you have the certificate as a `.pfx`:

**Locally:**
```powershell
signtool sign /fd SHA256 /f your-cert.pfx /p <password> `
  /tr http://timestamp.digicert.com /td SHA256 `
  launcher\build\jpackage\LuminaMC-*.exe
```

**In CI:** uncomment the *"Sign the installer"* step in `release.yml` and add two repo
secrets (Settings → Secrets and variables → Actions):
- `WINDOWS_PFX_BASE64` — your `.pfx`, base64-encoded (`certutil -encode cert.pfx cert.txt`)
- `WINDOWS_PFX_PASSWORD` — its password

That's it — signed installers from every tagged release.

---

### TL;DR
1. `git init` + push to GitHub.
2. Set `updateFeedUrl` to your repo's `releases/latest/download/latest.json`.
3. `git tag vX.Y.Z && git push --tags` → CI builds + publishes → users get the update popup.
4. (Optional) buy a cert + enable the signing step → no more "unknown publisher".
