package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.model.Song
import com.example.playback.PulseMediaSessionService
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.AccentOption
import com.example.ui.theme.PulseMusicTheme
import com.example.ui.theme.PulseTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.*
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private val viewModel: PulseMusicViewModel by viewModels {
        PulseMusicViewModel.Factory((application as PulseApplication).appContainer)
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            PulseMusicTheme(
                themeMode = ThemeMode.fromName(settings.themeMode),
                accent = AccentOption.fromName(settings.accent),
                animationIntensity = settings.animationIntensity
            ) {
                // Light status/navigation icons on dark themes, dark icons on the light theme.
                val isDark = PulseTheme.colors.isDark
                val view = androidx.compose.ui.platform.LocalView.current
                SideEffect {
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
                PulseApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            viewModel.openNowPlaying()
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
        }
    }

    override fun onStart() {
        super.onStart()
        // Binding a controller starts the MediaSessionService, which owns the media notification,
        // lock-screen controls and headset button handling.
        val token = SessionToken(this, ComponentName(this, PulseMediaSessionService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.example.pulse.OPEN_NOW_PLAYING"
    }
}

private fun Context.hasAudioPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openOutputSwitcher() {
    val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(panel) }.onFailure { openBluetoothSettings() }
}

private fun Context.openBluetoothSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PulseApp(vm: PulseMusicViewModel) {
    val context = LocalContext.current
    val colors = PulseTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.libraryData.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val audioLab by vm.audioLabState.collectAsStateWithLifecycle()
    val tab by vm.currentTab.collectAsStateWithLifecycle()
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val playlists by vm.userPlaylists.collectAsStateWithLifecycle()
    val homeFeed by vm.homeFeed.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()

    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.toasts.collect { toast = it } }

    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = context.hasAudioPermission()
        vm.completeOnboarding()
        vm.refreshLibrary()
    }
    fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionLauncher.launch(perms)
    }

    // Re-check permission and rescan when returning to the app (e.g. after granting it in Settings).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = context.hasAudioPermission()
                if (granted != hasPermission || (granted && library.songs.isEmpty())) vm.refreshLibrary()
                hasPermission = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var sheetSong by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    // Keep the display awake while the full player, lyrics or car mode is open and music is playing.
    val hostView = androidx.compose.ui.platform.LocalView.current
    val playerVisible = overlays.lastOrNull().let { it is Overlay.NowPlaying || it is Overlay.Lyrics || it is Overlay.CarMode }
    val keepAwake = settings.keepScreenOn && playback.isPlaying && playerVisible
    DisposableEffect(keepAwake) {
        hostView.keepScreenOn = keepAwake
        onDispose { hostView.keepScreenOn = false }
    }
    var searchAutoFocus by remember { mutableStateOf(false) }

    LaunchedEffect(tab) { if (tab != NavigationTab.SEARCH) searchAutoFocus = false }

    BackHandler(enabled = overlays.isNotEmpty() || tab != NavigationTab.HOME) { vm.back() }

    val shareSong: (Song) -> Unit = { song -> vm.shareSong(context, song, colors.accent.toArgb(), colors.accentPartner.toArgb()) }

    CompositionLocalProvider(LocalSongActions provides SongActions { sheetSong = it }) {
        AuroraBackground(Modifier.fillMaxSize()) {
            if (!settings.onboardingDone && !hasPermission) {
                OnboardingScreen(onGrantAccess = ::requestPermissions, onSkip = { vm.completeOnboarding() })
                return@AuroraBackground
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                val sidePaneWidth = if (maxWidth >= 1200.dp) 440.dp else 380.dp
                val showSidePane = maxWidth >= 900.dp && playback.currentSong != null
                val density = LocalDensity.current
                val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                val miniVisible = playback.currentSong != null && !showSidePane
                val bottomChrome = navBottom + (if (!useRail) 84.dp else 0.dp) + (if (miniVisible) 76.dp else 0.dp) + 16.dp
                val contentPadding = PaddingValues(top = statusTop + 8.dp, bottom = bottomChrome)

                Row(Modifier.fillMaxSize()) {
                    if (useRail) {
                        PulseNavRail(tab, { vm.selectTab(it) }, Modifier.statusBarsPadding().navigationBarsPadding())
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(targetState = tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tabs") { current ->
                            when (current) {
                                NavigationTab.HOME -> HomeScreen(
                                    feed = homeFeed,
                                    stats = stats,
                                    playbackState = playback,
                                    outputDevice = audioLab.outputDevice,
                                    onPlaySong = { s, list -> vm.playSong(s, list) },
                                    onPlayPause = vm::togglePlayPause,
                                    onOpenNowPlaying = vm::openNowPlaying,
                                    onOpenSearch = { searchAutoFocus = true; vm.selectTab(NavigationTab.SEARCH) },
                                    onPlaySmart = { vm.playSmart(it) },
                                    onOpenSmart = { vm.openDetail(DetailTarget.SmartTarget(it)) },
                                    onSurpriseMe = { vm.surpriseMe() },
                                    onOpenStats = { vm.open(Overlay.Stats) },
                                    onOpenDevices = { vm.open(Overlay.AudioLab(2)) },
                                    contentPadding = contentPadding,
                                    libraryEmpty = library.songs.isEmpty(),
                                    hasPermission = hasPermission,
                                    onRequestAccess = ::requestPermissions,
                                    onRescan = { vm.refreshLibrary(announce = true) }
                                )
                                NavigationTab.LIBRARY -> LibraryScreen(
                                    library = library,
                                    playbackState = playback,
                                    onPlaySong = { s, list -> vm.playSong(s, list) },
                                    onShuffleAll = { vm.playSongs(it, shuffle = true) },
                                    onOpenDetail = vm::openDetail,
                                    contentPadding = contentPadding
                                )
                                NavigationTab.SEARCH -> SearchScreen(
                                    library = library,
                                    playbackState = playback,
                                    recentSearches = recentSearches,
                                    onPlaySong = { s, list -> vm.playSong(s, list) },
                                    onOpenDetail = vm::openDetail,
                                    onSubmitSearch = { vm.addRecentSearch(it) },
                                    onClearRecent = { vm.clearRecentSearches() },
                                    contentPadding = contentPadding,
                                    autoFocus = searchAutoFocus
                                )
                                NavigationTab.PLAYLISTS -> PlaylistsScreen(
                                    playlists = playlists,
                                    likedCount = library.songs.count { it.isFavorite },
                                    onOpenDetail = vm::openDetail,
                                    onCreatePlaylist = { vm.createPlaylist(it) },
                                    contentPadding = contentPadding
                                )
                                NavigationTab.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    library = library,
                                    callbacks = SettingsCallbacks(
                                        onThemeMode = { vm.setThemeMode(it) },
                                        onAccent = { vm.setAccent(it) },
                                        onAnimationIntensity = { vm.setAnimationIntensity(it) },
                                        onSkipSilence = { vm.setSkipSilence(it) },
                                        onSleepFade = { vm.setSleepFade(it) },
                                        onLyricsScale = { vm.setLyricsFontScale(it) },
                                        onOpenAudioLab = { vm.open(Overlay.AudioLab(it)) },
                                        onRescan = { vm.refreshLibrary(announce = true) },
                                        onOpenStats = { vm.open(Overlay.Stats) },
                                        onOpenCarMode = { vm.open(Overlay.CarMode) },
                                        onOpenSystemNotificationSettings = {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            runCatching { context.startActivity(intent) }
                                        },
                                        onShakeToSkip = { vm.setShakeToSkip(it) },
                                        onKeepScreenOn = { vm.setKeepScreenOn(it) },
                                        onMinTrackSeconds = { vm.setMinTrackSeconds(it) },
                                        onBackup = {
                                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                            runCatching { backupLauncher.launch("pulse-music-backup-$date.json") }
                                        },
                                        onRestore = { runCatching { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }
                                    ),
                                    contentPadding = contentPadding
                                )
                            }
                        }

                        // Status-bar fade so scrolled content never collides with the clock/icons
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(statusTop + 16.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(colors.background.copy(alpha = 0.92f), colors.background.copy(alpha = 0.6f), androidx.compose.ui.graphics.Color.Transparent)
                                    )
                                )
                        )

                        // Floating chrome: toast, mini player, bottom navigation
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp)
                        ) {
                            PulseToastHost(toast, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { toast = null }
                            if (miniVisible) {
                                MiniPlayer(
                                    playbackState = playback,
                                    onPlayPause = vm::togglePlayPause,
                                    onNext = vm::seekToNext,
                                    onPrevious = vm::seekToPrevious,
                                    onClick = vm::openNowPlaying
                                )
                            }
                            if (!useRail) {
                                PulseBottomBar(tab, { vm.selectTab(it) })
                            }
                        }
                    }
                    if (showSidePane) {
                        Box(
                            Modifier
                                .width(sidePaneWidth)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(8.dp)
                                .glass(colors, RoundedCornerShape(32.dp), strong = true)
                        ) {
                            NowPlayingScreen(
                                playbackState = playback,
                                audioLabState = audioLab,
                                sleepFadeOut = settings.sleepFadeOut,
                                actions = nowPlayingActions(vm, context, shareSong) { sheetSong = it },
                                isPane = true
                            )
                        }
                    }
                }

                // Full-screen overlays (Now Playing, Lyrics, Audio Lab, details…)
                AnimatedContent(
                    targetState = overlays.lastOrNull(),
                    transitionSpec = {
                        val enteringPlayer = targetState is Overlay.NowPlaying || targetState is Overlay.Lyrics
                        val leavingPlayer = initialState is Overlay.NowPlaying || initialState is Overlay.Lyrics
                        when {
                            enteringPlayer -> slideInVertically(tween(380)) { it } + fadeIn() togetherWith fadeOut(tween(200))
                            leavingPlayer && targetState == null -> fadeIn(tween(200)) togetherWith slideOutVertically(tween(320)) { it } + fadeOut()
                            else -> slideInHorizontally(tween(320)) { it / 3 } + fadeIn() togetherWith slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut()
                        }
                    },
                    label = "overlay"
                ) { overlay ->
                    if (overlay != null) {
                        Box(Modifier.fillMaxSize()) {
                            OverlayContent(overlay, vm, library, playback, audioLab, settings, playlists, stats, shareSong, bottomChrome) { sheetSong = it }
                            if (overlay !is Overlay.NowPlaying && overlay !is Overlay.Lyrics && overlay !is Overlay.CarMode && miniVisible) {
                                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)) {
                                    PulseToastHost(toast, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { toast = null }
                                    MiniPlayer(playback, vm::togglePlayPause, vm::seekToNext, vm::seekToPrevious, vm::openNowPlaying)
                                }
                            } else if (overlay is Overlay.NowPlaying || overlay is Overlay.Lyrics) {
                                // Top of the player, so messages never cover the transport and tool buttons.
                                PulseToastHost(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp)) { toast = null }
                            }
                        }
                    }
                }
            }
        }

        sheetSong?.let { sheet ->
            val fresh = library.songs.firstOrNull { it.id == sheet.id } ?: sheet
            SongActionsSheet(
                song = fresh,
                playlists = playlists.map { it.playlist },
                onDismiss = { sheetSong = null },
                onPlayNext = { vm.playNext(fresh) },
                onAddToQueue = { vm.addToQueue(fresh) },
                onToggleFavorite = { vm.toggleFavorite(fresh) },
                onAddToPlaylist = { vm.addSongToPlaylist(fresh, it) },
                onCreatePlaylist = { vm.createPlaylist(it, listOf(fresh)) },
                onGoToAlbum = { vm.openDetail(DetailTarget.AlbumTarget(fresh.album)) },
                onGoToArtist = { vm.openDetail(DetailTarget.ArtistTarget(fresh.artist)) },
                onShare = { shareSong(fresh) },
                onDetails = { detailsSong = fresh }
            )
        }

        detailsSong?.let { song ->
            SongDetailsSheet(song, loadPlayCount = { vm.playCount(song) }, onDismiss = { detailsSong = null })
        }
    }
}

