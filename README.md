<div align="center">

# Legato

### A music player for the YouTube Music catalogue

[![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)](https://github.com/grayguava/legato/releases) ·
[![Kotlin](https://img.shields.io/badge/Kotlin-DB71A8?logo=kotlin&logoColor=white)](https://kotlinlang.org) ·
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[Download](https://github.com/grayguava/legato/releases) ·
[Features](#features) ·
[Build it yourself](#build-it-yourself) ·
[Privacy](#privacy) ·
[License](#license)

</div>

<p align="center">
  <img src="assets/mockups/main_demo.webp" alt="The artist page, lyrics and player, side by side" width="100%">
</p>

Free music player for the YouTube Music catalogue, sign in only if you want
your likes and playlists.

| Bottom dock | Lyrics | Player |
|:---:|:---:|:---:|
| <img src="assets/mockups/bottom_dock.webp" alt="Bottom dock" width="100%"> | <img src="assets/mockups/lyrics_screen.webp" alt="Lyrics" width="100%"> | <img src="assets/mockups/player_controls.webp" alt="Player controls" width="100%"> |
| **Bottom dock**<br>The mini player and the Home, Explore, Library pill, floating over the page. | **Lyrics**<br>The line being sung stays sharp; the rest fades back. | **Player**<br>Codec line, like, scrubber, transport and the output row. |

## Features

- Playback: search, browse and play anything on the catalogue; likes, playlists,
  radio, queue, smart shuffle; local music library.
- Design: frosted glass panels, sheet motion, animated icon buttons, artwork-driven
  accents.
- Controls: like and share in one row, playlists, ringtones, sleep timer, lyrics
  offset, skip silence and crossfade where the audio layer allows.

## Get it

Android phone or tablet: download the APK from the
[releases](https://github.com/grayguava/legato/releases).

## Build it yourself

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

You need a JDK and an Android SDK to build. `.github/workflows/release.yml`
runs by hand with a version, tests the tree, signs with the `LEGATO_KEYSTORE`
secrets and leaves a draft release with the APKs.

## Privacy

No ads, no account needed, no telemetry. Legato ships no analytics SDK, no crash
reporter and no usage counter, and there is no toggle that turns one on later.
Nothing about your listening is collected for us, and there is no server of ours
in the path.

Signing in is optional and only reaches your own likes and playlists. Streams
and artwork come from the catalogue, so that traffic is between you and YouTube,
as with any player.

## Disclaimer

The screenshots show real albums, artists and songs to show the app as it's
used. Cover art, names and recordings belong to their owners. Legato isn't
affiliated with or endorsed by YouTube, Google or any artist or label shown.

Legato hosts no music. It plays the catalogue's own streams, either the public
ones or the ones your signed-in account can reach, and it uploads nothing of
its own.

## License

Legato is licensed under the [GNU General Public License v3.0](LICENSE). Code,
libraries, fonts and data from other projects are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), as their licences require.
