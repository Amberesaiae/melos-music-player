# Architecture Documentation

This document provides a comprehensive overview of the Melos Music Player architecture, module structure, and design patterns.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Module Structure](#module-structure)
3. [Design Patterns](#design-patterns)
4. [Data Flow](#data-flow)
5. [Module Responsibilities](#module-responsibilities)
6. [Dependencies Graph](#dependencies-graph)

---

## Architecture Overview

Melos follows **Clean Architecture** principles with a clear separation of concerns across three layers:

```
┌─────────────────────────────────────────────────────────┐
│                 Presentation Layer                       │
│  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │  Jetpack Compose│  │      ViewModels            │  │
│  │      (UI)       │◄─┤   (State Management)       │  │
│  └─────────────────┘  └──────────────┬──────────────┘  │
└──────────────────────────────────────│──────────────────┘
                                       │ uses
┌──────────────────────────────────────▼──────────────────┐
│                   Domain Layer                           │
│  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │    Use Cases    │  │   Repository Interfaces    │  │
│  │  (Business Logic)│ │      (Contracts)           │  │
│  └─────────────────┘  └──────────────┬──────────────┘  │
└──────────────────────────────────────│──────────────────┘
                                       │ implements
┌──────────────────────────────────────▼──────────────────┐
│                    Data Layer                            │
│  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │   Repositories  │  │       DataSources          │  │
│  │ (Implementations)│ │  (Room, Retrofit, Player)  │  │
│  └─────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Key Principles

1. **Separation of Concerns**: Each layer has a specific responsibility
2. **Dependency Rule**: Dependencies point inward (Data → Domain → Presentation)
3. **Testability**: Business logic is isolated and easily testable
4. **Maintainability**: Clear boundaries make the codebase easier to modify

---

## Module Structure

The project is organized into 12 Gradle modules following a feature-first approach:

```
melos-music-player/
│
├── app/                          # Application module
│   ├── MelosApplication.kt       # Hilt setup, app-wide initialization
│   └── MainActivity.kt           # Compose UI host, navigation container
│
├── core/                         # Core functionality modules
│   ├── player/                   # Audio playback engine
│   ├── database/                 # Local persistence
│   ├── network/                  # Remote API client
│   ├── model/                    # Domain models
│   └── ui/                       # Shared UI components
│
├── feature/                      # Feature-specific modules
│   ├── library/                  # Local file browsing
│   ├── now-playing/              # Player interface
│   ├── playlists/                # Playlist management
│   ├── search/                   # Unified search
│   ├── server/                   # Server integration
│   └── settings/                 # App preferences
│
└── platform/                     # Platform-specific integrations
    ├── android-auto/             # Car interface
    └── notifications/            # Media notifications
```

---

## Design Patterns

### 1. Repository Pattern

The Repository pattern provides a clean API for data access, abstracting the underlying data sources.

```kotlin
// Domain layer - Interface
interface TrackRepository {
    fun getTracksFlow(): Flow<List<Track>>
    suspend fun getTrackById(id: Long): Track?
    suspend fun searchTracks(query: String): List<Track>
}

// Data layer - Implementation
class TrackRepositoryImpl @Inject constructor(
    private val localDataSource: TrackLocalDataSource,
    private val remoteDataSource: TrackRemoteDataSource,
    private val networkMonitor: NetworkMonitor
) : TrackRepository {
    
    override fun getTracksFlow(): Flow<List<Track>> {
        return networkMonitor.isOnline
            .flatMapLatest { online ->
                if (online) {
                    remoteDataSource.getTracksFlow()
                        .onEach { localDataSource.cacheTracks(it) }
                } else {
                    localDataSource.getCachedTracksFlow()
                }
            }
    }
}
```

**Benefits:**
- Single source of truth for data operations
- Easy to switch data sources (e.g., from local to remote)
- Testable with mock repositories

### 2. Use Case Pattern

Use cases encapsulate specific business logic operations.

```kotlin
class PlayTrackUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(trackId: Long, playFromStart: Boolean = false) {
        val track = trackRepository.getTrackById(trackId)
            ?: throw TrackNotFoundException(trackId)
        
        playerRepository.playTrack(track, playFromStart)
    }
}
```

**Benefits:**
- Single responsibility per use case
- Reusable across different ViewModels
- Easy to unit test

### 3. Unidirectional Data Flow (UDF)

UI follows MVI (Model-View-Intent) pattern with unidirectional data flow:

```
┌──────────┐     Events      ┌───────────┐
│   User   │ ───────────────►│ ViewModel │
│          │                 │           │
│          │◄────── State ───│           │
└──────────┘                 └───────────┘
      ▲                           │
      │                           │
      │      One-time Effects     │
      └────────────────────────────┘
```

```kotlin
// State
data class LibraryState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

// Events
sealed class LibraryEvent {
    data object Refresh : LibraryEvent()
    data class PlayTrack(val trackId: Long) : LibraryEvent()
}

// ViewModel
class LibraryViewModel @Inject constructor(
    private val tracksRepository: TracksRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    
    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.Refresh -> loadTracks()
            is LibraryEvent.PlayTrack -> playTrack(event.trackId)
        }
    }
}
```

### 4. Dependency Injection with Hilt

Hilt provides compile-time dependency injection across all modules:

```kotlin
@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MelosDatabase {
        return Room.databaseBuilder(
            context,
            MelosDatabase::class.java,
            "melos.db"
        ).build()
    }
    
    @Provides
    @Singleton
    fun provideTrackDao(database: MelosDatabase): TrackDao {
        return database.trackDao()
    }
}
```

**Module Scoping:**
- `SingletonComponent`: App-wide singletons (database, repositories)
- `ViewModelComponent`: ViewModel-scoped dependencies
- `ActivityComponent`: Activity-scoped dependencies

---

## Data Flow

### Local Library Data Flow

```
User Action (UI)
       │
       ▼
┌──────────────┐
│  ViewModel   │
│  (Feature)   │
└──────┬───────┘
       │ Use Case
       ▼
┌──────────────┐
│  Repository  │
│  (Domain)    │
└──────┬───────┘
       │
       ▼
┌──────────────┐     ┌──────────────┐
│ Local DataSource│   │Remote DataSource│
│    (Room)     │     │  (Subsonic)   │
└──────────────┘     └──────────────┘
```

### Playback Data Flow

```
NowPlayingScreen
       │
       ▼
┌──────────────┐
│ NowPlayingVM │
└──────┬───────┘
       │ StateFlow
       ▼
┌──────────────┐
│ PlayerState  │
└──────┬───────┘
       │ Events
       ▼
┌──────────────┐     ┌──────────────┐
│  MelosPlayer │────►│  Media3      │
│  (Wrapper)   │     │  ExoPlayer   │
└──────────────┘     └──────────────┘
```

### Server Sync Data Flow

```
SyncManager
       │
       ▼
┌──────────────┐
│ SubsonicClient│
│  (Retrofit)  │
└──────┬───────┘
       │ API Calls
       ▼
┌──────────────┐
│   Navidrome  │
│   / Jellyfin │
└──────────────┘
       │
       ▼
┌──────────────┐
│  MelosDatabase│
│   (Room)     │
└──────────────┘
```

---

## Module Responsibilities

### App Module (`app/`)

**Purpose:** Application entry point and composition root.

**Responsibilities:**
- Initialize Hilt dependency injection
- Host MainActivity with Compose UI
- Configure theme and navigation
- Handle deep links

**Dependencies:**
- All feature modules
- core:ui (theme, navigation)

---

### Core Modules

#### `core:player`

**Purpose:** Audio playback engine abstraction.

**Responsibilities:**
- Media3 ExoPlayer wrapper
- Gapless playback implementation
- Audio effects (equalizer, ReplayGain)
- Playback state management
- Queue management

**Key Classes:**
- `MelosPlayer`: Main player interface
- `PlaybackService`: Foreground service for background playback
- `AudioEffectManager`: Equalizer and audio processing

**Dependencies:**
- core:model
- core:database (for track metadata)

---

#### `core:database`

**Purpose:** Local data persistence layer.

**Responsibilities:**
- Room database schema definition
- DAO interfaces for data access
- Type converters for complex types
- Database migrations

**Key Classes:**
- `MelosDatabase`: Room database instance
- `TrackDao`, `AlbumDao`, `ArtistDao`: Data access objects
- `TrackEntity`, `AlbumEntity`: Database entities

**Dependencies:**
- core:model

---

#### `core:network`

**Purpose:** Remote API communication.

**Responsibilities:**
- Subsonic API client (Retrofit)
- Authentication handling
- Response/error models
- Network monitoring

**Key Classes:**
- `SubsonicApi`: Retrofit interface
- `SubsonicClient`: High-level API wrapper
- `SubsonicAuthInterceptor`: Authentication

**Dependencies:**
- core:model

---

#### `core:model`

**Purpose:** Domain model definitions.

**Responsibilities:**
- Track, Album, Artist data classes
- Playlist models
- Server configuration models
- Value objects (Duration, FileSize)

**Key Classes:**
- `Track`, `Album`, `Artist`
- `Playlist`, `PlaylistItem`
- `ServerConfig`

**Dependencies:** None (pure Kotlin module)

---

#### `core:ui`

**Purpose:** Shared UI components and theming.

**Responsibilities:**
- Material 3 theme implementation
- Common Compose components
- Navigation graph definition
- Design system tokens

**Key Classes:**
- `MelosTheme`: Theme provider
- `TrackItem`, `AlbumCard`: Reusable components
- `MelosNavHost`: Navigation container
- `Destinations`: Route definitions

**Dependencies:**
- core:model

---

### Feature Modules

#### `feature:library`

**Purpose:** Local file browsing and management.

**Responsibilities:**
- MediaStore integration
- File system scanning
- Local library display
- Folder ignore management

**ViewModel:** `LibraryViewModel`
**Screens:** `LibraryScreen`, `FoldersScreen`

---

#### `feature:now-playing`

**Purpose:** Current playback interface.

**Responsibilities:**
- Now Playing screen
- Queue management
- Player controls
- Lyrics display (future)

**ViewModel:** `NowPlayingViewModel`
**Screens:** `NowPlayingScreen`, `QueueScreen`

---

#### `feature:playlists`

**Purpose:** Playlist creation and management.

**Responsibilities:**
- Playlist CRUD operations
- Smart playlist rules
- Drag-and-drop reordering
- Export/import playlists

**ViewModel:** `PlaylistsViewModel`
**Screens:** `PlaylistsScreen`, `PlaylistDetailScreen`

---

#### `feature:search`

**Purpose:** Unified search across libraries.

**Responsibilities:**
- Local library search
- Server search (when connected)
- Search history
- Recent searches

**ViewModel:** `SearchViewModel`
**Screens:** `SearchScreen`, `SearchResultsScreen`

---

#### `feature:server`

**Purpose:** Subsonic server integration.

**Responsibilities:**
- Server configuration UI
- Sync management
- Offline cache settings
- Multi-server switching

**ViewModel:** `ServerViewModel`
**Screens:** `ServerScreen`, `ServerDetailScreen`

---

#### `feature:settings`

**Purpose:** App preferences and configuration.

**Responsibilities:**
- App settings UI
- Equalizer presets
- Playback preferences
- About screen

**ViewModel:** `SettingsViewModel`
**Screens:** `SettingsScreen`, `EqualizerScreen`

---

### Platform Modules

#### `platform:android-auto`

**Purpose:** Car interface integration.

**Responsibilities:**
- MediaBrowserService implementation
- Car-optimized UI templates
- Voice command support
- Simplified controls

**Key Classes:**
- `AndroidAutoService`: MediaBrowserService
- `CarPlaybackController`: Car-specific logic

---

#### `platform:notifications`

**Purpose:** Playback notifications.

**Responsibilities:**
- MediaSession setup
- Lock screen controls
- Notification actions
- Media button handling

**Key Classes:**
- `PlaybackNotificationManager`
- `MediaSessionCallback`

---

## Dependencies Graph

```
                                    ┌─────────────┐
                                    │     app     │
                                    └──────┬──────┘
                                           │
        ┌──────────────────────────────────┼──────────────────────────────────┐
        │                                  │                                  │
        ▼                                  ▼                                  ▼
┌───────────────┐              ┌─────────────────────┐           ┌──────────────────┐
│feature:library│              │feature:now-playing  │           │feature:playlists │
└───────┬───────┘              └──────────┬──────────┘           └────────┬─────────┘
        │                                 │                                │
        │                ┌────────────────┼────────────────┐               │
        │                │                │                │               │
        ▼                ▼                ▼                ▼               ▼
┌───────────────┐  ┌───────────┐   ┌────────────┐  ┌───────────┐   ┌──────────────┐
│core:database  │  │core:player│   │core:ui     │  │core:model │   │core:network  │
└───────────────┘  └───────────┘   └────────────┘  └───────────┘   └──────────────┘
                        │                │                              │
                        │                │                              │
                        ▼                ▼                              ▼
               ┌────────────────────────────────────────────────────────────┐
               │                   platform:notifications                   │
               └────────────────────────────────────────────────────────────┘
```

### Dependency Rules

1. **Feature modules** depend only on core modules, never on each other
2. **Core modules** have no dependencies on feature modules
3. **Platform modules** provide implementations used by core/feature modules
4. **App module** depends on all feature modules for composition

---

## Testing Strategy

### Unit Tests

**Location:** `src/test/` in each module

**Coverage:**
- ViewModels (state transformations)
- Use Cases (business logic)
- Repositories (with mock data sources)
- Utility functions

```kotlin
@Test
fun `play track emits correct state`() = runTest {
    val player = FakePlayerRepository()
    val viewModel = NowPlayingViewModel(player)
    
    viewModel.onEvent(NowPlayingEvent.Play(track))
    
    val state = viewModel.state.value
    assertThat(state.currentTrack).isEqualTo(track)
    assertThat(state.isPlaying).isTrue()
}
```

### Integration Tests

**Location:** `src/androidTest/`

**Coverage:**
- Repository implementations
- Database operations
- Network API calls (with mock server)

### UI Tests

**Location:** `src/androidTest/` in feature modules

**Coverage:**
- Critical user flows
- Navigation between screens
- State restoration

---

## Module Communication

### Event Bus Pattern

For cross-module communication without direct coupling:

```kotlin
interface EventPublisher {
    val events: Flow<UiEvent>
    fun publish(event: UiEvent)
}

// ViewModel consumes events
class LibraryViewModel @Inject constructor(
    @ForFeature("playback") private val eventPublisher: EventPublisher
) : ViewModel() {
    
    init {
        viewModelScope.launch {
            eventPublisher.events
                .filterIsInstance<PlaybackEvent.TrackStarted>()
                .collect { onTrackStarted(it.trackId) }
        }
    }
}
```

### Shared State Flow

For shared state across modules:

```kotlin
@Singleton
class PlayerStateHolder @Inject constructor() {
    val playerState: StateFlow<PlayerState> = ...
}

// Injected into any module that needs player state
class NowPlayingViewModel @Inject constructor(
    playerStateHolder: PlayerStateHolder
) : ViewModel() {
    val state = playerStateHolder.playerState
}
```

---

## Build Configuration

### Module Build Scripts

Each module uses convention plugins for consistent configuration:

```kotlin
// feature/library/build.gradle.kts
plugins {
    alias(libs.plugins.melos.android.feature)
    alias(libs.plugins.melos.android.compose)
}

android {
    namespace = "com.amberesaiae.melos.feature.library"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.ui)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

### Version Catalog

Centralized dependency management in `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.0.21"
compose = "1.7.5"
media3 = "1.5.0"
room = "2.6.1"
hilt = "2.52"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
```

---

## Future Architecture Considerations

### Modularization Opportunities

1. **Extract domain module**: Pure Kotlin module with use cases and repository interfaces
2. **Feature dynamic delivery**: On-demand feature modules for Android App Bundle
3. **Plugin architecture**: Third-party metadata provider plugins

### Potential Refactors

1. **MVI to MVVM**: Simplify state management for less complex screens
2. **Coroutine flows to StateFlow**: Standardize reactive streams
3. **Navigation component migration**: From Compose Navigation to Navigation Compose

---

## References

- [Clean Architecture by Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Android Architecture Guidelines](https://developer.android.com/topic/architecture)
- [Hilt Documentation](https://dagger.dev/hilt/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Media3 Documentation](https://developer.android.com/media/media3)