private fun nowPlayingActions(
    vm: PulseMusicViewModel,
    context: Context,
    share: (Song) -> Unit,
    onMore: (Song) -> Unit
) = NowPlayingActions(
    onCollapse = { vm.close(Overlay.NowPlaying) },
    onPlayPause = vm::togglePlayPause,
    onNext = vm::seekToNext,
    onPrevious = vm::seekToPrevious,
    onSeek = vm::seekTo,
    onToggleShuffle = vm::toggleShuffle,
    onToggleRepeat = vm::toggleRepeat,
    onSpeedChange = vm::setPlaybackSpeed,
    onToggleFavorite = vm::toggleFavorite,
    onShare = share,
    onMore = onMore,
    onStartSleepTimer = vm::startSleepTimer,
    onSleepEndOfTrack = vm::sleepAtEndOfTrack,
    onCancelSleepTimer = vm::cancelSleepTimer,
    onSleepFadeChange = { vm.setSleepFade(it) },
    onReorderQueue = vm::reorderQueue,
    onRemoveFromQueue = vm::removeFromQueue,
    onClearQueue = vm::clearUpNext,
    onSkipTo = vm::skipToQueueItem,
    onSaveQueue = { vm.saveQueueAsPlaylist(it) },
    onOpenAudioLab = { vm.open(Overlay.AudioLab(it)) },
    onOpenLyrics = { vm.open(Overlay.Lyrics) },
    onOpenOutputSwitcher = { context.openOutputSwitcher() },
    onPitchChange = vm::setPitch,
    onCycleABLoop = vm::cycleABLoop
)

