# BitChord+ — fork notes

Base: upstream `kushagrasinghx/BitChord` tag v1.10. Version name is `<upstream>-plusN`.

## What this fork changes (re-check these on every upstream merge)
- Package id `com.geven.bitchordplus`, app name "BitChord+" (app/build.gradle.kts, strings, manifest)
- AppUpdateChecker: never installs the official APK; `-plusN` is not a pre-release
- Queue: played "play next" songs stay as history (QueueCoordinator.keepPlayedUserQueueAsHistory), history shown by default, 100 kept
- Home refresh: HomeRefreshMix (Quick picks rebuilt from radios, shelves reshuffled), Top Picks pool 20, MainViewModel.onSongPlayedForPicks
- Lyrics translation: LyricsTranslation.kt (script-aware same-language check, line-by-line fallback) + TranslationFallbackTest
- Settings: original queue logic (played "play next" removed) and keep-queued-on-new-play toggles; Android Auto lyric translation (carLyricsTranslation)
- Lyrics outside the app: StatusBarLyrics.kt (Live Update), CarLyricsMetadata.kt + PlaybackService (car/lock screen/notification lines), settings rows in SettingsSheet
- Native build offline via local.properties oboe.dir / libusb.dir

## Merging a new upstream release
    git fetch origin --tags
    git merge vX.Y            # conflicts land in the files above
For every conflict (or upstream feature that overlaps one listed above) compare both
implementations and keep the better one; then bump versionName to `X.Y-plus1`,
run the tests, build `:app:assembleProdRelease`.
