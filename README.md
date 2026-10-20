# LuxTunes Mobile

> [!WARNING]
> **About this software.** LuxTunes Mobile was written entirely by an AI, Claude Opus 5.5
> ("vibe-coded"). For copyright purposes, **metric-ambient is the author** of this software.
>
> The code has not been reviewed line by line by a person. It plays your music, records
> plays, skips and ratings, and writes one file (`changes.json`) in the LuxTunes folder on
> your phone. Bugs could lose or damage data: **keep backups of your music**.
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
- No internet: the app doesn't even ask Android for internet access. No Google services,
  so it also runs on de-Googled phones.

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
  sync, plus ratings changed on the phone. LuxTunes adds them to the library and empties
  the file; the app then starts counting from zero again.
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

Release APKs are signed with the key stored in the repository secrets `KEYSTORE_BASE64`
and `KEYSTORE_PASSWORD` (key alias `luxtunes`). Without them the build uses a throwaway
debug key, and such an APK can't be updated by a properly signed one.

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
