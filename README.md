# Melos Music Player

A native Android music player built with modern Kotlin and Jetpack Compose, featuring gapless playback, 10-band equalizer, and support for local files, Subsonic servers, and SMB network shares.

## Features

- **Gapless Playback** - Sample-accurate transitions between tracks using Media3 ExoPlayer
- **10-Band Equalizer** - Built-in EQ with 20+ presets and custom preset saving
- **ReplayGain Support** - Automatic volume normalization across albums
- **Local Library** - Scan and browse your music collection by artist, album, genre, or folder
- **Smart Playlists** - Create dynamic playlists based on rules (rating, play count, date added)
- **Subsonic API** - Stream from Navidrome, Jellyfin, and other Subsonic-compatible servers
- **Offline Caching** - Download server tracks for offline playback with LRU eviction
- **SMB/CIFS** - Stream directly from NAS and network shares
- **Android Auto** - Full-featured car interface with voice search
- **Material 3** - Modern UI with dynamic theming and smooth animations

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1.x |
| UI Framework | Jetpack Compose 1.7.x |
| Architecture | Clean Architecture + MVVM |
| Audio Engine | AndroidX Media3 1.5.x |
| Dependency Injection | Hilt 2.52+ |
| Local Database | Room 2.6.x |
| Networking | Retrofit + OkHttp |
| Image Loading | Coil 3.x |
| Build System | Gradle 8.x + Version Catalogs |

## Minimum Requirements

- **Android 10+** (API 29)
- **Target SDK**: Android 15 (API 35)

## Development Status

See the documentation in `/docs` for:

- [Technical Specifications](docs/TECHNICAL_SPECS.md)
- [Implementation Plan](docs/TECH_PLAN.md)
- [Core Flows](docs/CORE_FLOWS.md)
- [Epic Brief](docs/EPIC_BRIEF.md)

## Building from Source

```bash
git clone https://github.com/Amberesaiae/melos-music-player.git
cd melos-music-player
./gradlew assembleDebug
```

## License

GPL-3.0 License

---

*Built with passion for music lovers, by music lovers.*
