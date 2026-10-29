# LuxTunes Mobile

> [!WARNING]
> **About this software.** LuxTunes Mobile was written entirely by an AI, Claude Opus 5.5
> ("vibe-coded"). For copyright purposes, **metric-ambient is the author** of this software.
>
> The code has not been reviewed line by line by a person. It plays your music, records
> plays, skips and ratings, writes `changes.json` in the LuxTunes folder on your phone,
> deletes song files there when you ask it to, and moves (or copies) songs into it when you
> use Add Folder to Library. Bugs could lose or damage data: **keep backups of your music**.
>
> LuxTunes Mobile is provided **"as is", without warranty of any kind**, express or
> implied, including fitness for a particular purpose. You run it at your own risk; the
> author is not liable for any damage or data loss. See the [GPL-3.0 license](LICENSE),
> sections 15 and 16.

An Android music player for the music that [LuxTunes](https://github.com/metric-ambient/luxtunes)
syncs to your phone. Plays, skips, ratings, playlists and edits go back to LuxTunes at the
next sync.

## What it does

- Plays what LuxTunes put in the `LuxTunes` folder on the phone (internal storage or SD
  card), with LuxTunes' own song details, ratings, play counts, lyrics and playlists.
- Browse by artist, album, song and playlist, with search in every tab, a fast-scroll
  handle with letters, and sorting (also in reverse).
- Now Playing with album art, star ratings and lyrics; timed lyrics (LuxTunes 0.8.5+)
  light up the line being sung. Lock-screen, notification and Bluetooth controls.
- Records plays, skips and ratings and hands them to LuxTunes at the next USB sync. A skip
  is pressing Next in a song's first 20 seconds, as in LuxTunes.
- Shuffle works as in LuxTunes: albums you haven't heard lately first, the same artist or
  album never close together. Also shuffle by albums or by artists.
- Playlists and smart playlists made or changed on the phone go back to LuxTunes at the
  next sync (LuxTunes 0.6.0 or newer). Smart playlists use LuxTunes' own rules and stay
  up to date on the phone.
- Movies and TV shows play full screen.
- Settings → Sound: LuxTunes' ten-band equalizer with the same presets (synced with the
  computer), crossfade or gapless, autoplay, playback speed for music and for
  audiobooks/podcasts, ReplayGain and mono; what happens during phone calls and when other
  apps play sound.
- Sleep timer, from 15 minutes to 8 hours, fading out at the end.
- Headphone and Bluetooth options: headset buttons, play on connect, and stopping a car
  from starting music by itself.
- Dark, Material and Light themes with your own colors, or an accent color taken from the
  album art.
- Tabs for artists, album artists, genres, composers, podcasts, audiobooks, movies and TV
  shows (choose them in Settings → Tabs, drag to reorder), select mode, Get Info with
  editing of song details (sent to LuxTunes at the next sync), and deleting songs from the
  phone (LuxTunes puts them back at the next sync if they're still chosen there).
- Add Folder to Library moves (or copies) songs already on the phone into the library;
  they go to LuxTunes at the next sync (LuxTunes 0.8.2 or newer).
- Picks up where you left off: the play queue, the song and the position.
- Goes online only when you ask it to (Settings → Check for Updates), and says so first.
  No Google services, so it also runs on de-Googled phones.

Needs Android 8 or newer.

## Privacy

LuxTunes Mobile collects nothing about you: no accounts, ads, analytics or tracking.
Your music, ratings, plays and playlists stay on the phone and go only to your own
computer, over USB, when you sync. The app goes online only when you choose Check for
Updates (it asks first); GitHub then sees your internet address, as any website would.

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
- `changes.json` is how the phone talks back: plays and skips since the last sync, ratings
  changed on the phone and (format 2) playlist changes, equalizer changes, songs added with
  Add Folder and edited song details. LuxTunes adds them to the library and empties the
  file, keeping anything the phone wrote while it was reading; the app then keeps only what
  hasn't arrived yet. Besides that file, the app only writes song files it adds with Add
  Folder, and deletes the ones you delete.
- `changes.json` is updated as soon as something happens, and again when the phone is
  plugged in for file transfer, so it's current when LuxTunes reads it.
- Lyrics come from LuxTunes when it has them (timed ones too, from LuxTunes 0.8.5),
  otherwise from the song file's own tags.
- LuxTunes decides what's on the phone: a song deleted on the phone comes back at the
  next sync if it's still selected in LuxTunes.

## Building

GitHub builds the APK on every push (`.github/workflows/build.yml`); download it from the
run's **Artifacts**. To publish a release, tag a version:

```bash
git tag v0.8.6
git push origin main v0.8.6
```

APKs are signed with the key stored in the repository secrets `KEYSTORE_BASE64` and
`KEYSTORE_PASSWORD` (key alias `luxtunes`); the build stops if they're missing. Android
only installs an update over the app when both are signed with the same key: the build
log ("Show the signing certificate") and Settings → About / Legal show which key signed a copy.

**Updating:** Settings → Check for Updates looks at this repository's latest release,
downloads the APK, checks it against the checksum GitHub publishes and hands it to
Android's installer.

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
