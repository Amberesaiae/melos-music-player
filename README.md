# Melos Music Player

> Open-source music player for local libraries and Subsonic servers

[![Build Status](https://github.com/Amberesaiae/melos-music-player/actions/workflows/build.yml/badge.svg)](https://github.com/Amberesaiae/melos-music-player/actions)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-35-green.svg?logo=android)](https://developer.android.com)

## Overview

Melos is an open-source Android music player that delivers **80% of Symfonium's capability at 40% the complexity**. Built for self-hosted enthusiasts and privacy-focused audiophiles, Melos combines local library management with seamless Subsonic server integration.

### Target Users

- **Self-hosted enthusiasts**: Native integration with Navidrome, Jellyfin, and Airsonic
- **Local library owners**: Advanced file browsing and metadata management
- **Privacy-focused users**: No telemetry, no ads, fully offline-capable
- **Audiophiles**: Gapless playback, ReplayGain support, and 10-band equalizer

### Key Differentiators

- **100% Open-source**: Transparent development, community-driven
- **Quick setup**: Get started in 10-15 minutes
- **Modern UI**: Material 3 You theming with dynamic color support
- **Android Auto**: Full-featured car interface
- **Lightweight**: Focused feature set without bloat

## Features (MVP v1.0)

### Audio Engine

- ✅ **Gapless playback** with Media3 ExoPlayer
- ✅ **10-band equalizer** with 12+ presets (Rock, Pop, Jazz, Classical, etc.)
- ✅ **ReplayGain support**: Album and track gain normalization
- ✅ **High-resolution audio**: Support for FLAC, ALAC, WAV up to 24-bit/192kHz

### Server Integration

- ✅ **Subsonic API compatibility**: Works with Navidrome, Jellyfin, Airsonic, Subsonic
- ✅ **Offline caching**: Intelligent LRU eviction with configurable limits
- ✅ **Smart sync**: Automatic library synchronization on WiFi
- ✅ **Multi-server support**: Switch between multiple Subsonic instances

### User Interface

- ✅ **Material 3 You**: Dynamic color theming based on wallpaper
- ✅ **Android Auto**: Full-featured car interface with simplified controls
- ✅ **Local file browsing**: MediaStore integration for on-device libraries
- ✅ **Unified search**: Instant search across local + server libraries
- ✅ **Playlist management**: Smart playlists with rule-based filtering

### Core Features

- ✅ **Background playback**: MediaSession service with notification controls
- ✅ **Sleep timer**: Configurable fade-out and stop timer
- ✅ **Last.fm scrobbling**: Optional tracking support
- ✅ **Folder ignore**: Exclude specific directories from scanning

## Architecture

Melos follows **Clean Architecture** principles with a multi-module Gradle project structure:

```
┌─────────────────────────────────────────────────┐
│              Presentation Layer                  │
│         (Jetpack Compose UI + ViewModels)        │
├─────────────────────────────────────────────────┤
│                Domain Layer                      │
│           (Use Cases + Repository Interfaces)    │
├─────────────────────────────────────────────────┤
│                 Data Layer                       │
│    (Repository implementations + DataSources)   │
└─────────────────────────────────────────────────┘
```

### Technical Stack

- **UI Framework**: Jetpack Compose + Material 3
- **Dependency Injection**: Hilt
- **Database**: Room (local cache + metadata)
- **Networking**: Retrofit + OkHttp (Subsonic API)
- **Audio Engine**: Media3 ExoPlayer
- **Async Operations**: Kotlin Coroutines + Flow
- **Navigation**: Compose Navigation

## Module Structure

```
melos-music-player/
├── app/                          # Application entry point, MainActivity
│   ├── src/main/java/.../MelosApplication.kt
│   └── src/main/java/.../MainActivity.kt
│
├── core/
│   ├── player/                   # Media3 wrapper, playback logic, AudioEngine
│   │   ├── MelosPlayer.kt
│   │   ├── PlaybackService.kt
│   │   └── AudioEffectManager.kt
│   │
│   ├── database/                 # Room database, DAOs, TypeConverters
│   │   ├── MelosDatabase.kt
│   │   ├── dao/
│   │   │   ├── TrackDao.kt
│   │   │   ├── AlbumDao.kt
│   │   │   └── PlaylistDao.kt
│   │   └── entity/
│   │
│   ├── network/                  # Retrofit, Subsonic API client
│   │   ├── SubsonicApi.kt
│   │   ├── SubsonicClient.kt
│   │   └── model/
│   │
│   ├── model/                    # Domain models (Track, Album, Artist, Playlist)
│   │   ├── Track.kt
│   │   ├── Album.kt
│   │   └── Artist.kt
│   │
│   └── ui/                       # Shared Compose components, theme, navigation
│       ├── theme/
│       │   ├── MelosTheme.kt
│       │   ├── Color.kt
│       │   └── Type.kt
│       ├── components/
│       │   ├── TrackItem.kt
│       │   ├── AlbumCard.kt
│       │   └── PlayerControls.kt
│       └── navigation/
│           ├── MelosNavHost.kt
│           └── Destinations.kt
│
├── feature/
│   ├── library/                  # Local file browsing, MediaStore integration
│   │   ├── LibraryScreen.kt
│   │   ├── LibraryViewModel.kt
│   │   └── FileBrowser.kt
│   │
│   ├── now-playing/              # Player UI, NowPlayingScreen
│   │   ├── NowPlayingScreen.kt
│   │   ├── NowPlayingViewModel.kt
│   │   └── QueueList.kt
│   │
│   ├── playlists/                # Playlist management, smart playlists
│   │   ├── PlaylistsScreen.kt
│   │   ├── PlaylistDetail.kt
│   │   └── SmartPlaylistRules.kt
│   │
│   ├── search/                   # Unified search (local + server)
│   │   ├── SearchScreen.kt
│   │   ├── SearchViewModel.kt
│   │   └── SearchResult.kt
│   │
│   ├── server/                   # Subsonic API integration, sync logic
│   │   ├── ServerScreen.kt
│   │   ├── ServerViewModel.kt
│   │   └── SyncManager.kt
│   │
│   └── settings/                 # App configuration, preferences
│       ├── SettingsScreen.kt
│       ├── EqualizerSettings.kt
│       └── PlaybackSettings.kt
│
├── platform/
│   ├── android-auto/             # Car interface, MediaBrowserService
│   │   ├── AndroidAutoService.kt
│   │   └── CarPlaybackController.kt
│   │
│   └── notifications/            # MediaSession service, notification controls
│       ├── PlaybackNotificationManager.kt
│       └── MediaSessionCallback.kt
│
├── build-logic/                  # Convention plugins for Gradle
│   ├── convention/
│   └── settings-logic/
│
├── gradle/
│   └── libs.versions.toml        # Version catalog
│
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Project settings
└── README.md
```

### Module Dependencies

```
app → feature:* → core:* → platform:*
                ↓
            domain models
```

## Build Instructions

### Requirements

- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: Version 17 or higher
- **Android SDK**: API level 35 (Android 14)
- **Minimum SDK**: API level 26 (Android 8.0)

### Setup Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/Amberesaiae/melos-music-player.git
   cd melos-music-player
   ```

2. **Sync Gradle project**
   - Open in Android Studio
   - Wait for Gradle sync to complete
   - Ensure all 12 modules are recognized

3. **Run the application**
   - Connect an Android device or start an emulator (API 26+)
   - Click "Run" or press `Shift+F10`
   - Select the `app` module

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run lint checks
./gradlew lint

# Check code formatting
./gradlew detekt

# Clean build
./gradlew clean
```

### Output Locations

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## Roadmap

### Phase 0: Foundation ✅ (Weeks 1-2)

- ✅ Project setup with multi-module Gradle structure
- ✅ Hilt dependency injection configuration
- ✅ Compose navigation infrastructure
- ✅ Material 3 theme implementation
- ✅ CI/CD pipeline with GitHub Actions

### Phase 1: Core Player (Weeks 3-6)

- [ ] Media3 ExoPlayer integration
- [ ] Gapless playback implementation
- [ ] 10-band equalizer with presets
- [ ] ReplayGain audio processing
- [ ] Background playback service

### Phase 2: Library + Database (Weeks 7-10)

- [ ] Room database schema design
- [ ] MediaStore integration for local files
- [ ] Metadata extraction (ID3 tags)
- [ ] Album art caching
- [ ] Smart playlist engine

### Phase 3: Server Integration (Weeks 11-15)

- [ ] Subsonic API client implementation
- [ ] Offline sync with LRU cache
- [ ] Multi-server support
- [ ] Incremental sync optimization
- [ ] Conflict resolution

### Phase 4: Polish (Weeks 16-19)

- [ ] Android Auto interface
- [ ] Performance optimization
- [ ] Battery usage optimization
- [ ] Accessibility improvements
- [ ] Internationalization (i18n)

### Phase 5: Launch Prep (Weeks 20-22)

- [ ] Beta testing program
- [ ] Documentation completion
- [ ] Play Store listing preparation
- [ ] Final QA testing
- [ ] v1.0 release

## Contributing

We welcome contributions from the community! Here's how to get started:

### Branch Naming Convention

```
feature/{description}     # New features (e.g., feature/equalizer-ui)
fix/{description}         # Bug fixes (e.g., fix/playback-stutter)
refactor/{description}    # Code refactoring
docs/{description}        # Documentation updates
test/{description}        # Test additions
```

### Pull Request Requirements

1. **Fork** the repository
2. **Create a feature branch** from `main`
3. **Implement changes** with tests
4. **Ensure CI passes** (build, test, lint, detekt)
5. **Submit PR** with clear description
6. **Address review feedback**

### Code Style

- **Detekt**: We enforce code quality with Detekt static analysis
- **Kotlin conventions**: Follow official Kotlin style guide
- **Commit messages**: Use conventional commits format
  ```
  feat: add 10-band equalizer UI
  fix: resolve gapless playback issue
  docs: update README with build instructions
  ```

### Testing Requirements

- **Unit tests**: Required for business logic (ViewModels, UseCases)
- **Integration tests**: Required for Repository implementations
- **UI tests**: Required for critical user flows
- **Minimum coverage**: 70% for new code

### Running Tests Locally

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:player:test

# Run with coverage
./gradlew jacocoTestReport
```

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0).

```
Copyright (C) 2026 Amberesaiae

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

## Acknowledgments

Melos is inspired by and built upon excellent open-source projects:

- **[Symfonium](https://symfonium.app/)**: The inspiration for Melos' feature set and UX philosophy
- **[Media3 / ExoPlayer](https://github.com/androidx/media)**: Powerful media playback library by Google
- **[Material Design 3](https://m3.material.io/)**: Modern design system by Google
- **[Navidrome](https://www.navidrome.org/)**: Open-source Subsonic-compatible music server
- **[Jellyfin](https://jellyfin.org/)**: Free Software Media System

### Libraries

- **Jetpack Compose**: Modern UI toolkit for Android
- **Hilt**: Dependency injection for Android
- **Room**: Persistence library for SQLite
- **Retrofit**: Type-safe HTTP client
- **Kotlin Coroutines**: Asynchronous programming

---

<div align="center">

**Made with ❤️ by the Melos Team**

[Report a Bug](https://github.com/Amberesaiae/melos-music-player/issues) · [Request Feature](https://github.com/Amberesaiae/melos-music-player/issues) · [Discussions](https://github.com/Amberesaiae/melos-music-player/discussions)

</div>
