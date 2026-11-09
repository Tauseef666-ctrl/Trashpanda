# TrashPanda — Design Document

> Windows cleaner for the AI-agent era.
> **"Eats your digital garbage. Leaves your files alone."**
> v0.1 · 09 Nov 2026 · owner: Tauseef Khan

---

## 1. Identity

| Thing | Value |
|---|---|
| Name | **TrashPanda** (why: raccoon = "trash panda" = eats garbage, never touches your files) |
| Folder | `git\web\trash panda\` |
| Mascot | **Bin** — a raccoon holding a small broom. Sweeps when scanning, naps when idle |
| Positioning | The cleaner that knows what AI agents and dev tools leave behind |
| Audience | People running Claude / OpenCode / Cursor / Compose / Node / Gradle on Windows |
| License | MIT, open source (public repo later: `github.com/<you>/trashpanda`) |

**Elevator pitch.** Dev machines fill up with gigabytes of invisible junk — AI-agent
session databases, 250 MB CLI binaries installed three times, browser builds bundled
inside agent tools, npm/Gradle/pip caches, stale build outputs. Windows Storage
Sense does not know any of this. TrashPanda scans, explains every item in plain
language, risk-codes it, and removes only what you approve — always to the
Recycle Bin, never a hard delete.

**The three promises** (embed in UI, README, everywhere):
1. **Read-only by default.** Scanning never changes the disk.
2. **Reversible forever.** Recycle Bin + receipt with restore. Permanent delete must be enabled in settings AND typed in.
3. **Explained, not implied.** If TrashPanda can't say why a folder is safe to delete, it doesn't touch it.

---

## 2. Market intelligence (from 2026-11 competitor scan)

| Product | Layer | Gap TrashPanda fills |
|---|---|---|
| Windows Storage Sense / PC Manager | Built-in | No dev/agent awareness, opaque |
| Disposable "sweep" clones (WinSweep, WinSweeper…) | Many are scareware | TrashPanda is source-open, no ads, no registry snake oil |
| DevSweep (×4: mac Swift, .NET, PySide6, a €50 product) | CLI / Mac | No dedicated **Windows GUI** + agent residue |
| agent-janitor (×3 CLI/menu tools, mac/Linux) | CLI | Process-focused; our niche is **disk state + GUI** |
| BitBroom (.NET WPF), DiskTidy | Windows GUI | Strong general tools — but zero AI-agent category |
| SweepKit, Sweeply, CleanJet… | Mac / phone | Not Windows, not agents |

**Whitespace = Windows GUI + AI-agent residue + transparent receipts.** Nobody owns
that triangle. Competitive validation: multiple products launch monthly in this
space and all sell "understand your disk first" — that's the standard bar, not a moat.
The moat is the **agent-residue knowledge base** and safety UX.

---

## 3. Brand system

### 3.1 Color tokens (three themes, one token set)

| Token | Dark glass (default) | Light milk | Terminal neon |
|---|---|---|---|
| `bg` | `#0B0F14` | `#FAFAF7` | `#0A100A` |
| `surface` (glass card) | `#111827` @ 72% + blur | `#FFFFFF` | `#0D140D` |
| `border` | `#1F2937` | `#E7E5E0` | `#1E3320` |
| `text` | `#E5E7EB` | `#1B1B18` | `#CAF7C4` |
| `primary` (accent) | Mint `#34D399` | Teal `#0D9488` | Phosphor `#4AF626` |
| `riskSafe` | `#22C55E` | same | same |
| `riskReview` | Amber `#F59E0B` | same | same |
| `riskDanger` | Red `#EF4444` | same | same |
| `focus` | `#818CF8` | `#4F46E5` | `#8AFF80` |
| `scanline` opacity (neon only) | — | — | 4% black |

Risk triplet is identical across themes — the colors mean the same thing everywhere:
**green = auto-checkable, amber = you must tick it, red = informational only.**

### 3.2 Typography

- UI: **Inter** (700/600/400) — headings use 600, body 400
- Monospace (paths, sizes, terminal theme): **JetBrains Mono**
- Sizes: Hero 32 · H1 22 · H2 17 · body 14 · caption 12 (mono captions 13)

