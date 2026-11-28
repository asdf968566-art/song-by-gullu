# Sangeet for iPhone (web app)

Open **https://asdf968566-art.github.io/song-by-gullu/** in Safari on the iPhone, tap **Share → Add to Home Screen**.
It opens full screen like an app: no App Store, no computer, no 7-day re-signing.

- The song catalog (`data/*.json`) is rebuilt every day by `.github/workflows/web.yml` from JioSaavn charts and playlists,
  because browsers can't call JioSaavn directly. Audio streams from JioSaavn's CDN and keeps playing on the lock screen.
- Search also asks YouTube; songs that aren't in the catalog play in the YouTube player (foreground only on iPhone).
