import Foundation

enum SourceType: String, Codable {
    case jiosaavn, youtube, url
    var label: String {
        switch self {
        case .jiosaavn: return "JioSaavn"
        case .youtube: return "YouTube"
        case .url: return "Web"
        }
    }
}

/// A song. `id` is unique across the app: "<source>:<sourceId>".
struct Track: Codable, Identifiable, Hashable {
    var id: String
    var source: SourceType
    var sourceId: String
    var title: String
    var artist: String
    var album: String = ""
    var durationSec: Double = 0
    var artworkURL: String?
    /// JioSaavn: the 96 kbps media URL (quality is swapped at play time). Others: nil.
    var mediaURL: String?
    var has320: Bool = false
    /// "hindi", "punjabi", ... when known.
    var language: String = ""

    static func makeId(_ source: SourceType, _ sourceId: String) -> String { "\(source.rawValue):\(sourceId)" }

    func inLanguages(_ langs: [String]) -> Bool {
        if langs.isEmpty { return true }
        let l = language.isEmpty ? LanguageGuess.guess(title: title, artist: artist) : language
        return !l.isEmpty && langs.contains(l)
    }
}

struct OnlinePlaylist: Identifiable, Hashable {
    var id: String
    var title: String
    var subtitle: String
    var artworkURL: String?
}

/// Guess a song's language from its title / artist (Gurmukhi = Punjabi, Devanagari = Hindi, keywords).
enum LanguageGuess {
    static func guess(title: String, artist: String) -> String {
        let text = "\(title) \(artist)"
        if text.unicodeScalars.contains(where: { (0x0A00...0x0A7F).contains($0.value) }) { return "punjabi" }
        if text.unicodeScalars.contains(where: { (0x0900...0x097F).contains($0.value) }) { return "hindi" }
        let t = text.lowercased()
        let punjabi = ["punjabi", "sidhu", "diljit", "karan aujla", "ap dhillon", "shubh", "gurnam", "jass manak", "ammy virk"]
        let hindi = ["hindi", "bollywood", "arijit", "t-series", "shreya ghoshal", "atif aslam", "jubin", "neha kakkar",
                     "badshah", "kishore kumar", "lata mangeshkar", "sonu nigam", "zee music", "tips official", "yrf"]
        if punjabi.contains(where: t.contains) { return "punjabi" }
        if ["haryanvi", "masoom sharma"].contains(where: t.contains) { return "haryanvi" }
        if ["bhojpuri", "pawan singh", "khesari"].contains(where: t.contains) { return "bhojpuri" }
        if hindi.contains(where: t.contains) { return "hindi" }
        return ""
    }
}

enum AudioQuality: String, Codable, CaseIterable {
    case low, medium, high
    var label: String { self == .low ? "96 kbps" : self == .medium ? "160 kbps" : "320 kbps" }
}
