# Third-party notices

Legato is free software, licensed under the GNU General Public License
version 3 (see [LICENSE](LICENSE)). It is built on other people's code and
reads other people's data; this file says what, and where it came from.

Code adapted from another project keeps that project's licence, and the
libraries below are used under their own. Nothing here is vendored except
where a section says so.

## Adapted code

### OpenTune

- Source: [justshivv/Opentune](https://github.com/justshivv/Opentune), by the
  OpenTune contributors, under GPL-3.0, the same licence as this app
- Legato is a fork of it, and most of the app's interface is its code: the
  Compose screens, the components they are built from, the theme, the
  player, the library, the settings pages and the widgets.

### BitChord

- Source: [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord), GPL-3.0
- The Innertube client, its response parser and the YouTube stream resolver
  under `data/innertube/` are adapted from it, as are the InnerTubeX glue and
  the PoToken WebView (`data/innertube/InnerTubeXResolver.kt`,
  `data/innertube/potoken/`, `assets/po_token.html`).
- `FlacInfo` adapts its Samsung FLAC decoder exclusion and its STREAMINFO
  measurement approach, from `AudioOutputPolicy.kt` and `PlaybackService.kt`
  at commit `edc1e6f36febf71a118e649411d2fc21b70ead45`. The affected
  Samsung vendor decoders are excluded, and real FLAC metadata is read
  rather than treating float PCM as proof of a hi-res master.

### NewPipe

- Source: [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe), GPL-3.0
- The PoToken WebView follows NewPipe's design.

### Echo Music

- Source: [EchoMusicApp/Echo-Music](https://github.com/EchoMusicApp/Echo-Music/tree/a31a72bd958f7cdc128db17d2ea6ae90c3489aa1),
  GPL-3.0
- The optional motion effects were adapted from `ui/player/MiniPlayer.kt`
  (the animated glow and the wavy play button) and `ui/component/BottomSheet.kt`
  (the soft spring), under `app/src/main/kotlin/com/music/echo/` at that
  revision.
- Legato's adaptations lived in `ui/player/EchoMotion.kt` and
  `ui/PageMotion.kt`. Those effects have since been removed; this notice
  stays as the record of where the code came from.

## Libraries

### InnerTubeX

- Source: [MetrolistGroup/innertubex](https://github.com/MetrolistGroup/innertubex), GPL-3.0
- The YouTube client library the Innertube code is built on.

### NewPipeExtractor and Nanojson

- Source: [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
  and [TeamNewPipe/nanojson](https://github.com/TeamNewPipe/nanojson), GPL-3.0
- The stream extractor library, and the JSON parser it uses.

### Mozilla Rhino

- Source: [mozilla/rhino](https://github.com/mozilla/rhino), MPL-2.0
- The JavaScript engine that runs YouTube's own player code to work out how
  to stream a song.

### Ktor and OkHttp

- Source: [ktorio/ktor](https://github.com/ktorio/ktor) and
  [square/okhttp](https://github.com/square/okhttp), Apache-2.0
- The HTTP client the app talks to services with, and the one it runs on.

### Coil

- Source: [coil-kt/coil](https://github.com/coil-kt/coil), Apache-2.0
- Loading and caching the artwork.

### Jsoup

- Source: [jsoup/jsoup](https://github.com/jsoup/jsoup), MIT
- Parsing the pages the lyrics and the metadata come from.

### Protocol Buffers

- Source: [protocolbuffers/protobuf](https://github.com/protocolbuffers/protobuf),
  BSD-3-Clause
- The wire format the Innertube responses arrive in.

### Kotlin libraries

- Source: [Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
  and [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines),
  Apache-2.0
- JSON, and the background work the app does off the main thread.

### AndroidX

- Source: [androidx/androidx](https://github.com/androidx/androidx), Apache-2.0
- Jetpack Compose for the interface, Media3 for playback, WorkManager for
  the background jobs, and Lifecycle and Navigation for the app's own state.

### Material Kolar

- Source: [material-kolor/material-kolor](https://github.com/material-kolor/material-kolor),
  Apache-2.0
- The colour scheme the theme is built from.

### Haze

- Source: [chrisbanes/haze](https://github.com/chrisbanes/haze), Apache-2.0
- The frosted glass behind the floating bars.

### Kyant backdrop

- Source: [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
  Apache-2.0
- The backdrop layer the liquid glass bends.

### Inter

- Source: [rsms/inter](https://github.com/rsms/inter), by Rasmus Andersson
  and the Inter Project Authors, under the SIL Open Font License 1.1
- The typeface the whole interface is set in, the plain cut for body text
  and the Display cut for the large styles. Bundled in `legato/ui`'s
  resources; its licence file sits beside the other cuts in the upstream
  release, and its text is at the end of this file.

### SF Symbols

- Source: [cosmictaserdev-creator/Jetpack_SF_Symbols](https://github.com/cosmictaserdev-creator/Jetpack_SF_Symbols),
  by cosmictaserdev-creator, MIT for the library and the code it generates;
  it is itself built on [phranck/sf-symbols-lib](https://github.com/phranck/sf-symbols-lib)
  and [phranck/sf-symbols-lib-jetpackCompose](https://github.com/phranck/sf-symbols-lib-jetpackCompose)
- The icons on the settings screens and the menus are Apple's SF Symbols,
  drawn as Compose vectors by that library.
- The library's licence ends with this disclaimer:

  > SF Symbols is a trademark of Apple Inc. The symbol glyphs and naming
  > conventions are the intellectual property of Apple Inc. This library and
  > its generated code are intended strictly for educational, research, and
  > design interoperability purposes. Please adhere to Apple's terms and
  > guidelines regarding SF Symbols usage.

## Data and services

### MusicBrainz and the Cover Art Archive

- Source: [metabrainz/musicbrainz-server](https://github.com/metabrainz/musicbrainz-server)
  and [coverartarchive.org](https://coverartarchive.org), run by the MetaBrainz
  Foundation
- Album covers and artist photographs.

### Apple Music and iTunes

- Source: [music.apple.com](https://music.apple.com) and the
  [iTunes Search API](https://performance-partners.apple.com/search-api)
- Animated cover art, artist photographs, track metadata, and word-by-word
  lyrics through Paxsenix.

### AutoEq

- Source: [jaakkopasanen/AutoEq](https://github.com/jaakkopasanen/AutoEq), MIT
- The headphone correction data behind the equalizer presets.

### SponsorBlock

- Source: [ajayyy/sponsorblock](https://github.com/ajayyy/sponsorblock),
  CC BY-NC-SA 4.0 for the data
- The skip segments that cut out the parts of a song people ask to skip.

### Lyrics services

- Sources: [lrc.red](https://lrc.red/),
  [lrclib.net](https://lrclib.net/api),
  [lyricsplus.binimum.org](https://lyricsplus.binimum.org) and its mirrors,
  [lyrics-api.binimum.org](https://lyrics-api.binimum.org/),
  [api-lyrics.simpmusic.org](https://api-lyrics.simpmusic.org/v1/),
  [music.163.com](https://music.163.com/) for NetEase, and
  [lyrics-api.boidu.dev](https://lyrics-api.boidu.dev/getLyrics) for KuGou
- Word-by-word and line-synced lyrics, each read from the service that has
  them.

## The Inter licence

Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
http://scripts.sil.org/OFL

-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION AND CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
