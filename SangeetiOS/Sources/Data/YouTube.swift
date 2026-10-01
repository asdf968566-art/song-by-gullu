import Foundation

/// YouTube Data API v3 for search and India trending music (metadata only).
/// iOS can't stream YouTube audio, so playback finds the same song on JioSaavn.
enum YouTube {
    private static let base = "https://www.googleapis.com/youtube/v3"

    static func search(_ query: String, key: String) async throws -> [Track] {
        let url = Net.url("\(base)/search", ["part": "snippet", "type": "video", "videoCategoryId": "10",
                                              "regionCode": "IN", "maxResults": "25", "q": query, "key": key])
        return try await items(url) { ($0.dict("id"))?.str("videoId") }
    }

    static func trendingMusic(key: String) async throws -> [Track] {
        let url = Net.url("\(base)/videos", ["part": "snippet", "chart": "mostPopular", "videoCategoryId": "10",
                                              "regionCode": "IN", "maxResults": "50", "key": key])
        return try await items(url) { $0.str("id") }
    }

    private static func items(_ url: URL, id: ([String: Any]) -> String?) async throws -> [Track] {
        guard let root = try await Net.json(url) as? [String: Any] else { return [] }
        return (root.array("items") ?? []).compactMap { o in
            guard let vid = id(o), let sn = o.dict("snippet"), let raw = sn.str("title") else { return nil }
            let channel = (sn.str("channelTitle") ?? "").replacingOccurrences(of: " - Topic", with: "")
            let (artist, title) = splitTitle(htmlUnescape(raw), channel: channel)
            let thumbs = sn.dict("thumbnails")
            let art = ["maxres", "standard", "high", "medium", "default"].compactMap { thumbs?.dict($0)?.str("url") }.first
            return Track(id: Track.makeId(.youtube, vid), source: .youtube, sourceId: vid, title: title, artist: artist,
                         album: "YouTube", artworkURL: art, language: LanguageGuess.guess(title: raw, artist: channel))
        }
    }

    /// "Kesariya - Brahmastra | Ranbir | Arijit Singh" -> ("Arijit Singh"?, "Kesariya")
    static func splitTitle(_ raw: String, channel: String) -> (String, String) {
        var cleaned = raw.replacingOccurrences(
            of: #"\s*[\(\[][^\)\]]*(official|video|lyric|audio|full song|4k|8k|hd|visualizer)[^\)\]]*[\)\]]"#,
            with: "", options: [.regularExpression, .caseInsensitive])
        cleaned = cleaned.replacingOccurrences(of: #"@\S+"#, with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        if cleaned.contains(" | ") {
            let parts = cleaned.components(separatedBy: " | ").map { $0.trimmingCharacters(in: .whitespaces) }
            let title = parts[0].components(separatedBy: " - ")[0].trimmingCharacters(in: .whitespaces)
            let singers = ["Arijit", "Shreya", "Atif", "Jubin", "Neha Kakkar", "Sonu Nigam", "Honey Singh", "Badshah",
                           "Diljit", "Karan Aujla", "Sidhu", "AP Dhillon", "Shubh", "B Praak", "Darshan Raval", "Armaan Malik"]
            let singer = parts.dropFirst().first { p in singers.contains { p.localizedCaseInsensitiveContains($0) } }
            return (singer ?? channel, title.isEmpty ? cleaned : title)
        }
        if let r = cleaned.range(of: " - ") {
            let left = cleaned[..<r.lowerBound].trimmingCharacters(in: .whitespaces)
            let right = cleaned[r.upperBound...].trimmingCharacters(in: .whitespaces)
            if !left.isEmpty, !right.isEmpty { return (left, right) }
        }
        return (channel, cleaned)
    }
}
