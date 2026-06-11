# Code signing (stop the Windows SmartScreen warning)

> **Status (June 2026):** Applied to SignPath Foundation on 2026-06-09 — **declined**
> on 2026-06-10: the project doesn't yet show enough *external verification signals*
> (GitHub stars/forks/contributors, Reddit/YouTube/blog mentions, sustained user
> activity). Not a quality judgment; they explicitly invite reapplying once the
> project has public traction. **Plan:** keep releasing, grow the community
> (Discord, posts, stars), then reapply at <https://signpath.org/apply>.
> The CI signing steps below stay a no-op until then — releases work unsigned,
> and the README explains the SmartScreen "Run anyway" flow to users.

Windows flags `LuminaMC-windows.zip` as coming from an *“unknown publisher”* because
the `.exe` inside it isn’t **code-signed**. The only real fix is signing the `.exe`
with a certificate from a CA that Windows trusts. A self-signed certificate does **not**
help SmartScreen.

For an open-source project the free option is **SignPath Foundation**, which signs OSS
releases at no cost (certificate issued by Certum). The CI is already wired for it
(`.github/workflows/release.yml`) — it stays a no-op until you complete the one-time
setup below, so releases keep working unsigned in the meantime.

> Result after setup: the scary *“Windows protected your PC – unknown publisher”* prompt
> shows **LuminaMC** as the verified publisher instead. With the OV certificate SignPath
> issues, SmartScreen reputation still warms up over the first downloads, but the red
> “unknown publisher” wall is gone.

---

## Prerequisites

- A public GitHub repo (✅ `Squexso/Lumina`).
- An **OSI-approved open-source license** at the repo root (✅ `LICENSE.txt` is now
  **GPL-3.0**, so the project qualifies for the free SignPath Foundation plan).

## Paid alternatives (no open-sourcing required)

- **Azure Trusted Signing** — ~$10/month, near-instant SmartScreen trust. Requires identity
  verification (registered business, or an individual with 3+ years verifiable history).
- **OV certificate** (~€120–200/yr): reputation builds over downloads.
  **EV certificate** (~€300–400/yr): instant SmartScreen trust.

  All of these sign the same artifact; only the certificate source differs. The CI step
  below can be pointed at any of them (SignPath also supports paid/private certificates).

## One-time setup (you do this part)

1. **Apply to SignPath Foundation** (free OSS plan): <https://signpath.org/apply>
   Give them the repo URL + license. Approval is manual and can take a few days.

2. Once approved, open the **SignPath dashboard** and note your **Organization ID**
   (Settings → Organization).

3. Create, in the dashboard:
   - a **Project** — e.g. slug `lumina`
   - an **Artifact Configuration** — use the *zip archive* template so it signs the
     `.exe` files **inside** `LuminaMC-windows.zip` (slug e.g. `windows-zip`)
   - a **Signing Policy** — e.g. slug `release-signing`

4. Connect the GitHub build: install the **SignPath GitHub app** / add this repository as
   a *trusted build system* so SignPath can verify the build origin
   (dashboard → Trusted Build Systems → GitHub).

5. Create a **CI API token** (dashboard → API Tokens).

6. In **GitHub → repo → Settings → Secrets and variables → Actions**, add:

   **Secret**
   | Name | Value |
   |------|-------|
   | `SIGNPATH_API_TOKEN` | the API token from step 5 |

   **Variables** (Variables tab, not Secrets)
   | Name | Value (example) |
   |------|-----------------|
   | `SIGNPATH_ORGANIZATION_ID`     | your org ID |
   | `SIGNPATH_PROJECT_SLUG`        | `lumina` |
   | `SIGNPATH_POLICY_SLUG`         | `release-signing` |
   | `SIGNPATH_ARTIFACT_CONFIG_SLUG`| `windows-zip` |

7. Cut a release as usual:
   ```bash
   # bump launcher/build.gradle version, then:
   git tag v0.1.4 && git push origin v0.1.4
   ```
   The `Release` workflow now uploads the zip to SignPath, waits for the signed result,
   swaps it back in, and publishes the **signed** `LuminaMC-windows.zip`.

The signing steps are guarded by `vars.SIGNPATH_ORGANIZATION_ID`, so until you set that
variable the workflow simply skips signing and releases unsigned (exactly as before).

## Verify a signed build

Right-click the downloaded `LuminaMC.exe` → **Properties → Digital Signatures** — you
should see a valid signature naming LuminaMC.

## References

- SignPath GitHub integration: <https://docs.signpath.io/trusted-build-systems/github>
- SignPath Foundation (free OSS): <https://signpath.org/about>
