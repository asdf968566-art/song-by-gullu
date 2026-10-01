import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: Store
    @State private var cleared = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(Store.allLanguages, id: \.self) { lang in
                        Toggle(lang.capitalized, isOn: Binding(
                            get: { store.languages.contains(lang) },
                            set: { on in
                                if on { store.languages.append(lang) } else { store.languages.removeAll { $0 == lang } }
                            }
                        ))
                    }
                } header: { Text("Languages") } footer: { Text("Only songs in these languages are suggested.") }

                Section("Audio quality") {
                    Picker("Streaming", selection: $store.quality) {
                        ForEach(AudioQuality.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                }

                Section {
                    TextField("API key", text: $store.youtubeKey)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .font(.footnote.monospaced())
                } header: { Text("YouTube Data API") } footer: { Text("Used for search results. Leave empty to turn off.") }

                Section {
                    Button("Reset suggestions") { store.clearSeen(); cleared = true }
                } footer: {
                    Text(cleared ? "Done. Songs you've already heard can be suggested again." : "Songs you've heard are never suggested twice.")
                }

                Section {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                }
            }
            .navigationTitle("Settings")
        }
    }
}
