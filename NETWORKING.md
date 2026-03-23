# Melos Music Player - Networking & System Architecture

## Phase 3 Integration Overview

This document provides comprehensive documentation for the networking architecture, module structure, and API integration patterns implemented in Melos Music Player Phase 3.

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Module Overview](#module-overview)
3. [API Integration Guide](#api-integration-guide)
4. [Authentication Module](#authentication-module)
5. [Download Manager](#download-manager)
6. [Player Enhancements](#player-enhancements)
7. [Usage Examples](#usage-examples)
8. [Dependency Matrix](#dependency-matrix)

---

## System Architecture

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Melos Music Player                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │   Login     │  │   Library   │  │   Player    │              │
│  │   Screen    │  │   Screen    │  │   Screen    │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                      │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐              │
│  │   Login     │  │   Library   │  │   Melos     │              │
│  │   ViewModel │  │   ViewModel │  │   Player    │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                      │
│  ┌──────▼────────────────▼────────────────▼──────┐              │
│  │              RemoteMediaSource                 │              │
│  │   (Subsonic & Jellyfin Media Item Creation)   │              │
│  └────────────────────┬──────────────────────────┘              │
│                       │                                          │
│  ┌────────────────────▼──────────────────────────┐              │
│  │           StreamingDataSource                  │              │
│  │      (HTTP Range Requests, OkHttp Integration)│              │
│  └────────────────────┬──────────────────────────┘              │
│                       │                                          │
│  ┌────────────────────▼──────────────────────────┐              │
│  │   NetworkState Monitor (ConnectivityManager)  │              │
│  │   - WiFi/Cellular Detection                   │              │
│  │   - Bandwidth Estimation                      │              │
│  │   - Quality Adaptation                        │              │
│  └───────────────────────────────────────────────┘              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Layered Architecture

```
┌─────────────────────────────────────────┐
│           Presentation Layer            │
│  (Jetpack Compose UI, ViewModels)       │
├─────────────────────────────────────────┤
│              Domain Layer               │
│    (Use Cases, Repository Interfaces)   │
├─────────────────────────────────────────┤
│               Data Layer                │
│  (Repositories, Data Sources, Models)   │
├─────────────────────────────────────────┤
│            Network Layer                │
│   (Retrofit, OkHttp, API Services)      │
└─────────────────────────────────────────┘
```

---

## Module Overview

### Project Structure

```
melos-music-player/
├── app/                          # Main application module
├── core/
│   ├── player/                   # Media playback (ExoPlayer/Media3)
│   ├── database/                 # Room database, DAOs
│   ├── network/                  # Retrofit, API services
│   ├── model/                    # Data classes, entities
│   └── ui/                       # Compose themes, components
├── feature/
│   ├── authentication/           # Login, credentials, biometric
│   ├── downloads-ui/             # Download queue, foreground service
│   ├── library/                  # Library browsing
│   ├── now-playing/              # Now playing screen
│   ├── playlists/                # Playlist management
│   ├── search/                   # Search functionality
│   ├── server/                   # Server configuration
│   └── settings/                 # App settings
└── platform/
    ├── auto-android/             # Android Auto support
    └── notifications/            # Notification management
```

### Module Dependencies

| Module | Dependencies | Purpose |
|--------|-------------|---------|
| `:core:player` | Media3 ExoPlayer, OkHttp, Hilt | Media playback with adaptive streaming |
| `:core:network` | Retrofit, OkHttp, Kotlinx Serialization | API communication with Subsonic/Jellyfin |
| `:core:model` | Kotlinx Serialization | Data models for songs, albums, artists |
| `:feature:authentication` | Security Crypto, Biometric, DataStore | Secure credential storage and biometric auth |
| `:feature:downloads-ui` | WorkManager, Foreground Service | Download management with parallel downloads |

---

## API Integration Guide

### Subsonic API Integration

#### Authentication

Subsonic uses token-based authentication with salt and MD5 hashing:

```kotlin
// Token Generation (from RemoteMediaSource.kt)
val salt = AuthUtils.generateSalt()
val token = AuthUtils.generateSubsonicToken(salt, password)

// Token = MD5(password + salt)
```

#### Stream URL Construction

```kotlin
private fun buildSubsonicStreamUrl(
    songId: String,
    username: String,
    salt: String,
    token: String
): String {
    return StringBuilder().apply {
        append("http://192.168.1.100:4040/rest/stream.view")
        append("?u=$username")
        append("&s=$salt")
        append("&t=$token")
        append("&v=1.16.1")        // API version
        append("&c=MelosPlayer")   // Client name
        append("&f=json")          // Response format
        append("&id=$songId")
    }.toString()
}
```

#### Artwork URL Construction

```kotlin
private fun buildSubsonicArtworkUrl(
    songId: String,
    username: String,
    salt: String,
    token: String
): String {
    return StringBuilder().apply {
        append("http://192.168.1.100:4040/rest/getCoverArt.view")
        append("?u=$username")
        append("&s=$salt")
        append("&t=$token")
        append("&v=1.16.1")
        append("&c=MelosPlayer")
        append("&f=json")
        append("&id=$songId")
    }.toString()
}
```

#### Key Subsonic Endpoints

| Endpoint | Purpose | Parameters |
|----------|---------|------------|
| `/rest/stream.view` | Audio streaming | `id`, `u`, `s`, `t`, `v`, `c`, `f` |
| `/rest/getCoverArt.view` | Album artwork | `id`, `u`, `s`, `t`, `v`, `c`, `f` |
| `/rest/getSongs.view` | List songs | `u`, `s`, `t`, `v`, `c`, `f` |
| `/rest/getArtist.view` | Artist details | `id`, `u`, `s`, `t`, `v`, `c`, `f` |
| `/rest/getAlbum.view` | Album details | `id`, `u`, `s`, `t`, `v`, `c`, `f` |

---

### Jellyfin API Integration

#### Authentication

Jellyfin uses API key-based authentication:

```kotlin
// Access token from login response
val accessToken: String // Obtained from /Users/AuthenticateByName
```

#### Stream URL Construction

```kotlin
private fun buildJellyfinStreamUrl(
    itemId: String,
    accessToken: String
): String {
    return "http://192.168.1.100:8096/Audio/$itemId/stream" +
        "?UserId={userId}" +
        "&DeviceId={deviceId}" +
        "&api_key=$accessToken"
}
```

#### Artwork URL Construction

```kotlin
private fun buildJellyfinArtworkUrl(
    itemId: String,
    accessToken: String
): String {
    return "http://192.168.1.100:8096/Items/$itemId/Images/Primary" +
        "?api_key=$accessToken"
}
```

#### Key Jellyfin Endpoints

| Endpoint | Purpose | Parameters |
|----------|---------|------------|
| `/Audio/{itemId}/stream` | Audio streaming | `UserId`, `DeviceId`, `api_key` |
| `/Items/{itemId}/Images/Primary` | Album artwork | `api_key` |
| `/Users/AuthenticateByName` | User authentication | `Username`, `Pw` |
| `/Items` | Item search | `api_key`, `IncludeItemTypes`, `SearchTerm` |

---

## Authentication Module

### Secure Credential Storage

The authentication module uses Android's EncryptedSharedPreferences with hardware-backed encryption:

```kotlin
// From CredentialManager.kt
private val masterKey: MasterKey by lazy {
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
}

private val encryptedPrefs: SharedPreferences by lazy {
    EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

### Biometric Authentication

```kotlin
// BiometricPrompt configuration
val biometricPrompt = BiometricPrompt(activity, executor, callback)
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Authenticate to Login")
    .setSubtitle("Use your biometric credential to log in")
    .setNegativeButtonText("Use account password")
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .build()
```

### Data Models

```kotlin
// Credentials data class
data class Credentials(
    val username: String,
    val password: String,  // Encrypted in storage
    val serverUrl: String,
    val serverType: ServerType,  // SUBSONIC or JELLYFIN
    val accessToken: String? = null
)

// Server configuration
data class ServerConfig(
    val url: String,
    val type: ServerType,
    val name: String = "My Server"
)

// Biometric state
data class BiometricState(
    val isEnabled: Boolean = false,
    val isAvailable: Boolean = false,
    val isStrong: Boolean = false
)
```

### LoginViewModel Flow

```
┌─────────────────┐
│  User Input     │
│  (Username,     │
│   Password,     │
│   Server URL)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Input          │
│  Validation     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Server         │
│  Connection     │
│  Test           │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Credential     │
│  Storage        │
│  (Encrypted)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Auto-Login     │
│  / Biometric    │
│  Setup          │
└─────────────────┘
```

---

## Download Manager

### Foreground Service Architecture

```kotlin
// From DownloadService.kt
class DownloadService : LifecycleService() {
    
    // Parallel download configuration
    companion object {
        private const val MAX_PARALLEL_DOWNLOADS = 3
    }
    
    // Download queue management
    private val downloadQueue = Channel<DownloadTask>(Channel.UNLIMITED)
    private val activeDownloads = ConcurrentHashMap<String, DownloadJob>()
    
    // Service binding for ViewModel access
    private val binder = LocalBinder()
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}
```

### Download States

```kotlin
enum class DownloadStatus {
    QUEUED,      // Waiting in queue
    DOWNLOADING, // Actively downloading
    PAUSED,      // Temporarily paused
    COMPLETED,   // Successfully completed
    FAILED,      // Download failed
    CANCELLED    // User cancelled
}
```

### Progress Tracking

```kotlin
data class DownloadItem(
    val id: String,
    val songId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val filePath: String? = null,
    val errorMessage: String? = null
) {
    val progress: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
        } else 0
}
```

### Parallel Download Implementation

```kotlin
// Coroutine-based parallel downloads
private val downloadScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob()
)

private fun startParallelDownloads() {
    repeat(MAX_PARALLEL_DOWNLOADS) { workerId ->
        downloadScope.launch {
            for (task in downloadQueue) {
                processDownload(task)
            }
        }
    }
}
```

---

## Player Enhancements

### Network-Aware Playback

The player adapts streaming quality based on network conditions:

```kotlin
// Network quality levels
enum class NetworkQuality {
    WIFI,           // Unlimited bandwidth
    CELLULAR_GOOD,  // > 5 Mbps
    CELLULAR_POOR,  // < 5 Mbps
    OFFLINE         // No connection
}

// Streaming quality presets
enum class StreamingQuality(val bitrate: Int, val description: String) {
    LOW(96, "Low (96 kbps)"),
    MEDIUM(128, "Medium (128 kbps)"),
    HIGH(192, "High (192 kbps)"),
    ORIGINAL(-1, "Original")
    
    companion object {
        fun forNetworkQuality(quality: NetworkQuality): StreamingQuality {
            return when (quality) {
                NetworkQuality.WIFI -> ORIGINAL
                NetworkQuality.CELLULAR_GOOD -> HIGH
                NetworkQuality.CELLULAR_POOR -> MEDIUM
                NetworkQuality.OFFLINE -> LOW
            }
        }
    }
}
```

### Buffer Configuration

```kotlin
data class BufferConfig(
    val streamBufferMs: Long = 15000L,    // 15 seconds
    val prefetchBufferMs: Long = 30000L,  // 30 seconds
    val backBufferMs: Long = 10000L,      // 10 seconds
    val minBufferMs: Long = 5000L,        // 5 seconds
    val maxBufferMs: Long = 60000L        // 60 seconds
)

// Network-aware buffer adjustment
fun getNetworkAwareBufferConfig(networkQuality: NetworkQuality): BufferConfig {
    return when (networkQuality) {
        NetworkQuality.WIFI -> BufferConfig(
            streamBufferMs = 20000L,
            prefetchBufferMs = 45000L,
            backBufferMs = 15000L,
            minBufferMs = 8000L,
            maxBufferMs = 90000L
        )
        NetworkQuality.CELLULAR_GOOD -> BufferConfig(
            streamBufferMs = 15000L,
            prefetchBufferMs = 30000L,
            backBufferMs = 10000L,
            minBufferMs = 5000L,
            maxBufferMs = 60000L
        )
        NetworkQuality.CELLULAR_POOR -> BufferConfig(
            streamBufferMs = 10000L,
            prefetchBufferMs = 20000L,
            backBufferMs = 5000L,
            minBufferMs = 3000L,
            maxBufferMs = 40000L
        )
        NetworkQuality.OFFLINE -> BufferConfig(
            streamBufferMs = 5000L,
            prefetchBufferMs = 0L,
            backBufferMs = 0L,
            minBufferMs = 1000L,
            maxBufferMs = 15000L
        )
    }
}
```

### Streaming Data Source

Custom DataSource implementation for HTTP range requests:

```kotlin
class StreamingDataSource(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cache: DataSource? = null
) : DataSource {
    
    override fun open(dataSpec: DataSource.DataSpec): Long {
        // Build HTTP request with Range header for seeking support
        val request = buildRequest(dataSpec)
        responseCall = okHttpClient.newCall(request)
        val response = responseCall!!.execute()
        
        // Handle HTTP errors
        if (!response.isSuccessful) {
            throw StreamingDataSourceException("HTTP error: ${response.code}", response.code)
        }
        
        responseStream = response.body!!.byteStream()
        return bytesRemaining
    }
    
    private fun buildRequest(dataSpec: DataSource.DataSpec): Request {
        val requestBuilder = Request.Builder()
            .url(uri.toString())
        
        // Add Range header for seeking
        if (dataSpec.position != 0L || dataSpec.length != -1L) {
            val from = dataSpec.position
            val to = if (dataSpec.length != -1L) {
                dataSpec.position + dataSpec.length - 1
            } else ""
            requestBuilder.addHeader("Range", "bytes=$from-$to")
        }
        
        // No caching for streaming
        requestBuilder.cacheControl(CacheControl.Builder().noStore().build())
        requestBuilder.addHeader("User-Agent", "MelosPlayer/1.0")
        
        return requestBuilder.build()
    }
}
```

---

## Usage Examples

### Example 1: Login with Biometric Authentication

```kotlin
@Composable
fun LoginExample(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    
    LoginScreen(
        username = state.username,
        onUsernameChange = viewModel::onUsernameChange,
        password = state.password,
        onPasswordChange = viewModel::onPasswordChange,
        serverType = state.serverType,
        onServerTypeChange = viewModel::onServerTypeChange,
        serverUrl = state.serverUrl,
        onServerUrlChange = viewModel::onServerUrlChange,
        isAutoLoginEnabled = state.isAutoLoginEnabled,
        onAutoLoginEnabledChange = viewModel::onAutoLoginEnabledChange,
        isBiometricEnabled = state.isBiometricEnabled,
        onBiometricEnabledChange = viewModel::onBiometricEnabledChange,
        connectionStatus = state.connectionStatus,
        onBiometricLogin = viewModel::authenticateWithBiometric,
        onLogin = {
            viewModel.login()
        },
        isLoading = state.isLoading,
        error = state.error
    )
}
```

### Example 2: Play Remote Song

```kotlin
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val melosPlayer: MelosPlayer,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {
    
    suspend fun playSong(song: Song, serverType: String, accessToken: String?) {
        melosPlayer.playRemoteSong(
            song = song,
            serverType = serverType,  // "subsonic" or "jellyfin"
            accessToken = accessToken
        )
    }
    
    // Network-aware quality adjustment happens automatically
    val networkQuality: StateFlow<NetworkQuality> = melosPlayer.networkState
        .map { it.networkQuality }
        .stateIn(viewModelScope, SharingStarted.Lazily, NetworkQuality.OFFLINE)
}
```

### Example 3: Start Download

```kotlin
@Composable
fun DownloadQueueExample(viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    
    DownloadQueueScreen(
        activeDownloads = state.activeDownloads,
        completedDownloads = state.completedDownloads,
        onPauseDownload = { id ->
            viewModel.pauseDownload(id)
        },
        onResumeDownload = { id ->
            viewModel.resumeDownload(id)
        },
        onCancelDownload = { id ->
            viewModel.cancelDownload(id)
        },
        onDeleteCompleted = { id ->
            viewModel.deleteCompletedDownload(id)
        },
        onPauseAll = viewModel::pauseAllDownloads,
        onResumeAll = viewModel::resumeAllDownloads
    )
}
```

### Example 4: Network State Observation

```kotlin
@Composable
fun NetworkStateObserver(viewModel: NowPlayingViewModel) {
    val networkQuality by viewModel.networkQuality.collectAsState()
    
    // Display network quality indicator
    when (networkQuality) {
        NetworkQuality.WIFI -> {
            Icon(Icons.Default.Wifi, contentDescription = "WiFi")
            Text("Streaming at original quality")
        }
        NetworkQuality.CELLULAR_GOOD -> {
            Icon(Icons.Default.NetworkWifi, contentDescription = "Cellular Good")
            Text("Streaming at high quality (192 kbps)")
        }
        NetworkQuality.CELLULAR_POOR -> {
            Icon(Icons.Default.NetworkCell, contentDescription = "Cellular Poor")
            Text("Streaming at medium quality (128 kbps)")
        }
        NetworkQuality.OFFLINE -> {
            Icon(Icons.Default.WifiOff, contentDescription = "Offline")
            Text("Playback offline or cached")
        }
    }
}
```

---

## Dependency Matrix

### Core Module Dependencies

| Module | Media3 | OkHttp | Retrofit | Hilt | Coroutines | Compose |
|--------|--------|--------|----------|------|------------|---------|
| `:core:player` | ✓ 1.5.1 | ✓ 4.12.0 | - | ✓ 2.55 | ✓ 1.10.1 | - |
| `:core:network` | - | ✓ 4.12.0 | ✓ 2.11.0 | ✓ 2.55 | ✓ 1.10.1 | - |
| `:core:model` | - | - | - | - | ✓ 1.10.1 | - |
| `:core:ui` | - | - | - | ✓ 2.55 | ✓ 1.10.1 | ✓ 1.7.0 |
| `:feature:authentication` | - | - | - | ✓ 2.55 | ✓ 1.10.1 | ✓ 1.7.0 |
| `:feature:downloads-ui` | - | ✓ 4.12.0 | - | ✓ 2.55 | ✓ 1.10.1 | ✓ 1.7.0 |

### Version Catalog (libs.versions.toml)

```toml
[versions]
androidx-media3 = "1.5.1"
okhttp = "4.12.0"
retrofit = "2.11.0"
hilt = "2.55"
kotlinx-coroutines = "1.10.1"
compose = "1.7.0"
kotlinx-serialization = "1.6.3"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "androidx-media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "androidx-media3" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
kotlinx-coroutines = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
```

---

## Security Considerations

### Credential Encryption

- Passwords are encrypted using AES256-GCM
- Encryption keys stored in Android Keystore (hardware-backed)
- Biometric authentication required for credential access (optional)

### Network Security

- HTTPS enforced for all external communications
- Certificate pinning recommended for production
- No credentials logged or transmitted in plain text

### Token Management

- Subsonic: Token = MD5(password + salt), regenerated per session
- Jellyfin: Access token stored encrypted, refreshed on expiration

---

## Troubleshooting

### Common Issues

#### 1. Connection Test Fails

**Symptoms:** Server connection test returns error despite valid credentials

**Solutions:**
- Verify server URL includes protocol (http:// or https://)
- Check firewall settings on server (default ports: Subsonic 4040, Jellyfin 8096)
- Ensure API version compatibility (Subsonic 1.16.1+, Jellyfin 10.8+)

#### 2. Biometric Authentication Not Available

**Symptoms:** Biometric option disabled or not shown

**Solutions:**
- Verify device has biometric sensors (fingerprint, face)
- Check `BiometricManager.canAuthenticate()` result
- Ensure app has appropriate permissions in manifest

#### 3. Download Service Stops Unexpectedly

**Symptoms:** Downloads pause or fail mid-download

**Solutions:**
- Check foreground service notification is visible
- Verify battery optimization is disabled for the app
- Ensure STORAGE permission is granted (Android 12+)

#### 4. Playback Stalls on Poor Network

**Symptoms:** Frequent buffering or playback interruptions

**Solutions:**
- Check `NetworkState` for current bandwidth estimate
- Manually reduce streaming quality in settings
- Increase buffer sizes in `BufferConfig`

---

## API Reference

### RemoteMediaSource

```kotlin
class RemoteMediaSource @Inject constructor(
    private val context: Context,
    private val subsonicApiService: SubsonicApiService,
    private val jellyfinApiService: JellyfinApiService
) {
    suspend fun createSubsonicMediaItem(songId: String, song: Song): MediaItem
    suspend fun createJellyfinMediaItem(itemId: String, song: Song, accessToken: String): MediaItem
    fun getNetworkAwareBufferConfig(networkQuality: NetworkQuality): BufferConfig
}
```

### MelosPlayer

```kotlin
@Singleton
class MelosPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val remoteMediaSource: RemoteMediaSource
) {
    suspend fun playRemoteSong(song: Song, serverType: String, accessToken: String? = null)
    fun setNextTrack(song: Song, serverType: String, accessToken: String? = null)
    
    val playbackState: StateFlow<PlayerState>
    val networkState: StateFlow<NetworkState>
    val currentQuality: StateFlow<StreamingQuality>
    
    fun play()
    fun pause()
    fun seekTo(position: Long)
    fun stop()
    fun release()
}
```

### CredentialManager

```kotlin
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun saveCredentials(credentials: Credentials)
    suspend fun getCredentials(): Credentials?
    suspend fun clearCredentials()
    suspend fun enableBiometric()
    suspend fun disableBiometric()
    suspend fun getBiometricState(): BiometricState
}
```

---

## Contributing

### Adding New Server Types

1. Create new API service interface in `:core:network`
2. Add server type enum value in `ServerType`
3. Implement media item creation in `RemoteMediaSource`
4. Add authentication flow in `LoginViewModel`
5. Update documentation in this file

### Adding New Download Features

1. Extend `DownloadStatus` enum if needed
2. Add new methods to `DownloadService`
3. Update `DownloadQueueScreen` UI components
4. Modify `DownloadsViewModel` state management
5. Test with various network conditions

---

## License

Copyright © 2024 Amberesaiae. All rights reserved.
