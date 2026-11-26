# Releasing

How a Legato release is made. None of this is needed to build or run the app.

## How long it takes

On a two-core machine with 8 GB of memory, a debug build is three to twenty minutes and a
release build is half an hour, nearly all of it R8 shrinking the program. Start the release
build before you do anything else.

## The version

The version lives in the `version` file at the root of the repository, as three numeric parts
(`1.0.4`). The build reads it for `versionName` and derives `versionCode` from it
(`major*10000 + minor*100 + patch`), so the code always rises with the version.

The file is tracked, and a push that changes it is what starts a release. To make a release,
change the version, commit it and push it. To push anything else, leave the file alone and
nothing happens.

A local build can take a version of its own, so an update can be tried without touching the
tracked file:

```
LEGATO_VERSION=1.0.5 .\gradlew.bat :legato:app:assembleRelease
```

## The signing key

The release is signed with a key that lives outside the repository, at
`D:\DevSoft\Android\keys\legato-release.jks`, so nobody needs one to work on the app. A release
build without a key falls back to the debug key and still installs.

| Secret | What it is |
| --- | --- |
| `LEGATO_KEYSTORE_BASE64` | the keystore file, base64 encoded |
| `LEGATO_KEYSTORE_PASSWORD` | the keystore password |
| `LEGATO_KEY_ALIAS` | the key alias; left unset, the release script reads it out of the keystore |
| `LEGATO_KEY_PASSWORD` | the key password |

Generate the keystore once, with the JDK's `keytool`:

```
keytool -genkeypair -v -keystore legato-release.jks -alias legato \
  -keyalg RSA -keysize 2048 -validity 10000 -storetype JKS -dname "CN=grayguava, C=IN"
```

The keystore is kept next to it as `legato-release.jks.b64`, the base64 of the file, for a secret
store. `certutil -encode legato-release.jks legato-release.jks.b64` writes it, between a
`-----BEGIN CERTIFICATE-----` header and a footer, in lines of 64 characters; the store wants the
base64 on its own, so take the lines between the two.

Keep the key and its password somewhere you control. If they are lost, a build signed with a
new key cannot upgrade an installed copy of the app, so people have to uninstall it first and
install again.

## The local scripts

`.localscripts/` has the helpers a release is driven from:

| Script | What it does |
| --- | --- |
| `buildDebug.bat` | builds the debug APK; `-Version` gives it a version of its own |
| `buildRelease.bat` | builds the signed release APK; `-Version`, `-Keystore` and `-SkipPrompt` |
| `clean.bat` | clears the build output and the caches |

They are batch files, so they run from a command prompt with nothing else set up.

`buildRelease.bat -DebugKey` builds the release APK signed with the debug key, which needs no
key and no passwords. It cannot upgrade an installed copy of the app, so it is for looking at a
release build and nothing more.

The passwords are asked for and never written anywhere. Batch cannot hide what you type, so the
keystore password shows on screen as you type it; set `LEGATO_KEYSTORE_PASSWORD` and
`LEGATO_KEY_PASSWORD` before running the script if you would rather not. The finished APK lands
in `bin/` at the root of the repository, so it is where you expect it: `bin/app-debug.apk`,
`bin/app-release.apk`.

## Two application ids

The debug build has its own application id, `com.grayguava.legato.debug`, so it and the release
build can sit on the same phone: two builds signed with different keys cannot upgrade one
another. The release build is `com.grayguava.legato`.

The `legato://` links are the same in both, so a link opens whichever app the system picks;
that is worth changing if the two ever need to be told apart.

## The workflow

There is no workflow yet: the repository is local-only for now. When it goes public, a release
is a push to `main` that touches the `version` file, and it runs:

- **preflight** reads the version, derives the tag `v1.0.4`, and refuses to run if that tag
  already exists.
- **test** runs the unit tests.
- **build** checks the secrets, decodes the key, assembles, refuses to publish an APK signed
  with the debug key, writes the checksum, and verifies it.
- **publish** verifies the checksum again and creates a **draft** release from a release-notes
  template, with the commit and the tag filled in.

The release is a draft on purpose: nobody sees it until the notes have been written and it is
published by hand.

The release carries two files:

```
v1.0.4_universal.apk
v1.0.4_universal.apk.sha256
```

## Trying an update

Build a release with a version of your own and the release key:

```
$env:LEGATO_KEYSTORE = "D:\DevSoft\Android\keys\legato-release.jks"
$env:LEGATO_KEYSTORE_PASSWORD = ...
$env:LEGATO_KEY_PASSWORD = ...
$env:LEGATO_VERSION = 1.0.5
.\gradlew.bat :legato:app:assembleRelease
```

The `LEGATO_VERSION` variable keeps the tracked version file untouched, which matters: a push
that changes that file starts a release. Uninstall the release app first, install the APK that
comes out, and it will find the newest release and put it in place.

Android asks before installing anything, and wants the "install unknown apps" permission for
the app doing the installing.
