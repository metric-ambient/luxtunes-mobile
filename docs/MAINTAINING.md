# Maintaining LuxTunes Mobile

How to look after this project: saving and sending changes to GitHub, making a release,
building the app on your own computer, and what to do when something goes wrong.

Every command below runs **on your computer, in a terminal, inside the project folder**
(the folder that holds `README.md` and `gradlew`). GitHub does the official builds.
Your computer only needs to build when you want to check something before you push.

## Contents

1. [How the pieces fit together](#1-how-the-pieces-fit-together)
2. [Everyday: save and send changes](#2-everyday-save-and-send-changes)
3. [Making a release](#3-making-a-release)
4. [When the GitHub build fails](#4-when-the-github-build-fails)
5. [Building on your own computer](#5-building-on-your-own-computer)
6. [Putting a build on your phone](#6-putting-a-build-on-your-phone)
7. [The signing key](#7-the-signing-key)
8. [Updating libraries](#8-updating-libraries)
9. [Working with LuxTunes on the computer](#9-working-with-luxtunes-on-the-computer)
10. [Where things are in the code](#10-where-things-are-in-the-code)
11. [Common git messages and what they mean](#11-common-git-messages-and-what-they-mean)

---

## 1. How the pieces fit together

| Where | What happens there |
|---|---|
| Your computer | You (or Claude) change files. Git keeps the history of every change. |
| GitHub (`main` branch) | Every push is built and tested automatically (the **Actions** tab). |
| GitHub (a `v…` tag) | A tag like `v0.8.6` builds the app and publishes it as a **Release**. |
| The phone | Settings → Check for Updates downloads the newest Release. |

The build is described in `.github/workflows/build.yml`. It:

1. runs the unit tests (`app/src/test/`);
2. builds the release APK, signed with your key (stored in GitHub as secrets, see
   [section 7](#7-the-signing-key));
3. prints the signing certificate in the log;
4. attaches the APK to the run, under **Artifacts**;
5. for a tag, also publishes it as a Release.

## 2. Everyday: save and send changes

### Before your first commit on a computer: check who git thinks you are

Git stamps every commit with a name and an email address, and on a public repository
anyone can read them. Check them before you commit:

```bash
git config user.name
git config user.email
```

The email address should be your GitHub "noreply" address (GitHub → Settings → Emails).
**It should never be your personal address.** To set it for every repository on this
computer:

```bash
git config --global user.name "your-github-username"
git config --global user.email "12345678+your-github-username@users.noreply.github.com"
```

In GitHub → Settings → Emails, also tick **Block command line pushes that expose my
email**. GitHub then refuses any push that would publish your real address.

### The usual routine

```bash
git status                  # what changed (red = not yet added)
git diff                    # the changes themselves (press q to leave)
git add -A                  # include every change in the next commit
git commit -m "Say what changed, in a few words"
git push                    # send it to GitHub
```

Then open the repository's **Actions** tab and wait for a green tick. A red cross means
the build failed: see [section 4](#4-when-the-github-build-fails).

To check the last few commits, and who they're from, before pushing:

```bash
git log -5 --format='%h %an <%ae>  %s'
```

### Good habits

- **Commit small and often**, with a message that says what changed ("Sleep timer fades
  out", not "updates"). A small commit is easy to find and easy to undo.
- **Don't commit what's personal.** Files that hold notes, keys or anything about you
  are listed in `.gitignore`, so git leaves them out. Before you add anything new of that
  kind, add its name to `.gitignore` first.
- **Edited a file yourself?** That's fine. Just mention it when you next ask Claude for
  changes, so your edit isn't overwritten.

### Undoing things

| Situation | Command |
|---|---|
| Throw away the changes to one file since the last commit | `git restore path/to/File.kt` |
| Take back a `git add` (the change itself stays) | `git restore --staged path/to/File.kt` |
| Change the message of the last commit (not pushed yet) | `git commit --amend -m "New message"` |
| Undo a commit that's already pushed (adds a new commit that reverses it) | `git revert <commit id>` then `git push` |

Avoid `git push --force` unless you know exactly why you need it. It rewrites history on
GitHub.

## 3. Making a release

1. Push your changes (section 2) and wait for the green tick on the Actions tab.
2. Tag that commit and push the tag:

   ```bash
   git tag v0.8.6
   git push origin v0.8.6
   ```

3. The tag starts a release build. When it finishes (a few minutes), the APK appears under
   **Releases** on the repository page, and phones find it with Check for Updates.

Things to know:

- **The tag must start with `v`.** `0.8.6` without the `v` builds nothing.
- **The version number comes from the tag.** `v0.8.6` → the app says 0.8.6. There's
  nothing to edit in the code.
- **Each build gets a higher internal number** (the GitHub run number), so a new APK
  always installs over the old one.
- **Pick the next number:** small fixes go up in the last number (0.8.6 → 0.8.7); new
  features in the middle one (0.8.7 → 0.9.0).

Tagged the wrong commit? Delete the tag on both sides, then tag again:

```bash
git tag -d v0.8.6
git push origin --delete v0.8.6
```

If the Release was already published, also delete it on GitHub (Releases → the release →
Delete).

To list the tags you have: `git tag`.

## 4. When the GitHub build fails

1. Open the failed run on the Actions tab, then the **build** job.
2. Look in the log for lines starting with **`e:`**. Those are the errors, with the file
   and line number, for example:

   ```
   e: file:///…/app/src/main/java/…/ui/NowPlayingScreen.kt:120:24 Unresolved reference 'foo'.
   ```

   Lines starting with `w:` are warnings and don't stop the build.
3. If it says `FAILED` after `testDebugUnitTest`, a test didn't pass. The log names the
   test and what it expected.
4. Copy the `e:` lines (or the failing test) and give them to Claude, or fix the line
   yourself. Then commit and push again.

Other failures you may meet:

- **"The KEYSTORE_BASE64 secret is missing"**: the signing secrets are gone from the
  repository settings. See [section 7](#7-the-signing-key).
- **A download error** (a library couldn't be fetched): usually a hiccup at GitHub. Use
  **Re-run jobs** on the failed run.

## 5. Building on your own computer

This is optional: GitHub builds every push anyway. It's useful for checking that things
compile, or running the tests, before you push.

### One-time setup (Linux Mint)

You need Java 17 and the Android SDK. The simplest route is Android Studio, which
includes both. The lighter route is just the command-line tools:

```bash
# 1. Java 17
sudo apt install openjdk-17-jdk unzip

# 2. Android command-line tools: download "Command line tools only" for Linux from
#    https://developer.android.com/studio (near the bottom of the page), then:
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*_latest.zip
mv cmdline-tools latest

# 3. Tell your terminal where they are (once; new terminals pick it up)
echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc

# 4. Accept the licences and fetch what this project builds against
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Then go back to the project folder. The first build downloads Gradle and the libraries,
so it needs internet and takes a few minutes. Later builds are quicker.

### Checking and testing

```bash
./gradlew testDebugUnitTest     # run the unit tests
./gradlew assembleDebug         # build a test APK
```

If you get "Permission denied", run `chmod +x gradlew` once.

- The test results are in `app/build/reports/tests/testDebugUnitTest/index.html`.
- The test APK is `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew clean` deletes everything built, if a build gets into a strange state.

`app/build/` is ignored by git, so built files never end up in a commit.

## 6. Putting a build on your phone

**The safe way is GitHub's APK:** download it from the run's Artifacts (or a Release) and
open it on the phone. It's signed with your key, so it installs over the app you have
and keeps your data.

**A debug build from your computer won't install over it.** Android only replaces an app
with one signed by the same key, and debug builds use a throwaway key. Uninstalling the
app to make room deletes its data, including plays, ratings and playlist changes that
haven't reached LuxTunes yet. Sync first if you ever do this.

**To build a phone-ready APK on your computer**, sign it with your own key. Use the run
number of the newest build on the Actions tab, so Android accepts it and the next GitHub
build still counts as newer:

```bash
export LUX_KEYSTORE="$PWD/signing/luxtunes-release.jks"
export LUX_KEYSTORE_PASSWORD="$(cat signing/KEYSTORE_PASSWORD.txt)"
export GITHUB_RUN_NUMBER=123      # ← the newest run number from the Actions tab
./gradlew testDebugUnitTest assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Without a tag it calls itself
by the fallback version in `app/build.gradle.kts`. Close the terminal afterwards, so the
password doesn't stay in it.

To copy the APK to the phone, use USB file transfer and open the file on the phone. Or,
with USB debugging turned on in the phone's developer options:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 7. The signing key

Every APK is signed with the key in `signing/luxtunes-release.jks`. Android uses it to
tell that an update really comes from you.

- **Never commit it.** `signing/` is in `.gitignore`. GitHub has its own copy as two
  repository secrets (Settings → Secrets and variables → Actions):
  - `KEYSTORE_BASE64`: the key file, as text (`signing/KEYSTORE_BASE64.txt`);
  - `KEYSTORE_PASSWORD`: its password (`signing/KEYSTORE_PASSWORD.txt`).
- **Back it up** somewhere safe that isn't this repository, for example an encrypted USB
  stick or a password manager. **If the key is lost, no future version can install over
  the existing app.** Everyone would have to uninstall, losing whatever hadn't synced yet.
- To put the secrets back on GitHub: repository → Settings → Secrets and variables →
  Actions → **New repository secret**. Paste the contents of each `.txt` file, using the
  name above.
- Each build log shows the key's SHA-256 fingerprint ("Show the signing certificate"),
  and so does the app under Settings → About / Legal. They should always match.

## 8. Updating libraries

The library versions are in two files:

- `build.gradle.kts` (top level): the Android build plugin and Kotlin;
- `app/build.gradle.kts`, under `dependencies`: Compose (through the `compose-bom` line),
  Media3, Coil and the rest.

Update **one thing at a time**, push, and check the build. If it breaks, the error is
about that one change, and `git revert` takes it back. Media3 updates are the most likely
to need code changes, because playback relies on parts it marks as "unstable".

## 9. Working with LuxTunes on the computer

The phone and the computer talk through two files in the phone's `LuxTunes` folder:

- `library.db`: written by LuxTunes at each sync. The app only reads it.
- `changes.json`: written by the app. It holds plays, skips, ratings, playlist changes,
  equalizer changes, songs added on the phone and edited song details. LuxTunes reads it
  at the next sync.

The format is documented in the LuxTunes repository, in
`docs/android-library-format.md`. That document is the reference for both sides. When a
feature needs both apps (like timed lyrics or editing song details), one side writes down
what it needs, the other builds it, and the format document is updated.

LuxTunes tells the app what it supports through keys in `library.db`'s `info` table. For
example, `phone_imports` and `phone_edits` are `1` from LuxTunes 0.8.2. The app checks
these keys, so an older LuxTunes doesn't lose anything sent from the phone.

## 10. Where things are in the code

```
app/src/main/java/io/github/metricambient/luxtunes/
    data/          the library, settings and the sync files
        Repository.kt       reading library.db, writing changes.json, what the screens show
        AppDatabase.kt      the app's own database (a copy of the library, pending changes)
        Prefs.kt            every setting and its default
        Shuffle.kt          the shuffle (matches LuxTunes)
        Lyrics.kt           reading timed (LRC) lyrics
        FolderImport.kt     Add Folder to Library
        Updater.kt          Check for Updates
    playback/      the player
        PlaybackService.kt  plays music in the background; counts plays and skips
        PlayerConnection.kt the screens' link to the player (queue, shuffle)
    ui/            the screens
        MoreScreens.kt      the Help text (the HELP list) and the licences
        SettingsScreens.kt  the Settings screens
app/src/main/res/           icons and the app's name (values/strings.xml)
app/src/test/               unit tests
.github/workflows/build.yml the GitHub build
README.md                   the project's front page on GitHub
```

To find where something on screen comes from, search the code for its exact text:

```bash
grep -rn "Highlight lyrics" app/src
```

When you add or change a feature, also update the Help text (`MoreScreens.kt`) and the
README's feature list, so they keep matching the app.

## 11. Common git messages and what they mean

| Message | What it means | What to do |
|---|---|---|
| `nothing to commit, working tree clean` | Everything is already committed. | Nothing; push if you haven't. |
| `The current branch main has no upstream branch` | Git doesn't know where `main` goes on GitHub. | `git push --set-upstream origin main` once. |
| `src refspec v0.8.6 does not match any` | There's no tag by that name (often the `v` is missing). | `git tag` to see your tags, then tag again. |
| `tag 'v0.8.6' already exists` | That number is taken. | Use the next number, or delete the old tag (section 3). |
| `! [rejected] main -> main (fetch first)` | GitHub has commits you don't have. | `git pull`, then `git push`. |
| `Please tell me who you are` | Git has no name or email set. | See "check who git thinks you are" (section 2). |
| `push declined due to email privacy restrictions` | A commit has your real email address in it. | Set the noreply address, then fix the commit with `git commit --amend --reset-author --no-edit`. Ask for help if there's more than one commit. |