### 3.3 Logo & icon

- App icon: rounded-square, dark-glass bg, raccoon head silhouette (Cartesian, flat,
  2-tone gray + mint mask/bandit-eye), tiny mint broom over the shoulder
- Wordmark: `trashpanda` lowercase mono; never capitalize the b (Gen-Z rule: no Title Case)
- ASCII version for console theme output:
  ```
   (\_/)
  (=+'.)  trashpanda v0.1 — your disk did NOT feel clean today
  (")_(")
  ```
- Deliverables: `icon.png` 1024, `.ico` (16/24/32/48/64/128/256), tray 24/32

### 3.4 Voice & copy rules

- Honest, dry, a little funny, professional enough for a job interview demo
- Never fear-monger, never "optimiZE your PC 300%", no fake progress
- Always humanize sizes ("8.1 GB", not "8676171776 B")
- Sample lines:
  - Empty report: "Bin found nothing. Even trash pandas have standards."
  - Locked file skip: "In use right now — skipped, not killed. (Bin respects working files.)"
  - Execute confirm: "Moving 12.4 GB to Recycle Bin. Undo anytime from the receipt."
  - Agent db item: "OpenCode keeps every chat you've ever had here. Old history, 8.1 GB. Delete = that history is gone."

---

## 4. UX — screens & flows

### 4.1 Primary flow (MainActivity navigation stack)

```
[Home] ──▸ [Scanning] ──▸ [Report] ──▸ [Cleaning] ──▸ [Receipt]
    │                                                       │
    └──────────────── [Trash Bin (undo)] ◀──── restore ◀────┘
    └── [Settings] / [About] via side rail
```

**001 · Home "Drive health"**
- Hero: storage donut (free/used) + grayed "reclaimable now" sector from last scan cache
- Top-5 junk categories with sizes + risk chips, tap = jump to report scroll position
- Big mint CTA: `Scan now (re-reads the whole machine)`
- State: first-run → onboarding card explaining the 3 promises; no network anywhere

**002 · Scanning** (full-screen takeover, cancellable)
- Live counters (files hashed? no — files seen / bytes sized; throttled coalesced updates)
- Bin the mascot paces with the broom; progress = subtle glass shimmer, no % spinner theater
- Log rail (mono, collapsible): toggled paths currently walked — safety optics

**003 · Report** (the heart; list grouped by category)
- Grouped sections: `AI agents` · `Dev toolchains` · `Build outputs` · `Windows temp` ·
  `Browsers` · `Recycle Bin scope` · `Large personal files (review only)`
- Row anatomy: `[checkbox] [risk chip] 12 icons+name · size · human explanation · path (mono, tooltip)`
- Auto-rules section in header: `GREEN items auto-checked. Review ambers. Reds are look-don't-touch.`
- Explain-expander per row: "why it's safe / what regenerates / what will hurt"
- Group bulk actions: check all safe · uncheck all · copy report (markdown to clipboard)

**004 · Cleaning**
- Pre-confirm modal: total size + count + the strongest single warning if any amber touched
- Execute list streaming statuses: `moved → Recycle Bin ✓ / skipped (in use) / denied`
- Sub-second responsive Cancel (mid-item pause then stop; already-moved stays moved)

