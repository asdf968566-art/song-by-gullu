import SwiftUI

@main
struct SangeetApp: App {
    @StateObject private var store: Store
    @StateObject private var player: Player

    init() {
        let s = Store()
        _store = StateObject(wrappedValue: s)
        _player = StateObject(wrappedValue: Player(store: s))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(player)
                .tint(.accent)
                .preferredColorScheme(.dark)
        }
    }
}

extension Color {
    static let accent = Color(red: 1.0, green: 0.36, blue: 0.42)
}

struct ContentView: View {
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            FeedView()
                .tabItem { Label("For You", systemImage: "play.square.stack") }
                .tag(0)
            SearchView()
                .withMiniPlayer()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(1)
            LibraryView()
                .withMiniPlayer()
                .tabItem { Label("Library", systemImage: "music.note.list") }
                .tag(2)
            SettingsView()
                .withMiniPlayer()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(3)
        }
    }
}
