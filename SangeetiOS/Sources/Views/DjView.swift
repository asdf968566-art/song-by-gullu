import SwiftUI

struct DjView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var player: Player
    @State private var request = ""
    @State private var result: AiDj.Result?
    @State private var working = false

    private let examples = ["Sad Punjabi songs for a night drive", "90s Bollywood romantic", "Arijit Singh latest", "Gym workout Hindi", "Rainy day chill"]

    var body: some View {
        List {
            Section {
                HStack {
                    TextField("What do you want to hear?", text: $request)
                        .submitLabel(.go)
                        .onSubmit(make)
                    if working { ProgressView() } else {
                        Button(action: make) { Image(systemName: "sparkles") }.disabled(request.isEmpty)
                    }
                }
                if result == nil {
                    ForEach(examples, id: \.self) { e in
                        Button(e) { request = e; make() }.foregroundStyle(.secondary)
                    }
                }
            }
            if let r = result {
                Section {
                    if r.tracks.isEmpty {
                        Text("Nothing found. Try different words.").foregroundStyle(.secondary)
                    } else {
                        HStack(spacing: 12) {
                            Button { player.play(r.tracks) } label: { Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity) }
                            Button { store.createPlaylist(r.title, tracks: r.tracks) } label: { Label("Save", systemImage: "plus").frame(maxWidth: .infinity) }
                        }
                        .buttonStyle(.bordered)
                        TrackList(tracks: r.tracks)
                    }
                } header: { Text(r.title) }
            }
        }
        .navigationTitle("AI DJ")
    }

    private func make() {
        let q = request.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty, !working else { return }
        working = true
        Task {
            result = await AiDj.make(q, store: store)
            working = false
        }
    }
}
