import SwiftUI

/// "For You": full-screen songs, swipe up for the next one (Resso style).
struct FeedView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var player: Player
    @State private var feed: [Track] = []
    @State private var pageId: String?
    @State private var loadingMore = false
    @State private var started = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if feed.isEmpty {
                ProgressView().tint(.white)
            } else {
                ScrollView(.vertical) {
                    LazyVStack(spacing: 0) {
                        ForEach(feed) { t in
                            FeedPage(track: t, active: player.current?.id == t.id) {
                                if player.current?.id == t.id { player.toggle() } else { start(at: t) }
                            }
                            .containerRelativeFrame([.horizontal, .vertical])
                            .id(t.id)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollIndicators(.hidden)
                .scrollPosition(id: $pageId)
                .ignoresSafeArea(edges: .top)
            }
        }
        .task { if feed.isEmpty { await loadMore() } }
        .onChange(of: pageId) { _, id in
            guard let id, let t = feed.first(where: { $0.id == id }) else { return }
            if started, player.current?.id != id { start(at: t) }
            if let i = feed.firstIndex(where: { $0.id == id }), i >= feed.count - 4 { Task { await loadMore() } }
        }
        .onChange(of: player.current?.id) { _, id in
            // Song finished on its own: move the feed along with it.
            guard let id, feed.contains(where: { $0.id == id }), pageId != id else { return }
            withAnimation { pageId = id }
        }
        .onChange(of: player.queue) { _, q in
            // Autoplay added songs at the end of the feed queue: show them too.
            if q.first?.id == feed.first?.id, q.count > feed.count { feed = q }
        }
    }

    private func start(at t: Track) {
        guard let i = feed.firstIndex(of: t) else { return }
        started = true
        player.play(feed, at: i)
    }

    private func loadMore() async {
        if loadingMore { return }
        loadingMore = true
        defer { loadingMore = false }
        let fresh = await Recommender(store: store).suggestions(count: 25, exclude: Set(feed.map(\.id)))
        guard !fresh.isEmpty else { return }
        store.markSeen(fresh.map(\.id))
        let wasEmpty = feed.isEmpty
        feed += fresh
        if wasEmpty { pageId = feed.first?.id }
        if player.queue.first?.id == feed.first?.id { player.append(fresh) }
    }
}

private struct FeedPage: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var player: Player
    let track: Track
    let active: Bool
    let onTap: () -> Void

    var body: some View {
        ZStack {
            ArtworkView(url: track.artworkURL, radius: 0)
                .blur(radius: 40)
                .overlay(Color.black.opacity(0.55))
                .ignoresSafeArea()

            VStack(spacing: 22) {
                Spacer()
                ArtworkView(url: track.artworkURL, radius: 14)
                    .aspectRatio(1, contentMode: .fit)
                    .padding(.horizontal, 36)
                    .shadow(radius: 20)
                    .onTapGesture(perform: onTap)
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(track.title).font(.title2.bold()).lineLimit(2)
                        Text(track.artist).font(.body).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                    }
                    Spacer()
                    Button { store.toggleLike(track) } label: {
                        Image(systemName: store.isLiked(track) ? "heart.fill" : "heart")
                            .font(.title)
                            .foregroundStyle(store.isLiked(track) ? Color.accent : .white)
                    }
                }
                .padding(.horizontal, 28)
                if active {
                    SeekBar().padding(.horizontal, 28)
                    Controls(big: false)
                } else {
                    Button(action: onTap) {
                        Image(systemName: "play.circle.fill").font(.system(size: 56))
                    }
                    .frame(height: 112)
                }
                Spacer()
            }
            .foregroundStyle(.white)
            .padding(.bottom, 40)
        }
        .trackMenu(track)
    }
}