@Composable
private fun OverlayContent(
    overlay: Overlay,
    vm: PulseMusicViewModel,
    library: com.example.data.scanner.LibraryData,
    playback: com.example.model.PlaybackState,
    audioLab: com.example.model.AudioLabState,
    settings: com.example.data.AppSettings,
    playlists: List<PlaylistWithSongs>,
    stats: com.example.smart.ListeningStats,
    share: (Song) -> Unit,
    bottomChrome: androidx.compose.ui.unit.Dp,
    onMore: (Song) -> Unit
) {
    val context = LocalContext.current
    val colors = PulseTheme.colors
    val backdrop = Modifier.fillMaxSize().background(colors.background)
    when (overlay) {
        Overlay.NowPlaying -> {
            if (playback.currentSong == null) {
                LaunchedEffect(Unit) { vm.close(Overlay.NowPlaying) }
            } else {
                NowPlayingScreen(playback, audioLab, settings.sleepFadeOut, nowPlayingActions(vm, context, share, onMore), backdrop)
            }
        }
        Overlay.Lyrics -> LyricsScreen(
            playbackState = playback,
            fontScale = settings.lyricsFontScale,
            onFontScaleChange = { vm.setLyricsFontScale(it) },
            onBack = { vm.close(Overlay.Lyrics) },
            onSeekTo = vm::seekTo,
            onPlayPause = vm::togglePlayPause,
            modifier = backdrop
        )
        is Overlay.AudioLab -> {
            val spectrum by vm.spectrum.collectAsStateWithLifecycle()
            AudioLabScreen(
                state = audioLab,
                spectrum = spectrum,
                isPlaying = playback.isPlaying,
                settings = settings,
                manager = vm.audioLabManager,
                callbacks = AudioLabCallbacks(
                    onBack = { vm.close(overlay) },
                    onOpenOutputSwitcher = { context.openOutputSwitcher() },
                    onOpenBluetoothSettings = { context.openBluetoothSettings() },
                    onOpenSoundSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    onPerDeviceEq = { vm.setPerDeviceEq(it) },
                    onPauseOnDisconnect = { vm.setPauseOnDisconnect(it) },
                    onResumeOnReconnect = { vm.setResumeOnReconnect(it) },
                    onBluetoothAutoplay = { vm.setBluetoothAutoplay(it) },
                    onToast = { vm.showToast(it) }
                ),
                initialTab = overlay.tab,
                modifier = backdrop,
                bottomPadding = bottomChrome
            )
        }
        Overlay.Stats -> StatsScreen(stats, onBack = { vm.close(Overlay.Stats) }, onPlaySong = { s, l -> vm.playSong(s, l) }, modifier = backdrop)
        Overlay.CarMode -> CarModeScreen(
            playbackState = playback,
            onPlayPause = vm::togglePlayPause,
            onNext = vm::seekToNext,
            onPrevious = vm::seekToPrevious,
            onExit = { vm.close(Overlay.CarMode) }
        )
        is Overlay.Detail -> {
            val target = overlay.target
            val smartSongs by produceState(emptyList<Song>(), target, library.songs) {
                if (target is DetailTarget.SmartTarget) value = vm.smartPlaylistSongs(target.type)
            }
            val playlistEntry = (target as? DetailTarget.PlaylistTarget)?.let { t -> playlists.firstOrNull { it.playlist.id == t.playlist.id } }
            val songs = remember(target, library.songs, smartSongs, playlistEntry) {
                when (target) {
                    is DetailTarget.AlbumTarget -> library.songs.filter { it.album == target.title }.sortedBy { it.trackNumber }
                    is DetailTarget.ArtistTarget -> library.songs.filter { it.artist == target.title }
                    is DetailTarget.GenreTarget -> library.songs.filter { it.genre.equals(target.title, true) }
                    is DetailTarget.FolderTarget -> library.songs.filter { it.path.startsWith(target.path) }
                    is DetailTarget.YearTarget -> library.songs.filter { it.year == target.year }
                    is DetailTarget.PlaylistTarget -> playlistEntry?.songs.orEmpty()
                    is DetailTarget.SmartTarget -> smartSongs
                }
            }
            val (kind, subtitle) = when (target) {
                is DetailTarget.AlbumTarget -> "Album" to (songs.firstOrNull()?.artist ?: "")
                is DetailTarget.ArtistTarget -> "Artist" to "${songs.map { it.album }.distinct().size} albums"
                is DetailTarget.GenreTarget -> "Genre" to "Mix"
                is DetailTarget.FolderTarget -> "Folder" to target.path.substringAfterLast('/', target.path)
                is DetailTarget.YearTarget -> "Year" to "Released"
                is DetailTarget.PlaylistTarget -> "Playlist" to "Your playlist"
                is DetailTarget.SmartTarget -> "Smart playlist" to target.type.subtitle
            }
            CollectionDetailScreen(
                title = target.title,
                subtitle = subtitle,
                kindLabel = kind,
                songs = songs,
                playbackState = playback,
                onBack = { vm.close(overlay) },
                onPlay = { vm.playSongs(songs, it) },
                onShuffle = { vm.playSongs(songs, shuffle = true) },
                modifier = backdrop,
                isArtist = target is DetailTarget.ArtistTarget,
                albums = if (target is DetailTarget.ArtistTarget) library.albums.filter { it.artist == target.title } else emptyList(),
                onOpenAlbum = { vm.openDetail(DetailTarget.AlbumTarget(it.title)) },
                onRemoveSong = (target as? DetailTarget.PlaylistTarget)?.let { t -> { song: Song -> vm.removeSongFromPlaylist(song, t.playlist.id) } },
                onDelete = (target as? DetailTarget.PlaylistTarget)?.let { t -> { vm.deletePlaylist(t.playlist) } },
                onRename = (target as? DetailTarget.PlaylistTarget)?.let { t -> { name: String -> vm.renamePlaylist(t.playlist, name) } },
                bottomPadding = bottomChrome
            )
        }
    }
}
