# Sangeet for iPhone

A separate SwiftUI version of Sangeet (the Android app in `Sangeet/` is unchanged).

- For You feed (swipe up for the next song), search, library, online charts and playlists, AI DJ, synced lyrics, lock-screen controls.
- Songs stream from JioSaavn. YouTube results are matched to the same song on JioSaavn, because iOS can't play YouTube audio.

## Install

CI builds an unsigned `Sangeet-iOS.ipa` (release `ios-latest`). Install it with
[Sideloadly](https://sideloadly.io) or [AltStore](https://altstore.io) using your Apple ID.
A free Apple ID has to re-sign the app every 7 days.

## Build locally (macOS)

```sh
brew install xcodegen
cd SangeetiOS && xcodegen generate && open Sangeet.xcodeproj
```
