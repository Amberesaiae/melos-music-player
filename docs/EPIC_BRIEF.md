# Melos Music Player - Epic Brief

## Project Vision

**Melos** is an open-source Android music player designed for people who own their music libraries and self-host their music servers.

### Mission Statement

> Make self-hosted music accessible to everyone - no 3-day configuration required.

---

## Problem Statement

**Symfonium** (the market leader) suffers from:
- Steep learning curve - Users report 1-3 days to configure
- Closed source - No community contributions
- Feature overload - 256-band EQ overwhelms casual users
- Vendor lock-in - Encrypted cache files

### Target Users

| Persona | Description |
|---------|-------------|
| Self-Hosted Enthusiast | Runs Navidrome/Jellyfin, 10K-50K tracks |
| Local Library Owner | 5K-30K FLAC/MP3 files on device/SD card |
| Privacy-Conscious Listener | No tracking, no ads, transparent software |
| Audiophile on Budget | Good EQ, gapless playback, format support |

---

## Solution: 80% of Symfonium's capability, 40% of the complexity

| Dimension | Symfonium | Melos |
|-----------|-----------|-------|
| Setup Time | 1-3 days | 10-15 minutes |
| License | Proprietary ($5.99) | Open Source (GPL-3.0) |
| EQ | 256-band + PEQ | 10-band + AutoEQ presets |
| Server Support | 10+ APIs | Subsonic API + SMB |
| Caching | Encrypted | Unencrypted, user-accessible |
| Onboarding | Overwhelming | Guided wizard |

---

## MVP Goals (v1.0)

1. Local Playback Excellence - All major formats, gapless, ReplayGain, 10-band EQ
2. Library Management - Browse by metadata, manual + smart playlists, search
3. Server Integration - Subsonic API, offline caching, ratings sync
4. Modern UI/UX - Material 3, Android Auto, lock screen controls
5. Performance - 50K tracks, <2s startup, <150MB RAM

---

## Timeline

| Phase | Duration | Milestone |
|-------|----------|-----------|
| Foundation | Weeks 1-6 | Core player, database, basic UI |
| Library | Weeks 7-10 | Metadata browsing, playlists, search |
| Servers | Weeks 11-15 | Subsonic API, caching, offline mode |
| Polish | Weeks 16-19 | Android Auto, Material You, performance |
| Launch Prep | Weeks 20-22 | Testing, docs, F-Droid submission |

**Total MVP: 22 weeks (5-6 months)**

---

*Document Version: 1.0 | Last Updated: March 23, 2026 | Status: Draft*
