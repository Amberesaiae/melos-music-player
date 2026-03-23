# Melos Music Player - Core Flows & User Journeys

## Overview

Complete user experience flows from first launch through daily usage.

---

## User Personas

| Persona | Tech Level | Library | Primary Use Case |
|---------|------------|---------|------------------|
| Alex (Self-Hosted) | Advanced | 25K FLAC+MP3 on Navidrome | Stream + cache for commute |
| Jamie (Local) | Intermediate | 8K MP3 on phone/SD | Local playback, EQ, playlists |
| Sam (Privacy) | Intermediate | 5K FLAC | No tracking, offline, quality |

---

## Flow 1: First-Time Onboarding (10-15 minutes)

1. **Welcome Screen** - App logo, tagline, value props, Get Started button
2. **Music Source Selection** - Local Files, Subsonic Server, SMB, Cloud (multi-select)
3. **Server Setup** - URL, username, password, Test Connection, Auto-Discover
4. **Permission Request** - Storage, Media, Network, Android Auto (optional)
5. **Library Scanning** - Progress bar, song count, background indexing
6. **Listening Preferences** - Streaming quality, caching, EQ preset
7. **Quick Tutorial** - 3-card carousel (Now Playing, Smart Playlists, Offline Mode)
8. **Home Library** - Ready to play

---

## Flow 2: Playing Music

- **Browse Library** - Artists/Albums/Songs/Playlists/Folders tabs, list/grid toggle
- **Album Detail** - Play, Shuffle, track listing, favorite
- **Now Playing** - Album art, scrubbar, controls, EQ/Lyrics/Sleep timer buttons
- **Play Queue** - Reorder via drag, remove tracks, save as playlist
- **Equalizer** - 10-band EQ, 20+ presets, Bass Boost, AutoEQ headphone profiles

---

## Flow 3: Playlists

- **Playlists List** - Auto playlists (Recently Played, Most Played) + manual
- **Create Playlist** - Name, songs, cover art
- **Smart Playlist Builder** - Rules (Genre, Year, Rating, Play Count, etc.), match ANY/ALL, limits
- **Playlist Detail** - Play, drag-reorder, add/remove songs

Example Smart Playlist:
```
Name: "80s Rock Road Trip"
Rules: Genre IS Rock AND Year BETWEEN 1980-1989 AND Rating > 3
Limit: 50 songs, Sort: Random
```

---

## Flow 4: Server Integration

- **Server List** - Status indicators (Connected/Offline/Error), multi-server
- **Server Library** - Same browse UI as local, download icon per track/album
- **Download Manager** - Active downloads with progress, completed with size/date

---

## Flow 5: Settings

| Section | Key Settings |
|---------|--------------|
| Audio | Gapless, ReplayGain, Volume Boost, Crossfade, Bit-Perfect Mode |
| Library | Sources, Scan, Excluded Folders |
| Cache | Location, Size Limit, Auto-Cache Rules, Download Quality |
| Appearance | Theme (Light/Dark/System), Dynamic Color, Grid Size |
| Connectivity | Mobile Data, Streaming Quality |
| Platform | Android Auto, Notifications |

---

## Flow 6: Search

- Real-time results with 300ms debounce
- Scope filters: Songs, Albums, Artists, Playlists
- Recent searches history + voice search
- Results grouped by type with View All links

---

## Flow 7: Android Auto

- Grid layout, large touch targets, high contrast
- Sections: Playlists, Artists, Albums, Recently Played, Servers
- Voice search only (no typing while driving)
- Large player controls (Previous, Play/Pause, Next, Like)

---

## Error Handling

| Scenario | Response |
|----------|----------|
| Server unreachable | Retry x3 with backoff, show error |
| Auth failed | Stop retrying, prompt re-auth |
| File not found | Skip to next, toast notification |
| Storage full | Pause download, notify user |
| Audio focus lost | Pause playback |

---

## Performance Targets

| Flow | Target |
|------|--------|
| Onboarding | <15 min total |
| Play Music | <500ms tap to sound |
| Create Playlist | <30s manual, <2min smart |
| Server Sync | <10s for 10K songs |
| Search | <200ms results |
| Android Auto cold start | <2s |

---

*Document Version: 1.0 | Last Updated: March 23, 2026 | Status: Draft*
