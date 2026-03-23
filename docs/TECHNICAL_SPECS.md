# Melos Music Player - Technical Specifications

## System Overview

Melos is a native Android music player built with Kotlin and Jetpack Compose, following Clean Architecture principles with a modular structure.

---

## Architecture

Three-layer Clean Architecture:
- **Presentation Layer** - Jetpack Compose UI + ViewModels
- **Domain Layer** - Use Cases, Domain Models, Interfaces
- **Data Layer** - Repositories, APIs, Database, Cache

Key principles: unidirectional data flow, dependency inversion, reactive state via Kotlin StateFlow, offline-first with Room DB.

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.x |
| UI Framework | Jetpack Compose | 1.7.x |
| Architecture | Clean Architecture + MVVM | - |
| DI Framework | Hilt | 2.52+ |
| Async | Kotlin Coroutines + Flow | 1.9.x |
| Playback Engine | AndroidX Media3 ExoPlayer | 1.5.x |
| Media Session | AndroidX Media3 Session | 1.5.x |
| Local Database | Room | 2.6.x |
| HTTP Client | Retrofit + OkHttp | 2.11.x / 4.12.x |
| Image Loading | Coil | 3.x |
| SMB Shares | jcifs-ng | 2.1.x |
| Build System | Gradle + Version Catalogs | 8.x |

**Note:** This project uses `androidx.media3.*` (NOT the legacy `com.google.android.exoplayer2.*`).

---

## Module Structure

```
melos-music-player/
|-- app/                    # Application entry point
|-- core/
|   |-- player/             # Media3 ExoPlayer wrapper, EQ, gapless
|   |-- database/           # Room DB, DAOs, migrations
|   |-- network/            # Retrofit, Subsonic API client
|   |-- model/              # Domain models (Song, Album, Artist)
|   +-- ui/                 # Shared Compose components, theme
|-- feature/
|   |-- library/            # Local file browsing
|   |-- now-playing/        # Full-screen player UI
|   |-- playlists/          # Playlist management
|   |-- search/             # Unified search
|   |-- server/             # Subsonic server setup + sync
|   +-- settings/           # App configuration
+-- platform/
    |-- android-auto/       # CarAppService implementation
    +-- notifications/      # MediaSession + foreground service
```

---

## Performance Requirements

| Metric | Target |
|--------|--------|
| Cold Start | <2 seconds |
| Library Load (50K tracks) | <5 seconds |
| Playback Start Latency | <500ms |
| Scroll Performance | 60 FPS |
| RAM Usage (playback) | <150 MB |
| CPU (idle playback) | <5% |
| APK Size | <15 MB (F-Droid) |
| Battery (8h playback) | <15% drain |
| Crash-Free Sessions | >99.5% |

---

## SDK Requirements

| Attribute | Value |
|-----------|-------|
| Min SDK | API 29 (Android 10) |
| Target SDK | API 35 (Android 15) |
| Compile SDK | API 35 |
| Kotlin | 2.1.x |
| Media3 | 1.5.x |
| Jetpack Compose BOM | 2024.12.01 |
| Hilt | 2.52+ |

---

## Audio Engine Details

- **Playback:** Media3 ExoPlayer with gapless support
- **Equalizer:** 10-band graphic EQ via Android `Equalizer` API
- **ReplayGain:** Album and track normalization
- **Audio Focus:** Handles phone calls, notifications
- **Headset Buttons:** Skip, play/pause via media button events
- **Formats:** FLAC, MP3, ALAC, AAC, OGG, DSD, WAV, OPUS

---

## Database Schema (Room)

Entities: `SongEntity`, `AlbumEntity`, `ArtistEntity`, `PlaylistEntity`, `PlaylistSongCrossRef`, `CacheEntryEntity`

Key DAOs: `SongDao`, `AlbumDao`, `ArtistDao`, `PlaylistDao`, `CacheDao`

---

## Subsonic API Integration

Endpoints used: `ping`, `getIndexes`, `getMusicDirectory`, `getAlbumList`, `search3`, `stream`, `star`, `setRating`

Authentication: Salted MD5 token method.

Compatible servers: Navidrome, Jellyfin, Subsonic, Gonic, Funkwhale, Ampache.

---

*Document Version: 1.1 | Last Updated: March 23, 2026 | Status: Draft*
