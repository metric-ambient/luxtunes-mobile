package io.github.metricambient.luxtunes.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.LuxFolder
import io.github.metricambient.luxtunes.data.StorageChoice

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LuxTheme { Root() } }
    }

    override fun onStart() {
        super.onStart()
        (application as LuxTunesApp).player.connect()
    }

    override fun onStop() {
        (application as LuxTunesApp).player.release()
        super.onStop()
    }
}

/** Screens, by route. Names and keys go in the route URL-encoded (Navigation decodes them). */
object Routes {
    const val HOME = "home"
    const val NOW = "now"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val QUEUE = "queue"
    const val SETTINGS_TABS = "settings/tabs"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_THEME = "settings/theme"
    const val SETTINGS_OPERATION = "settings/operation"
    const val SETTINGS_LIBRARY = "settings/library"
    const val SETTINGS_SOUND = "settings/sound"
    const val SETTINGS_EQUALIZER = "settings/equalizer"
    const val SETTINGS_HEADSET = "settings/headset"
    const val SETTINGS_HELP = "settings/help"
    const val SETTINGS_LICENSES = "settings/licenses"
    fun album(key: String) = "album/${Uri.encode(key)}"
    /** An album artist's page. */
    fun artist(name: String) = group("artist", name)
    /** kind: artist (album artist), trackartist, genre, composer or tvshow. */
    fun group(kind: String, name: String) = "group/$kind/${Uri.encode(name)}"
    const val VIDEO = "video"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    const val SMART_NEW = "smart/new"
    fun smartEdit(id: String) = "smart/edit/${Uri.encode(id)}"
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val app = app()
    var access by remember { mutableStateOf(LuxFolder.hasAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = LuxFolder.hasAccess(context)
        if (now && !access) app.repository.refreshAsync()
        access = now
    }

    // Android 13+ asks before an app may show its playback notification.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // First start with an SD card in the phone: ask where the LuxTunes folder is.
    // With internal storage only, that's the answer without asking.
    LaunchedEffect(access) {
        if (access && app.prefs.current.storage == null) {
            if (LuxFolder.hasRemovable(context)) Dialogs.show(DialogRequest.ChooseStorage(firstTime = true))
            else {
                app.prefs.update { it.copy(storage = StorageChoice.INTERNAL) }
                app.repository.refreshAsync()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Lux.background).systemBarsPadding()) {
        if (access) MainScreens() else PermissionScreen { access = LuxFolder.hasAccess(context) }
        DialogHost()
        UpdateDialogs()
        SleepTimerSheet()
        AddFolderHost()
        NoticeHost()
    }
}

@Composable
private fun MainScreens() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                nav,
                startDestination = Routes.HOME,
                // Quick fades (the default is 0.7 s, long enough to tap the old screen by mistake).
                // Now Playing slides up over the screen below, which stays put meanwhile.
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = {
                    if (targetState.destination.route == Routes.NOW) ExitTransition.KeepUntilTransitionsFinished
                    else fadeOut(tween(150))
                },
                popEnterTransition = {
                    if (initialState.destination.route == Routes.NOW) EnterTransition.None else fadeIn(tween(150))
                },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                screen(nav, Routes.HOME) { HomeScreen(nav) }
                screen(
                    nav, Routes.NOW,
                    enter = { slideInVertically(tween(300)) { it } },
                    popExit = { slideOutVertically(tween(300)) { it } },
                ) { NowPlayingScreen(nav) }
                screen(nav, Routes.SETTINGS) { SettingsScreen(nav) }
                screen(nav, Routes.SETTINGS_TABS) { TabsSettingsScreen(nav) }
                screen(nav, Routes.SMART_NEW) { SmartEditorScreen(nav, null) }
                screen(nav, "smart/edit/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                    SmartEditorScreen(nav, it.arguments?.getString("id"))
                }
                screen(nav, Routes.SETTINGS_ABOUT) { AboutScreen(nav) }
                screen(nav, Routes.SETTINGS_THEME) { ThemeSettingsScreen(nav) }
                screen(nav, Routes.SETTINGS_OPERATION) { OperationSettingsScreen(nav) }
                screen(nav, Routes.SETTINGS_LIBRARY) { LibrarySettingsScreen(nav) }
                screen(nav, Routes.SETTINGS_SOUND) { SoundSettingsScreen(nav) }
                screen(nav, Routes.SETTINGS_EQUALIZER) { EqualizerScreen(nav) }
                screen(nav, Routes.SETTINGS_HEADSET) { HeadsetSettingsScreen(nav) }
                screen(nav, Routes.SETTINGS_HELP) { HelpScreen(nav) }
                screen(nav, Routes.SETTINGS_LICENSES) { LicensesScreen(nav) }
                screen(nav, Routes.SEARCH) { SearchScreen(nav) }
                screen(nav, Routes.QUEUE) { QueueScreen(nav) }
                screen(nav, "album/{key}", listOf(navArgument("key") { type = NavType.StringType })) {
                    AlbumScreen(nav, it.arguments?.getString("key").orEmpty())
                }
                screen(
                    nav,
                    "group/{kind}/{name}",
                    listOf(navArgument("kind") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }),
                ) {
                    GroupScreen(nav, it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("name").orEmpty())
                }
                screen(nav, Routes.VIDEO) { VideoScreen(nav) }
                screen(nav, "playlist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                    PlaylistScreen(nav, it.arguments?.getString("id").orEmpty())
                }
            }
        }
        val wantMini = route != Routes.NOW && route != Routes.VIDEO &&
            route?.startsWith(Routes.SETTINGS) != true && route?.startsWith("smart/") != true
        // Leaving Now Playing: wait until it has slid away, so the bar doesn't
        // appear on top of it halfway down.
        var showMini by remember { mutableStateOf(wantMini) }
        var lastRoute by remember { mutableStateOf(route) }
        LaunchedEffect(wantMini, route) {
            val from = lastRoute
            lastRoute = route
            if (wantMini && !showMini && (from == Routes.NOW || from == Routes.VIDEO)) delay(320)
            showMini = wantMini
        }
        if (showMini && wantMini) MiniPlayer(nav, showMenu = route == Routes.HOME)
    }
}

