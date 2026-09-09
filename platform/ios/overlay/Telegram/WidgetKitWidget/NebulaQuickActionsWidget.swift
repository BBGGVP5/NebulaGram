#if arch(arm64) || arch(x86_64)
import SwiftUI
import WidgetKit
import BuildConfig

@available(iOSApplicationExtension 14.0, iOS 14.0, *)
private struct NebulaQuickEntry: TimelineEntry { let date: Date }
@available(iOSApplicationExtension 14.0, iOS 14.0, *)
private struct NebulaQuickProvider: TimelineProvider {
    func placeholder(in context: Context) -> NebulaQuickEntry { NebulaQuickEntry(date: Date()) }
    func getSnapshot(in context: Context, completion: @escaping (NebulaQuickEntry) -> Void) { completion(placeholder(in: context)) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<NebulaQuickEntry>) -> Void) {
        completion(Timeline(entries: [placeholder(in: context)], policy: .never))
    }
}
@available(iOSApplicationExtension 14.0, iOS 14.0, *)
private struct NebulaQuickView: View {
    private var ru: Bool { Locale.preferredLanguages.first?.hasPrefix("ru") == true }
    private func destination(_ path: String) -> URL {
        let bundle = Bundle.main.bundleIdentifier ?? ""
        let base = bundle.split(separator: ".").dropLast().joined(separator: ".")
        let scheme = BuildConfig(baseAppBundleId: base).appSpecificUrlScheme
        return URL(string: "\(scheme)://settings/nebula/\(path)")!
    }
    private var content: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("NebulaGram").font(.headline)
            Link(destination: destination("settings")) {
                Label(ru ? "Настройки" : "Settings", systemImage: "gearshape.fill")
            }
            Link(destination: destination("link")) {
                Label("NebulaLink", systemImage: "shield.lefthalf.filled")
            }
        }.font(.subheadline).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
    var body: some View {
        if #available(iOSApplicationExtension 17.0, iOS 17.0, *) {
            content.containerBackground(for: .widget) { Color(.secondarySystemBackground) }
        } else { content.padding().background(Color(.secondarySystemBackground)) }
    }
}
@available(iOSApplicationExtension 14.0, iOS 14.0, *)
struct NebulaQuickActionsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NebulaQuickActions", provider: NebulaQuickProvider()) { _ in NebulaQuickView() }
            .configurationDisplayName("NebulaGram")
            .description(Locale.preferredLanguages.first?.hasPrefix("ru") == true ? "Настройки и NebulaLink. Без содержимого переписки." : "Settings and NebulaLink. No message content.")
            .supportedFamilies([.systemMedium])
    }
}
#endif
