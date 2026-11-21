# Music app: SimpMusic + internet radio

This is SimpMusic (GPL-3.0) with its `core` submodule vendored in (no `git submodule` step) and a new
`:radio` module for internet radio.

## Build
1. Android Studio (latest stable), SDK Platform 37, JDK 17 or newer.
2. Open this folder, let Gradle sync, run the `androidApp` configuration.
   Or from a terminal: `./gradlew :androidApp:assembleDebug`
3. GitHub Actions: `.github/workflows/android.yml` builds on push.

## What is new (milestone 1)
- `radio/` module: Radio Browser client, favourites/history store, play-a-station, Radio tab UI
- Radio tab in the bottom bar (Home, Radio, Library, Search)
- Player patches in `core/`: radio stream URLs open directly (no cache), MP3/AAC/Ogg extractors,
  Cast and Android Auto support for stations
- Android Auto: a "Radio" folder with Favourite, Recently played and Popular stations
- Station artwork: logo if the station has one, else `radio/src/main/res/drawable-nodpi/radio_art_N.jpg`
  (add more numbered images and they are used automatically)

## Not built yet
Sleep timer for radio, alarm, Near Me, ICY now-playing line in the station card, podcasts,
share, local files in the radio queue, erhu features, player-style chooser (Classic / Cards / Carousel).

## One-click build
- Windows: right-click the folder, open PowerShell, run `.\build.ps1`. The APK lands in `dist\`.
  If it fails it writes `build-errors.txt`; send me that file.
- Cloud: run `.\push-to-github.ps1` (needs the GitHub CLI). The "Build debug APK" workflow then builds on
  every push and you download the APK from the run's Artifacts.