/** A screen in the NavHost that only takes touches while it's the current one. */
private fun NavGraphBuilder.screen(
    nav: NavHostController,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    enter: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition)? = null,
    popExit: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition)? = null,
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments, enterTransition = enter, popExitTransition = popExit) { entry ->
    Box(Modifier.fillMaxSize().onlyWhenCurrent(nav, entry)) { content(entry) }
}

/**
 * Swallows touches on a screen that's on its way out (still fading after Back or
 * after opening another screen), so a quick tap can't hit a song on it.
 */
private fun Modifier.onlyWhenCurrent(nav: NavHostController, entry: NavBackStackEntry) = pointerInput(entry) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (nav.currentBackStackEntry?.id != entry.id) event.changes.forEach { it.consume() }
        }
    }
}

/** Go somewhere without stacking the same screen twice. */
fun NavHostController.go(route: String) {
    navigate(route) { launchSingleTop = true }
}

@Composable
private fun PermissionScreen(onRecheck: () -> Unit) {
    val context = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onRecheck() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to LuxTunes", fontSize = 24.sp, color = Lux.text)
        Spacer(Modifier.height(16.dp))
        Text(
            "To play the music LuxTunes syncs to this phone, the app needs to read the " +
                "LuxTunes folder, and write one file there (changes.json) so your plays, " +
                "skips and ratings reach LuxTunes at the next sync.\n\n" +
                "On the next screen, turn on “Allow access to manage all files” for LuxTunes, " +
                "then come back.",
            color = Lux.dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val mine = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                try {
                    context.startActivity(mine)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }) { Text("Allow access") }
    }
}
