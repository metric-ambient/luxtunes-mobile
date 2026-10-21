# LuxTunes Mobile

> [!WARNING]
> **About this software.** LuxTunes Mobile was written entirely by an AI, Claude Opus 5.5
> ("vibe-coded"). For copyright purposes, **metric-ambient is the author** of this software.
>
> The code has not been reviewed line by line by a person. It plays your music, records
> plays, skips and ratings, writes one file (`changes.json`) in the LuxTunes folder on your
> phone, and deletes song files there when you ask it to. Bugs could lose or damage data:
> **keep backups of your music**.
>
> LuxTunes Mobile is provided **"as is", without warranty of any kind**, express or
> implied, including fitness for a particular purpose. You run it at your own risk; the
> author is not liable for any damage or data loss. See the [GPL-3.0 license](LICENSE),
> sections 15 and 16.

An Android music player for the music that [LuxTunes](https://github.com/metric-ambient/luxtunes)
syncs to your phone. Plays, skips and ratings go back to LuxTunes at the next sync.

## What it does

- Plays what LuxTunes put in the `LuxTunes` folder on the phone (internal storage or SD
  card), with LuxTunes' own song details, ratings, play counts, lyrics and playlists.
- Browse by artist, album, song and playlist, with search in every tab.
- Now Playing with album art, lyrics and star ratings; lock-screen, notification and
  Bluetooth controls.
- Records plays, skips and ratings and hands them to LuxTunes at the next USB sync.
- Playlists and smart playlists made or changed on the phone go back to LuxTunes at the
  next sync (LuxTunes 0.6.0 or newer). Smart playlists use LuxTunes' own rules and stay
  up to date on the phone.
- Movies and TV shows play full screen.
- Tabs for artists, album artists, genres, composers, podcasts, audiobooks, movies and TV
  shows (choose them in Settings → Tabs), select mode, sorting, Get Info, and deleting songs from the
  phone (LuxTunes puts them back at the next sync if they're still chosen there).
- Picks up where you left off: the Now Playing list, the song and the position.
- Goes online only when you ask it to (Settings → Check for Updates), and says so first.
  No Google services, so it also runs on de-Googled phones.

Needs Android 8 or newer.

## Installing

1. Download the latest `LuxTunes-Mobile-….apk` from the releases page onto your phone.
2. Open it. Android asks you to allow installing apps from your browser or file manager
   the first time; allow it, then tap **Install**.
3. Open LuxTunes Mobile and allow **All files access** when asked. The app needs it to
   read the LuxTunes folder and write `changes.json` there.
4. Sync your phone from LuxTunes on the computer (USB, "File transfer" mode), then open
   the app.

## How it works with LuxTunes

The folder format is described in the LuxTunes repo
([docs/android-library-format.md](https://github.com/metric-ambient/luxtunes/blob/main/docs/android-library-format.md)).

- `library.db` (written by LuxTunes) is read-only for the app. The app copies it into its
  own storage whenever a sync replaces it.
- `changes.json` is the only file the app writes there: plays and skips since the last
  sync, ratings changed on the phone and (format 2) playlist changes. LuxTunes adds them to
  the library and empties the file, keeping anything the phone wrote while it was reading;
  the app then keeps only what hasn't arrived yet.
- `changes.json` is updated as soon as something happens, and again when the phone is
  plugged in for file transfer, so it's current when LuxTunes reads it.
- Lyrics come from LuxTunes when it has them, otherwise from the song file's own tags.
- LuxTunes decides what's on the phone: a song deleted on the phone comes back at the
  next sync if it's still selected in LuxTunes.

## Building

GitHub builds the APK on every push (`.github/workflows/build.yml`); download it from the
run's **Artifacts**. To publish a release, tag a version:

```bash
git tag v0.1.0
git push origin main v0.1.0
```

APKs are signed with the key stored in the repository secrets `KEYSTORE_BASE64` and
`KEYSTORE_PASSWORD` (key alias `luxtunes`); the build stops if they're missing. Android
only installs an update over the app when both are signed with the same key: the build
log ("Show the signing certificate") and Settings → About show which key signed a copy.

**Updating:** Settings → Check for Updates looks at this repository's latest release
(once the repository is public), downloads the APK and hands it to Android's installer.

To build on your own computer you need Android Studio (or the Android SDK and JDK 17):
`./gradlew assembleDebug`.

## Tech stack

| Part | Library |
|---|---|
| Language | Kotlin |
| Interface | Jetpack Compose (Material 3) |
| Playback | AndroidX Media3 (ExoPlayer, MediaSession) |
| Images | Coil |
| Data | SQLite |

## Project layout

```
app/src/main/java/io/github/metricambient/luxtunes/
    data/       LuxTunes folder, library import, the app's database, changes.json
    playback/   playback service and the connection to it
    ui/         screens (Compose)
app/src/test/   unit tests (fake data only)
```

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
