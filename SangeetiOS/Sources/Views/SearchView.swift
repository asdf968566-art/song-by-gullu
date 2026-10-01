import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var store: Store
    @State private var query = ""
    @State private var results: [Track] = []
    @State private var loading = false

    var body: some View {
        NavigationStack {
            List {
                if loading && results.isEmpty {
                    HStack { Spacer(); ProgressView(); Spacer() }.listRowSeparator(.hidden)
                }
                TrackList(tracks: results)
            }
            .listStyle(.plain)
            .navigationTitle("Search")
            .searchable(text: $query, prompt: "Songs, artists, movies")
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .task(id: query) {
                let q = query.trimmingCharacters(in: .whitespaces)
                guard q.count >= 2 else { results = []; return }
                try? await Task.sleep(for: .milliseconds(400))
                if Task.isCancelled { return }
                loading = true
                let r = await Catalog.search(q, store: store)
                if !Task.isCancelled { results = r }
                loading = false
            }
        }
    }
}
