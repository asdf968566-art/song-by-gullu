# Sangeet 🎵

Android music player app (Kotlin + Jetpack Compose + Media3). Spotify jaisa look,
phone ke gaane + online free music, offline download aur synced lyrics.

## Features

- **Phone ke gaane**: albums, artists, poori list (MediaStore se)
- **For You feed (Resso jaisa)**: app khulte hi scroll feed — upar scroll karo, agla gaana apne aap, lyrics saath mein
- **Indian gaane**: JioSaavn (Hindi, Punjabi, Bollywood, 320 kbps tak) + YouTube / YouTube Music (NewPipeExtractor, bina key)
- **Online music**: Audius (bina key ke), Jamendo (free client_id), apna Navidrome/Subsonic server
- **Categories**: Bollywood Hits, Hindi Romantic, Punjabi Hits, Party, Lofi, Sad, Old is Gold, Haryanvi, Bhojpuri, Bhakti...
- **YouTube share**: YouTube app mein Share → Sangeet, gaana seedha bajega
- **Offline downloads**: online gaane app ke andar save hote hain
- **Lyrics**: LRCLIB se synced lyrics, ya apni `.lrc` file import karo
- **Playlists**: banao, rename karo, M3U8 export karo; M3U / PLS / CSV (Exportify) / TXT import
- **Player**: queue, shuffle, repeat, sleep timer, speed, skip silence, equalizer + bass boost
- **Discover feed**: reels jaisa — upar scroll karo, agla gaana apne aap bajta hai (aapke taste se)
- **Auto playlists**: Daily Mix, Artist Mix, On Repeat, Naye gaane, Bhoole-bisre — track record se apne aap bante aur update hote hain
- **Suggestions + Autoplay**: Home pe "Aapke liye", aur queue khatam hone pe milte-julte gaane chalte rehte hain
- **Open with Sangeet**: file manager / browser se koi bhi audio file ya link seedha isme bajao
- **Themes**: Classic Dark, Aurora, Glassmorphism, Neumorphism, AMOLED, Material You
- **Quality**: Wi-Fi / mobile data / download ke liye alag quality (128 / 256 / 320 kbps)

## APK kaise milega

### Sabse aasaan: Releases se
Repo ke **Releases** section me **Sangeet (latest build)** kholo aur `Sangeet.apk` download karke phone me install karo.
`main` pe har push ke baad ye APK apne aap update hota hai.

### GitHub Actions se (kisi bhi branch ka build)
1. Repo ka **Actions** tab kholo → **Build APK** workflow.
2. Sabse upar wala green ✓ run kholo.
3. Neeche **Artifacts** me `Sangeet-debug-apk` download karo, zip kholo, APK phone me install karo.

Har push pe APK apne aap banta hai. **Run workflow** button se haath se bhi chala sakte ho.

### Android Studio se
1. Android Studio me `Sangeet` folder kholo.
2. Gradle sync hone do, phir **Run ▶** dabao.

### Command line se
```bash
cd Sangeet
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Requirements
- Android 8.0 (API 26) ya upar
- Build ke liye JDK 17 aur Android SDK 35

## Testing (GitHub Actions)
Har push pe:
- **build**: APK banta hai
- **api-check**: Audius (gaane + stream) aur LRCLIB (lyrics) sach mein chal rahe hain ya nahi
- **emulator-test**: Android emulator pe APK install hota hai, asli gaana bajaya jaata hai aur check hota hai ki wo PLAYING state mein hai. Screenshots `emulator-screenshots-and-logs` artifact mein milte hain.

## YouTube Data API key (optional, free)
Bina key ke bhi YouTube chalta hai. Key doge to search aur India trending official API se aayenge:
1. https://console.cloud.google.com kholo → upar project dropdown → **New Project** banao
2. **APIs & Services → Library** → "YouTube Data API v3" search karo → **Enable**
3. **APIs & Services → Credentials → Create credentials → API key** → key copy karo
4. (Behtar) key pe **Restrict key → API restrictions → YouTube Data API v3** chuno
5. App mein **Settings → Music sources → YouTube** mein key daalo → **Save & test**

Free quota: 10,000 units/din. Search = 100 units (~100 search/din), trending = 1 unit. Credit card nahi chahiye.

> JioSaavn aur YouTube unofficial tareeke se chalte hain — ye app sirf personal use ke liye hai.