**005 · Receipt** (saved under `%APPDATA%\TrashPanda\receipts\`)
- Before/after free space delta (measured, not estimated)
- Items moved (size, original path, new recycle identity), skipped list, timestamp
- Buttons: `Restore all`, `Export receipt.md`, note on recycle-bin auto-purge

**006 · Trash Bin (in-app undo)**
- Union of recycle contents touched by us + in-app UndoStash
- Filter: by receipt / by date; `Restore` preserves original path (collision → `(2)` suffix)

**007 · Settings**
- Themes (three from §3.1) · User rule overrides (path/risk) · Exclusions
  (project folders, `TrashPanda` itself) · "Permanent delete" growth-locked behind settings + typed confirmation ·
  `Manage elevated tasks` (v0.2)

### 4.2 Secondary components

- **System tray**: panda icon → popup donut + `Report now` / `Open TrashPanda`; background scan optional (off by default)
- **Toasts**: `12.4 GB moved. 0 regrets.` with `Undo` inline action
- **Empty/error states**: every screen has one, written in the §3.4 voice
- **Keyboard**: `Space` toggles focused row, `Enter` opens explain, `F6` cycles sections (compliance with basic a11y: contrast ≥ 4.5, focus visible, no screen-reader-hostile glass)

### 4.3 Explicit non-goals (v0–v1)

No registry cleaning. No driver updaters. No antivirus. No disk-by-mail "booster".
No background daemon by default. No account, no cloud, no telemetry. App size
budget: **< 80 MB installed** (we exist because Electron bloats drives).

---

## 5. Architecture (Compose Desktop)

### 5.1 Gradle modules

```
trash panda/
  app/        → Compose UI, navigation, theming, mascot, DI wiring
  engine/     → scan + plan + execute pipeline (pure Kotlin, NO UI imports)
  rules/      → built-in TOML db + loader + validation (ktoml) + fixtures
  platform/   → Windows-specific pies: NIO walker, JNA recycle, locks, elevation bridge
  tools/      → future CLI / dev scripts (v0.x stub only)
```

Dependency rule (lint-enforced): `app → engine → rules → platform`. Nothing ever
inverted; `engine` must tree-shake by running headless (no UI imports —集结 DI-free, constructor injection).

### 5.2 Core types

```kotlin
risk: { green, amber, red }
class Rule(
  id: String, title: String, explanation: String,
  risk: Risk, category: CategoryId,
  patterns: List<PathGlob>,            // env-expanded at load: %LOCALAPPDATA%, %USERPROFILE%
  scanConstraints: ScanConstraints,     // minAgeDays, minSizeMB, maxDepth, followJunctions=false
  action: Action,                      // recycle | reportOnly | command(elevated)
  dropWhenPaths: List<String>,         // config files NEVER to touch: .git, .ssh, wallets, PATH files
)
sealed class ScanNode { dir(name, size, items), file(name, size, locked) }
data class PlanItem(rule, node, why, plannedAction)
data class Receipt(id, startedAt, moved, skipped, freedBytesMeasured, items, targetTheme)
```

### 5.3 Module responsibilities

| Module | Owns | Must never do |
|---|---|---|
| `engine.scan` | Parallel WalkTask pool, sizing bottom-up, progress Flow | Mutate disk; follow junctions; delete |
| `engine.execute` | Plan→actions, per-item retry/skip policy, receipt writer | Command-elevate alone; touch outside plan; parallel-delete same dir |
| `rules` | Load + validate built-in + user override TOMLs | Trust paths without guards; network |
| `platform.io` | NIO walker w/ junction skip, `\\?\` long paths, in-use probe | Open file handles for read-modify |
| `platform.winapi` | JNA SHFileOperation (recycle), kernel32 lock probe | Anything without `FOF_ALLOWUNDO` |
| `platform.elevate` | One-shot elevated PowerShell w/ JSON temp-file I/O | Carrying app token; installing services |
| `app` | Compose, theme tokens, mascot, narrowing Model View | Business logic; shell calls |

### 5.4 Scan algorithm (per top-level root)

- **Root fans out**: one worker per top-level dir (coroutine, Dispatchers.IO bounded to `cores-1`, cap 8)
- **Inside a subtree**: sequential descent (matches robocopy /MT behavior empirically)
- **Junction/reparse guard**: `BasicFileAttributes.isOther()` or `isSymbolicLink()` ⇒ record-as-mirror, **never descend**
- **Long paths**: canonical target `\\?\` + length > 240 ⇒ auto-prefix
- **Sizing**: bottom-up byte sums only, no per-file open; stat-only walker
- **Locked probe piggybacks**: executed look-ahead ONLY during Plan phase, not scan phase
- **Progress heartbeat**: `MutableStateFlow(Snapshot)` + `sample(125.ms)` to UI — no per-file bus spam
- **Failure policy**: per-branch best-effort; a denied dir contributes "denied" chip, never halts the walk

### 5.5 In-use detection (the hard 10%)

1. Plan probe: JNA `kernel32.CreateFileW(path, GENERIC_WRITE, 0 /*no-share*/, OPEN_EXISTING)`
   → sharing violation ⇒ mark `locked`, exclude from plan (green→gray, never hidden)
2. Fallback: recycle attempt catches `ACCESS_DENIED`; item → skipped w/ human note
3. Never `taskkill`/force whatever; if the owning app is detected (procname via
   `GetNamedPipeServerProcessId`-lite trick or scan of snapshots), offer inline "close apps first?" link

### 5.6 Deletion semantics

- Default = **Windows Recycle Bin via JNA `IFileOperation`/`SHFileOperation` w/ `FOF_ALLOWUNDO`**
- Oversized-file path (recycle can be slow > ~1 GB): move to
  `%LOCALAPPDATA%\TrashPanda\UndoStash\<receiptId>\` (auto-purge user-set default 7 days, prompt)
  — chosen interactively per item group when > 1 GB
- **Permanent delete** exists only behind: Settings toggle + per-run typed phrase. Default OFF forever.

### 5.7 Elevated helper (v0.2, architecture now)

- One-shot: `Start-Process powershell -Verb RunAs -Wait '-NoProfile -File elevateInvoked.ps1 -Task hibernateOff -OutFile %TMP%\tp-result.json'`
- Tasks: `hibernateOff/On`, `dismComponentCleanup`, `windowsTempPurge` — each declares risk & expected output
- Offline output parse; `app` displays verbatim tail; auto-cancel UX if UAC declined

### 5.8 Persistence & privacy

```
%APPDATA%\TrashPanda\settings.json          → theme, exclusions, permanent-delete-flag
%APPDATA%\TrashPanda\user-rules.toml        → overrides (risk downgrades logged)
%APPDATA%\TrashPanda\receipts\YYYYMMDD-*.json
%APPDATA%\TrashPanda\logs\scan-*.ndjson     → rotated at 5 MB × 3
```
Zero network in v0/v1: no crash-report upload, no update URLs, no LLM features —
the mascot explains, not ChatGPT. Update check = GitHub Releases manual link only.

---

## 6. Rule database — the real product (v0.1 catalog)

Every entry: risk · constraint · human one-liner. Bullets = actual paths observed
live on this laptop 09 Nov (proof set, first fixtures).

### Group A — AI-agent residue (FLAGSHIP category)

| # | Target | Path(s) | Risk | Rule text |
|---|---|---|---|---|
| A1 | OpenCode session DB | `%USERPROFILE%\.local\share\opencode\opencode.db` (**8.1 GB here**) | **amber** | "Every chat you've ever had. Close OpenCode first. Delete = history gone." Auto-detect `-wal/-shm` siblings |
| A2 | OpenCode/dist artifacts | `%USERPROFILE%\.cache\opencode\**\*.exe` + Temp\opencode\opencode-upgrade-*.exe | **green** | Stale build/upgrade caches inside cache/Temp dirs |
| A3 | Claude Code transcripts | `%USERPROFILE%\.claude\projects\**\*.jsonl` | **red→reportOnly** | Plaintext chat + tool logs. Never auto-select. Offer archive-only |
| A4 | npm global installs | `%APPDATA%\npm\node_modules\*` (259 MB × N here: claude, kilo, opencode×3) | **amber** | "Real tools installed N times. We list each copy; you pick." Never pre-tick |
| A5 | Playwright/patchright browsers | `%LOCALAPPDATA%\ms-playwright\*`, `*.profile\patchright-browsers\*` (0.7 GB here) | **green** | Any `npx playwright install` re-fetches |
| A6 | Agent-bundled browsers | `%LOCALAPPDATA%\hermes\tools\chromium-*` (5.4 GB here) | **amber** | "A third-party agent shipped its own Chromium. Re-downloads when used." |
| A7 | HuggingFace / Ollama models | `~\.cache\huggingface`, `~\.ollama\models` | **red** | "16 GB model = 16 GB real re-pull. Never pre-selected." |
| A8 | Cursor/antigravity/IBM caches | `%LOCALAPPDATA%\cursor-agent`, `AppData\Roaming\Antigravity IDE\…` | **amber** | Cache subdirs only; extensions & settings never |
| A9 | VS Code working dirs | `%APPDATA%\Code` → `Cache`, `Code Cache`, `CachedData`, `GPUCache`, `logs` (skip `workspaceStorage` always) | **green** (offline-once check for `Code.exe`) | |

### Group B — Dev toolchain caches

| # | Target | Path(s) | Risk |
|---|---|---|---|
| B1 | npm cache | `%LOCALAPPDATA%\npm-cache` | green |
| B2 | Gradle caches | `%USERPROFILE%\.gradle\caches` (1.9 GB here) | green |
| B3 | Gradle wrapper zips | `.gradle\wrapper\dists` | amber (re-download cost noted) |
| B4 | pip / uv | `%LOCALAPPDATA%\pip\cache`, `%LOCALAPPDATA%\uv\cache` | green |
| B5 | Cargo registry | `%USERPROFILE%\.cargo\registry` | green |
| B6 | pnpm / yarn / bun caches | `%LOCALAPPDATA%\pnpm\store*`, `%LOCALAPPDATA%\Yarn\Cache`, `%LOCALAPPDATA%\bun\install\cache` | green |
| B7 | Electron builders | `%LOCALAPPDATA%\electron\Cache`, `%LOCALAPPDATA%\electron-builder\Cache` | green |
| B8 | Android (.gradle project extras) | user dirs list, v0.2 |

### Group C — Build outputs (scanned INSIDE configured project roots only, never blind-`C:\`)

- Patterns: `**/build`, `**/dist`, `**/out`, `**/target`, `**/.next`, `**/__pycache__`,
  `**/node_modules` (amber), `**/.dart_tool`
- Guard rules: exclude roots whose tree contains a top-level `Cargo.toml` targeted file for `target` deletion;

  skip any dir with a sibling `.git` UNC under 30-days-old commit (recency guard: mtime > 60 days → default unchecked)
- This laptop's real targets: `git\web\NewPipe\desktopApp\build` (1.36 GB), `git\animation video\build` (0.45 GB) — our first fixtures

### Group D — Windows housekeeping

| # | Target | Risk | Note |
|---|---|---|---|
| D1 | `%LOCALAPPDATA%\Temp` | green | Skip anything modified < 24 h; skip `opencode` live dir (our own process' kin) |
| D2 | `C:\Windows\Temp` | green-elevated (v0.2) | via elevated task |
| D3 | Recycle Bin scope meter | reportOnly | Own size, emptying is manual (it's the undo store!) |
| D4 | `hiberfil.sys` | reportOnly | Never file-delete. Action = guided `powercfg /h off` elevated task |
| D5 | `Windows\Installer`, `WinSxS`, `DriverStore`, `pagefile.sys` | **forbidden** | Hard-banned from file ops; WinSxS only ever via `DISM /StartComponentCleanup` (v0.2) |
| D6 | `SoftwareDistribution\Download` | green-elevated (v0.2) | |

### Group E — Browser caches (green, only when owning process not running)

Per profile dir (`Default`, `Profile N`): `Cache*`, `Code Cache`, `GPUCache`,
`ShaderCache`, `Media Cache`, `Service Worker\CacheStorage` — Brave/Chrome/Edge/Firefox
paths detected from standard roots. Never touch `Bookmarks`, `Cookies`, `Passwords`,
`Local Storage`, `IndexedDB`.

### Group F — Large personal files (report-only forever)

`Downloads` + `Desktop` + user-chosen roots: files > 200 MB listed (path, size, mtime) —
TrashPanda explains but never checks them. Trust = never touching user data.

**Rule invariant (executable must read this literally):** if a rule cannot produce its
one-line human explanation from §6 text, the engine refuses to include the item. No
explanation, no deletion.

---

## 7. Packaging & distribution

- `jlink` trimmed SDK runtime + `jpackage app-image` → launcher exe + bundled msstore-free `.msi`
- Budget: < 80 MB installed (test in CI); Opt In ProGuard/R8 minimal config
- Code signing: not v0.1; when shipped — Azure Trusted Signing (~$9/mo) + write
  SmartScreen transparency README ("what this exe does, how to read the source")
- Metadata: `trashpanda-v0.1.0-windows-x64.msi` + SHA-256 in release notes (audit-friendly)
- CI: GitHub Actions `windows-latest` — `gradlew build test packageMsi`, upload artifact, `ktlint` + `detekt`

---

## 8. Testing & safety invariants (CI-gated)

Fixture tree = synthetic `FakeDrive/` with planted traps:

1. **Never-follow**: junction → `C:\Windows`, symlink to user data — assert untouched & reported "mirror"
2. **Never-delete-outside**: any test path whose resolved root isn't the fixture root fails arithmetic
3. **Config-kill-protect**: `.git`, `.ssh\id_*`, `settings.json` of browsers — assert no rule matches
4. **Locked-file**: planted sharing-lock (test helper) → skipped gracefully, no crash, no partials
5. **Undo round-trip**: recycle → restore → byte-identical + mtime within tolerance
6. **Receipt integrity**: moved list = restore list; freed bytes within 3% of free-space delta (JVM IO noise)
7. **Rule golden files**: TOML parse → invariants; a bug here is a PR-blocker, not a warning
8. **Benchmark smoke**: cold scan of this machine's profile — record ms in CI artifact table

---

## 9. Roadmap

| Version | Contents (realistic, not fantasy) |
|---|---|
| **v0.1 MVP** (≈8-10 weeks evenings) | Groups A1-A6, A9, B1-B7, C-patterns, D1, E — scan + report + execute + receipts + undo + 3 themes |
| **v0.2** | Elevated tasks (hibernation, DISM, Windows Temp), tray + scheduled Task Scheduler clean, exclusions UI |
| **v0.3** | CLI `trashpanda --dry-run --json`, duplicate finder (size→head-hash→SHA-256, keep-newest), `agent residue` JSON export for CI |
| **v1.0** | Scaled rule catalog, signed, msstore page, autoupdate via GitHub Releases tags, docs site (GitHub Pages, brand from §3) |
| **v2 dreams** | Co-ownership: WSL `ext4.vhdx` compact (lossless, the #1 dev win), Docker Desktop vhdx compact, git worktree archive, per-project `clean` integrations |

---

## 10. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| AV/SmartScreen false positive (file-deleting exe) | Medium | Open source + source-verifiable, small exe, receipt transparency, signing at v1, submit to MS AV vendors `/false-positive` |
| Rules rot (Windows/tool updates move paths) | High (time) | Rules are data, PR-able; scan-phase never *executes* on unknowns; monthly maintenance |
| User trust damage from one bad deletion | Unbounded | Triple-wall: rule file-explanations, amber/red requiring ticks, Recycle/Stash by default |
| Compose Desktop learning pains | Low | Same stack as NP-Desktop; engine is framework-agnostic if UI changes |
| Windows API friction (junctions, long paths, UAC) | Medium | platform module siphons it; every API call gets a fixture test first |
| Scope creep → never ships | High | PLAN.txt MVP list is the contract; anything else is v0.2+ |
| Name/mascot collision with other "trash panda" projects | Medium | Distinct mascot art, `trashpanda` lowercase wordmark; final shine: GitHub org + domain check at launch |

---

## 11. Open questions (answer at P0, not before)

1. GitHub org / repo name & public timeline — repo counts toward portfolio only when it exists
2. Code-signing budget now or v1?
3. Mascot: AI-generated art this weekend (`opencode` image models available) vs commission later?
4. Repo keeps the folder name `trash panda` (space) or `trashpanda` (CI safest)? — default: keep space locally, repo name `trashpanda`
5. Windows version floor: Win10 22H2 (wide) vs Win11-only (less API pain)? — default recommendation: Win10 22H2

*End of DESIGN.md — read together with PLAN.txt. Both are contracts; breaking either = PR-review conversation.*
