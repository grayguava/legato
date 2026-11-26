<div align="center">

# Legato

### An iOS-style music player for the YouTube Music catalogue

[Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white) ·
[Kotlin and Jetpack Compose](https://img.shields.io/badge/Kotlin_7F52FF) ·
[License](LICENSE)

[Download](https://github.com/grayguava/legato/releases) ·
[Features](#features) ·
[Build it yourself](#build-it-yourself) ·
[Privacy](#privacy) ·
[License](#license)

</div>

Free music player for the YouTube Music catalogue, sign in only if you want
your likes and playlists. Android first, desktop and TV later.

Legato puts the YouTube Music catalogue in an iOS-style shell: frosted glass
panels, springy enter motion, bottom docks, and one song menu for every screen —
a long press and the "⋮" button open the same panel, with the song it acts on
staying lit.

## Features

- **Playback** — search, browse and play anything on the catalogue; likes,
  playlists, radio, queue, smart shuffle; local music library.
- **Design** — frosted glass panels, iOS-style sheet motion, animated icon
  buttons, artwork-driven accents.
- **Controls** — like and share in one row, playlists, ringtones, sleep timer,
  lyrics offset, skip silence and crossfade where the audio layer allows.

## Get it

Android phone or tablet: download the APK from the
[releases](https://github.com/grayguava/legato/releases). Android 8.0 or newer.

## Build it yourself

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

You need JDK 17 or newer and an Android SDK. Release signing and the project
layout are documented in the repo.

## Privacy

No ads, no account needed. Sign in only to sync likes and playlists.

## Disclaimer

The screenshots show real albums, artists and songs to show the app as it's
used. Cover art, names and recordings belong to their owners. Legato isn't
affiliated with or endorsed by YouTube, Google or any artist or label shown.

## License

Legato is licensed under the [GNU General Public License v3.0](LICENSE). Code,
libraries, fonts and data from other projects are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), as their licences require.
