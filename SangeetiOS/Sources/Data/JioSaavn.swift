import CommonCrypto
import Foundation

/// JioSaavn: Hindi, Punjabi, Bollywood and other Indian songs, up to 320 kbps. Personal use only.
enum JioSaavn {
    private static let base = "https://www.jiosaavn.com/api.php"

    private static func call(_ method: String, _ params: [String: String], langs: [String] = []) async throws -> Any {
        var q = ["__call": method, "_format": "json", "_marker": "0", "api_version": "4", "ctx": "web6dot0"]
        params.forEach { q[$0.key] = $0.value }
        let headers = langs.isEmpty ? [:] : ["Cookie": "L=\(langs.joined(separator: "%2C"))"]
        return try await Net.json(Net.url(base, q), headers: headers)
    }

    static func search(_ query: String, page: Int = 1) async throws -> [Track] {
        songs(try await call("search.getResults", ["q": query, "n": "40", "p": String(page)]))
    }

    static func trending(language: String) async throws -> [Track] {
        let list = songs(try await call("content.getTrending", ["entity_type": "song", "entity_language": language]))
        if list.count >= 10 { return list.map { var t = $0; if t.language.isEmpty { t.language = language }; return t } }
        return try await search("\(language) new songs")
    }

    /// JioSaavn's own "people who played this also played".
    static func similar(_ songId: String) async throws -> [Track] {
        songs(try await call("reco.getreco", ["pid": songId]))
    }

    static func charts(langs: [String]) async throws -> [OnlinePlaylist] {
        playlists(try await call("content.getCharts", [:], langs: langs))
    }

    static func featuredPlaylists(langs: [String], page: Int) async throws -> [OnlinePlaylist] {
        playlists(try await call("content.getFeaturedPlaylists", ["fetch_from_serialized_files": "true", "p": String(page), "n": "50"], langs: langs))
    }

    static func playlistTracks(_ id: String) async throws -> [Track] {
        songs(try await call("playlist.getDetails", ["listid": id, "n": "300", "p": "1"]))
    }

    /// 96 kbps URL -> requested quality.
    static func streamURL(_ t: Track, quality: AudioQuality) -> URL? {
        guard let raw = t.mediaURL else { return nil }
        let kbps: String
        switch quality {
        case .low: kbps = "96"
        case .medium: kbps = "160"
        case .high: kbps = t.has320 ? "320" : "160"
        }
        let s = raw.replacingOccurrences(of: #"_(96|160|320)\.(mp4|m4a|mp3)"#, with: "_\(kbps).$2", options: .regularExpression)
        return URL(string: s)
    }

    // MARK: parsing

    private static func items(_ root: Any, keys: [String]) -> [[String: Any]] {
        if let a = root as? [[String: Any]] { return a }
        if let d = root as? [String: Any] {
            for k in keys { if let a = d.array(k) { return a } }
        }
        return []
    }

    private static func songs(_ root: Any) -> [Track] {
        var seen = Set<String>()
        return items(root, keys: ["results", "list", "songs", "data"]).compactMap(track).filter { seen.insert($0.id).inserted }
    }

    private static func track(_ o: [String: Any]) -> Track? {
        if let type = o.str("type"), type != "song" { return nil }
        guard let id = o.str("id") else { return nil }
        let info = o.dict("more_info") ?? [:]
        guard let enc = info.str("encrypted_media_url") ?? o.str("encrypted_media_url"), let media = decrypt(enc) else { return nil }
        let artists = (info.dict("artistMap")?.array("primary_artists") ?? []).compactMap { $0.str("name") }.joined(separator: ", ")
        guard let title = o.str("title") ?? o.str("song") else { return nil }
        return Track(
            id: Track.makeId(.jiosaavn, id), source: .jiosaavn, sourceId: id,
            title: htmlUnescape(title),
            artist: htmlUnescape(artists.isEmpty ? (info.str("music") ?? o.str("subtitle") ?? "Unknown") : artists),
            album: htmlUnescape(info.str("album") ?? o.str("album") ?? ""),
            durationSec: Double(info.str("duration") ?? o.str("duration") ?? "") ?? 0,
            artworkURL: o.str("image")?.replacingOccurrences(of: "150x150", with: "500x500"),
            mediaURL: media,
            has320: (info.str("320kbps") ?? o.str("320kbps")) == "true",
            language: (o.str("language") ?? "").lowercased()
        )
    }

    private static func playlists(_ root: Any) -> [OnlinePlaylist] {
        items(root, keys: ["data", "results"]).compactMap { o in
            if let type = o.str("type"), type != "playlist" { return nil }
            guard let id = o.str("id") ?? o.str("listid"), let title = o.str("title") ?? o.str("listname") else { return nil }
            return OnlinePlaylist(id: id, title: htmlUnescape(title), subtitle: htmlUnescape(o.str("subtitle") ?? ""),
                                  artworkURL: o.str("image")?.replacingOccurrences(of: "150x150", with: "500x500"))
        }
    }

    /// Media URLs are DES-ECB encrypted with the key "38346591".
    static func decrypt(_ encrypted: String) -> String? {
        guard let data = Data(base64Encoded: encrypted.trimmingCharacters(in: .whitespacesAndNewlines)) else { return nil }
        let key = Array("38346591".utf8)
        var out = [UInt8](repeating: 0, count: data.count + kCCBlockSizeDES)
        var outLen = 0
        let status = data.withUnsafeBytes { raw in
            CCCrypt(CCOperation(kCCDecrypt), CCAlgorithm(kCCAlgorithmDES), CCOptions(kCCOptionPKCS7Padding | kCCOptionECBMode),
                    key, kCCKeySizeDES, nil, raw.baseAddress, data.count, &out, out.count, &outLen)
        }
        guard status == kCCSuccess else { return nil }
        return String(bytes: out.prefix(outLen), encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "http://", with: "https://")
    }
}
