# Changelog

All notable changes to TISAM are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **Tab row now uses `Elements.row(Justify.EQUAL, ...)`** instead of a private
  equal-width row class. The tabs still fill the dialog exactly, with the
  rounding remainder on the last slot, but a hundred lines of hand-written
  layout code are gone.
- **Card shell is built from generic decorations.** `ModuleCardElement` used to
  draw its own gradient ring and run its own spark swarm with a static map. It
  now wraps the content tree in `GradientRing` + `Sparkles` from Hellow:
  - the gradient ring keeps its animated start color on critical/featured/locked
    tiers,
  - the swarm keeps its peak opacity (0xB4) and is keyed by entry id, so the
    virtualized list's per-frame rebuild no longer restarts it,
  - `Sparkles.clearAll()` replaces the private `clearSparks()`.
- Hellow is now a **separate mod dependency**: compiled against
  `libs/hellow-1.0.0.jar` and resolved at runtime through `neoforge.mods.toml`.
  The jar is not bundled.

### Removed

- `ModListScreen.EqualWidthRow` (superseded by `Justify.EQUAL`).
- The hand-drawn card border, spark particle classes and static swarm map
  (superseded by Hellow decorations).

## [1.0.0] — Initial release

- Startup scan of `mods/` matched by jar filename against
  `config/tisam/modules.json`.
- Three-step flow: first prompt → mod list → install guide.
- Reminder frequency decay (every launch → 3 → 7 → 15).
- Permanent skip with a 5-second confirmation cooldown and configurable
  disclaimer.
- Priority tags (`featured`, `critical`, `normal`, `optional`) with sorting and
  visual badges; required entries cannot be skipped.
- Title-screen entry button and `/tisam` client commands.
- Preview mode for modpack authors.
- All data stored locally; TISAM never bundles or auto-downloads mods.
