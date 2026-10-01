import Foundation

enum Net {
    static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 20
        c.httpAdditionalHeaders = ["User-Agent": "Sangeet/1.0 (iOS music player)"]
        return URLSession(configuration: c)
    }()

    static func json(_ url: URL, headers: [String: String] = [:]) async throws -> Any {
        var req = URLRequest(url: url)
        headers.forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let (data, resp) = try await session.data(for: req)
        if let http = resp as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return try JSONSerialization.jsonObject(with: data)
    }

    static func url(_ base: String, _ query: [String: String]) -> URL {
        var c = URLComponents(string: base)!
        c.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        return c.url!
    }
}

extension Dictionary where Key == String, Value == Any {
    func str(_ k: String) -> String? {
        if let s = self[k] as? String, !s.isEmpty { return s }
        if let n = self[k] as? NSNumber { return n.stringValue }
        return nil
    }
    func dict(_ k: String) -> [String: Any]? { self[k] as? [String: Any] }
    func array(_ k: String) -> [[String: Any]]? { self[k] as? [[String: Any]] }
}

func htmlUnescape(_ s: String) -> String {
    s.replacingOccurrences(of: "&quot;", with: "\"")
        .replacingOccurrences(of: "&#039;", with: "'")
        .replacingOccurrences(of: "&apos;", with: "'")
        .replacingOccurrences(of: "&amp;", with: "&")
        .replacingOccurrences(of: "&lt;", with: "<")
        .replacingOccurrences(of: "&gt;", with: ">")
}
