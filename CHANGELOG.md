# Changelog

All notable changes to TISAM are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Tab row uses `Elements.row(Justify.EQUAL, ...)`; the private equal-width row class is removed.
- Card shell uses `GradientRing` + `Sparkles` instead of hand-drawn borders and a private particle system.
- Hellow is compiled against `libs/hellow-1.0.0.jar` and declared as a required mod dependency at runtime.

### Removed

- `ModListScreen.EqualWidthRow`.
- Hand-drawn card border, spark particle classes and the static swarm map.

## [1.0.0]

- Initial release.
