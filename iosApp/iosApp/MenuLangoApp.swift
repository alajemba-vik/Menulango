import SwiftUI
import ComposeApp

/// A thin shell: configuration in, the shared Compose app out.
@main
struct MenuLangoApp: App {
    init() {
        let info = Bundle.main.infoDictionary ?? [:]
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        MainViewControllerKt.startMenuLango(
            proxyUrl: info["MenuLangoProxyURL"] as? String ?? "",
            revenueCatApiKey: info["MenuLangoRevenueCatKey"] as? String ?? "",
            isDebug: isDebug,
            betaTools: (info["MenuLangoBetaTools"] as? String) == "YES"
        )
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
