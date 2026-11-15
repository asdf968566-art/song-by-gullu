# Nothing uses reflection; the defaults are enough.
-dontwarn org.jetbrains.annotations.**

# WebRTC (libwebrtc) uses JNI and reflection into these classes; keep them.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
