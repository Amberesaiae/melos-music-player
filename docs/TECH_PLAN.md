# Melos Music Player - Technical Implementation Plan

## Overview

Phased implementation plan from project setup to MVP launch.

---

## Project Timeline

| Phase | Duration | Weeks | Focus |
|-------|----------|-------|-------|
| Phase 0: Foundation | 2 weeks | 1-2 | Project setup, architecture scaffolding |
| Phase 1: Core Player | 4 weeks | 3-6 | Playback engine, audio pipeline |
| Phase 2: Library | 4 weeks | 7-10 | Local file browsing, metadata, playlists |
| Phase 3: Servers | 5 weeks | 11-15 | Subsonic API, caching, offline mode |
| Phase 4: Polish | 4 weeks | 16-19 | UI refinement, Android Auto, performance |
| Phase 5: Launch | 3 weeks | 20-22 | Testing, documentation, release |

**Total MVP: 22 weeks (5-6 months)**

---

## Phase 0: Foundation (Weeks 1-2)

- Create GitHub repository with branch protection
- Set up Gradle multi-module project structure
- Configure version catalog (libs.versions.toml)
- Set up Hilt dependency injection
- Create module stubs (core + feature + platform)
- Configure linting (detekt, ktlint)
- Set up CI/CD pipeline (GitHub Actions)
- Create development documentation

---

## Phase 1: Core Player (Weeks 3-6)

- Integrate AndroidX Media3 1.5+
- Create MelosPlayer wrapper class
- Implement gapless playback
- Add ReplayGain support
- Create 10-band graphic EQ
- Implement MediaSession + foreground service
- Handle audio focus + headset buttons

---

## Phase 2: Library Management (Weeks 7-10)

- MediaStore scanning + metadata extraction
- Room database schema and DAOs
- Background indexing service
- Library browse UI (Artists, Albums, Songs tabs)
- Manual playlist creation
- Smart playlist rule builder
- Auto-playlists (Recently Played, Most Played, etc.)
- Unified search with real-time results

---

## Phase 3: Server Integration (Weeks 11-15)

- Retrofit Subsonic API client
- Authentication (salted MD5 token)
- Server setup UI (add, edit, test connection)
- Background library sync service
- Multi-server support
- Offline caching with LRU eviction
- Download manager with progress UI
- SMB/CIFS support via jcifs-ng

---

## Phase 4: Polish & Platforms (Weeks 16-19)

- Material 3 dynamic theming
- Shared element transitions and animations
- Android Auto (CarAppService)
- Performance optimization with Paging 3
- TalkBack accessibility support
- i18n string resources

---

## Phase 5: Launch Preparation (Weeks 20-22)

- End-to-end testing (Android 10-15, multiple devices)
- Performance benchmarks (50K+ library)
- User documentation
- F-Droid submission
- Google Play Store listing
- GitHub release with signed APKs

---

## Post-Launch Roadmap

| Version | Features |
|---------|----------|
| v1.1 | Chromecast, community bug fixes |
| v1.2 | Wear OS, Android TV, widgets |
| v1.3 | Parametric EQ, AutoEQ database, bit-perfect |
| v2.0 | Plex/Emby, cloud storage (Drive, Dropbox) |

---

## Success Metrics (Month 1 Post-Launch)

| Metric | Target |
|--------|--------|
| F-Droid downloads | 5,000+ |
| Play Store downloads | 10,000+ |
| GitHub stars | 500+ |
| App rating | 4.5+ stars |
| Crash-free sessions | 99%+ |

---

*Document Version: 1.0 | Last Updated: March 23, 2026 | Status: Draft*
