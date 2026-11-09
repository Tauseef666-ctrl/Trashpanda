<div align="center">

<img src="docs/logo.png" alt="trashpanda" width="180"/>

# Trashpanda

**Windows cleaner for the AI-agent era.**
*Eats your digital garbage. Leaves your files alone.*

Kotlin · Compose Desktop · 100% local · no telemetry · MIT

[![build](https://github.com/Tauseef666-ctrl/Trashpanda/actions/workflows/build.yml/badge.svg)](https://github.com/Tauseef666-ctrl/Trashpanda/actions/workflows/build.yml)
![platform](https://img.shields.io/badge/platform-Windows%2010%2B-1F2937)
![license](https://img.shields.io/badge/license-MIT-22C55E)
![size](https://img.shields.io/badge/app%20size%20%3C%2080MB-60MB%20built-34D399)

</div>

---

Your dev machine fills up with gigabytes of invisible junk — AI-agent session
databases, 250 MB CLI binaries installed three times, browser builds bundled
inside agent tools, npm/Gradle/pip caches, stale build outputs. Windows Storage
Sense doesn't know any of this. **TrashPanda scans, explains every item in plain
language, risk-codes it, and removes only what you approve — always to the
Recycle Bin, never a hard delete.**

## The three promises

1. **Read-only by default.** Scanning never changes the disk.
2. **Reversible forever.** Recycle Bin + receipt with restore.
3. **Explained, not implied.** No explanation, no deletion.

Every item gets an **importance verdict** in plain words, so you always know what
you're looking at:

| Verdict | Meaning |
|---|---|
| `trash` | Garbage. Safe to clean. |
| `regenerates` | Tools re-download or rebuild it automatically. Costs time, not data. |
| `review` | Your data or a real tool. TrashPanda asks — **you** decide. |
| `keep` | System-critical. TrashPanda never cleans it. |

## Preview

![Home screen preview](preview/preview-home.png)

## What it finds (v0.1 rule db — 30 rules)

- **AI-agent residue (flagship):** OpenCode/Claude/Kilo session DBs & caches,
  npm CLIs installed ×3, Playwright & agent-bundled Chromium, Cursor/VS Code caches
- **Dev toolchains:** npm, Gradle, pip/uv, Cargo, pnpm/yarn/bun, Electron builder, conda
- **Build outputs:** `build` / `dist` / `out` / `target` / `node_modules` (inside project roots)
- **Windows housekeeping:** Temp (>24 h), thumbnail cache, shader cache, crash dumps,
  WER queue, Windows.old — with `WinSxS`/`Installer`/`DriverStore` hard-banned
- **Browsers:** per-profile caches only — never bookmarks, passwords, or history
- **Personal files:** >200 MB in Downloads/Desktop, report-only forever
- **Multi-drive:** scans every fixed drive, not just C:\

## Try it (console, read-only)

```powershell
# dry-run of %LOCALAPPDATA%\Temp — prints a receipt, deletes nothing
gradlew.bat :engine:run --args="--temp"

# dry-run of any directory
gradlew.bat :engine:run --args="--scan C:\path\to\dir"

# whole system: every fixed drive
gradlew.bat :engine:run --args="--all-drives"

# guided Q&A: Bin explains each category and asks what to delete
gradlew.bat :engine:run --args="--ask --temp"

# guided Q&A + real Recycle Bin moves + a receipt you can undo (explicit, opt-in)
gradlew.bat :engine:run --args="--ask --temp --execute"

# restore everything a receipt moved back to its original paths
gradlew.bat :engine:run --args="--undo latest"
```

## Try it (desktop app)

```powershell
gradlew.bat :app:run          # Compose Desktop Home screen + read-only demo scan
gradlew.bat :app:packageDistribution   # native app build → app/build/compose/binaries
```

## Build from source

```
JDK 21 · Windows 10 22H2+ (Win11 ideal)
git clone https://github.com/Tauseef666-ctrl/Trashpanda
cd Trashpanda
gradlew.bat build    # compiles + runs the full test suite
```

## Project map

| Module | Owns | Must never do |
|---|---|---|
| `app` | Compose UI, themes, mascot | business logic; shell calls |
| `engine` | scan + plan + interview + report (pure Kotlin, no UI) | mutate disk; follow junctions; delete |
| `rules` | built-in TOML rule db + loader + validation | trust paths without guards |
| `platform` | Windows IO: junction guard, long paths, env expand | open files for read-modify |
| `tools` | future CLI stubs | — |

Safety invariants are **CI-gated tests**: junctions are mirrored never followed;
`.git`/`.ssh`/wallets/cookies can never match a recyclable rule; scan-only is
asserted byte-for-byte on a live fixture tree.

## Roadmap

- **v0.1 (now)** — scaffold · rule db · read-only engine · interview · Compose Home
- **v0.2** — Recycle Bin execution + receipts + undo (**shipped**) · report screen + GUI execute/undo (**shipped**)
- **v0.3** — themes (Light milk, Terminal neon) · elevated tasks (hibernation, DISM) · scheduler · tray
- **v1.0** — signed installer · duplicate finder · `--dry-run --json` CLI

Full contracts: [PLAN.txt](PLAN.txt) + [DESIGN.md](DESIGN.md).

## License

MIT — see [LICENSE](LICENSE).
