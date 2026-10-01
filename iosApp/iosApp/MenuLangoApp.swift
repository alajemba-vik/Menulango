import SwiftUI
import ComposeApp
import FirebaseAppCheck
import FirebaseCore
import MLKitTranslate
import MultipeerConnectivity
import UniformTypeIdentifiers

/// A thin shell: configuration in, the shared Compose app out.
@main
struct MenuLangoApp: App {
    init() {
        let info = Bundle.main.infoDictionary ?? [:]
        #if DEBUG
        let isDebug = true
        // Debug builds run from Xcode buy from RevenueCat's Test Store when a `test_` key is
        // set. TestFlight and App Store builds are Release and always use the real Apple key.
        let testKey = info["MenuLangoRevenueCatTestKey"] as? String ?? ""
        let revenueCatKey = testKey.isEmpty ? (info["MenuLangoRevenueCatKey"] as? String ?? "") : testKey
        #else
        let isDebug = false
        let revenueCatKey = info["MenuLangoRevenueCatKey"] as? String ?? ""
        #endif
        MainViewControllerKt.startMenuLango(
            proxyUrl: info["MenuLangoProxyURL"] as? String ?? "",
            revenueCatApiKey: revenueCatKey,
            isDebug: isDebug,
            betaTools: (info["MenuLangoBetaTools"] as? String) == "YES",
            noteTranslationBridge: MLKitNoteTranslationBridge(),
            backupFileBridge: BackupFileBridge(),
            appCheckBridge: AppCheckBridge(info: info),
            nearbyBridge: MultipeerTableBridge()
        )
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

/** Owns iOS's system share sheet and document picker; backup content is encrypted by shared Kotlin. */
private final class BackupFileBridge: NSObject, IosBackupFileBridge, UIDocumentPickerDelegate {
    private var importCallback: IosBackupImportCallback?

    func share(encoded: String, filename: String) {
        guard let data = Data(base64Encoded: encoded) else { return }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        do {
            try data.write(to: url, options: .atomic)
            guard let presenter = presenter() else { return }
            let shareSheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            presenter.present(shareSheet, animated: true)
        } catch {
            return
        }
    }

    func pick(callback: IosBackupImportCallback) {
        importCallback = callback
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.data])
        picker.delegate = self
        presenter()?.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        defer { importCallback = nil }
        guard let url = urls.first, let data = try? Data(contentsOf: url) else {
            importCallback?.onData(encoded: nil)
            return
        }
        importCallback?.onData(encoded: data.base64EncodedString())
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        defer { importCallback = nil }
        importCallback?.onData(encoded: nil)
    }

    private func presenter() -> UIViewController? {
        let root =
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first(where: \.isKeyWindow)?
                .rootViewController
        return visibleViewController(from: root)
    }

    private func visibleViewController(from controller: UIViewController?) -> UIViewController? {
        guard let controller else { return nil }
        if let presented = controller.presentedViewController { return visibleViewController(from: presented) }
        if let navigation = controller as? UINavigationController { return visibleViewController(from: navigation.visibleViewController) }
        if let tabs = controller as? UITabBarController { return visibleViewController(from: tabs.selectedViewController) }
        return controller
    }
}

/// Firebase App Check: each scan carries an App Attest token so the proxy can tell the real app
/// from a script. Configured from Info.plist; without those values scans are sent with no token.
private final class AppCheckBridge: NSObject, IosAppCheckBridge {
    private let enabled: Bool

    init(info: [String: Any]) {
        let appID = info["MenuLangoFirebaseAppID"] as? String ?? ""
        let apiKey = info["MenuLangoFirebaseAPIKey"] as? String ?? ""
        let projectID = info["MenuLangoFirebaseProjectID"] as? String ?? ""
        // The app id reads "1:<project number>:ios:<hash>"; the project number is the sender id.
        let parts = appID.split(separator: ":")
        if parts.count == 4, !apiKey.isEmpty, !projectID.isEmpty {
            AppCheck.setAppCheckProviderFactory(AppCheckProviders())
            let options = FirebaseOptions(googleAppID: appID, gcmSenderID: String(parts[1]))
            options.apiKey = apiKey
            options.projectID = projectID
            FirebaseApp.configure(options: options)
            enabled = true
        } else {
            enabled = false
        }
        super.init()
    }

    func token(callback: IosAppCheckCallback) {
        guard enabled else {
            callback.onToken(token: nil)
            return
        }
        AppCheck.appCheck().token(forcingRefresh: false) { token, _ in
            callback.onToken(token: token?.token)
        }
    }
}

/// Simulators cannot attest. Debug builds use the debug provider instead: it prints a token to the
/// Xcode console on first use, which is added under App Check → Manage debug tokens in Firebase.
private final class AppCheckProviders: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG
        return AppCheckDebugProvider(app: app)
        #else
        return AppAttestProvider(app: app)
        #endif
    }
}

/// Phones at one table, iPhone to iPhone, over Apple's MultipeerConnectivity (Wi-Fi and Bluetooth,
/// no internet): the host advertises the table, guests browse and ask to join, and nobody joins
/// until the host accepts. Everything is encrypted. Peers are named to Kotlin by a random key.
private final class MultipeerTableBridge: NSObject, IosNearbyBridge, MCSessionDelegate,
    MCNearbyServiceAdvertiserDelegate, MCNearbyServiceBrowserDelegate {
    /// Also listed under NSBonjourServices in Info.plist, as iOS requires.
    private static let service = "menulango-tbl"

    private var listener: IosNearbyListener?
    private var session: MCSession?
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?
    private var peers: [String: MCPeerID] = [:]
    private var keys: [MCPeerID: String] = [:]
    private var pending: [String: (Bool, MCSession?) -> Void] = [:]
    private var isHost = false

    func setListener(listener: IosNearbyListener) {
        self.listener = listener
    }

    func host(name: String) {
        isHost = true
        let session = start(name: name)
        let advertiser = MCNearbyServiceAdvertiser(peer: session.myPeerID, discoveryInfo: nil, serviceType: Self.service)
        advertiser.delegate = self
        advertiser.startAdvertisingPeer()
        self.advertiser = advertiser
    }

    func look(name: String) {
        isHost = false
        let session = start(name: name)
        let browser = MCNearbyServiceBrowser(peer: session.myPeerID, serviceType: Self.service)
        browser.delegate = self
        browser.startBrowsingForPeers()
        self.browser = browser
    }

    func join(peer: String) {
        guard let peerID = peers[peer], let session, let browser else { return }
        browser.invitePeer(peerID, to: session, withContext: nil, timeout: 30)
    }

    func answer(peer: String, accept: Bool) {
        pending.removeValue(forKey: peer)?(accept, accept ? session : nil)
    }

    func send(peer: String, base64: String) {
        guard let peerID = peers[peer], let session, let data = Data(base64Encoded: base64) else { return }
        try? session.send(data, toPeers: [peerID], with: .reliable)
    }

    func stop() {
        advertiser?.stopAdvertisingPeer()
        browser?.stopBrowsingForPeers()
        session?.disconnect()
        pending.values.forEach { $0(false, nil) }
        advertiser = nil
        browser = nil
        session = nil
        pending = [:]
        peers = [:]
        keys = [:]
    }

    private func start(name: String) -> MCSession {
        stop()
        let me = MCPeerID(displayName: name.isEmpty ? UIDevice.current.name : name)
        let session = MCSession(peer: me, securityIdentity: nil, encryptionPreference: .required)
        session.delegate = self
        self.session = session
        return session
    }

    private func key(for peer: MCPeerID) -> String {
        if let key = keys[peer] { return key }
        let key = UUID().uuidString
        keys[peer] = key
        peers[key] = peer
        return key
    }

    private func onMain(_ work: @escaping () -> Void) {
        DispatchQueue.main.async(execute: work)
    }

    // The host is asked; the diner answers on screen, so the handler waits until then.
    func advertiser(
        _ advertiser: MCNearbyServiceAdvertiser,
        didReceiveInvitationFromPeer peerID: MCPeerID,
        withContext context: Data?,
        invitationHandler: @escaping (Bool, MCSession?) -> Void
    ) {
        onMain {
            let key = self.key(for: peerID)
            self.pending[key] = invitationHandler
            self.listener?.joinAsked(peer: key, name: peerID.displayName)
        }
    }

    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        onMain { self.listener?.failed(peer: nil) }
    }

    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String: String]?) {
        onMain { self.listener?.tableFound(peer: self.key(for: peerID), name: peerID.displayName) }
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        onMain { self.listener?.tableLost(peer: self.key(for: peerID)) }
    }

    func browser(_ browser: MCNearbyServiceBrowser, didNotStartBrowsingForPeers error: Error) {
        onMain { self.listener?.failed(peer: nil) }
    }

    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        onMain {
            let key = self.key(for: peerID)
            switch state {
            case .connected:
                if !self.isHost { self.browser?.stopBrowsingForPeers() }
                self.listener?.connected(peer: key)
            case .notConnected:
                self.listener?.disconnected(peer: key)
            default:
                break
            }
        }
    }

    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        let text = data.base64EncodedString()
        onMain { self.listener?.received(peer: self.key(for: peerID), base64: text) }
    }

    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}

    func session(
        _ session: MCSession,
        didStartReceivingResourceWithName resourceName: String,
        fromPeer peerID: MCPeerID,
        with progress: Progress
    ) {}

    func session(
        _ session: MCSession,
        didFinishReceivingResourceWithName resourceName: String,
        fromPeer peerID: MCPeerID,
        at localURL: URL?,
        withError error: Error?
    ) {}
}

/// Translates one note at a time to keep ML Kit's on-device model memory bounded.
private final class MLKitNoteTranslationBridge: NSObject, IosNoteTranslationBridge {
    /// ML Kit loads its whole translation stack on first use, which froze the UI when done on the
    /// main thread (the waiter view hung on first open). All ML Kit work starts off the main thread.
    private let work = DispatchQueue(label: "menulango.translate", qos: .userInitiated)

    func prefetch(targetLanguageTag: String) {
        work.async { self.startPrefetch(targetLanguageTag: targetLanguageTag) }
    }

    private func startPrefetch(targetLanguageTag: String) {
        let conditions = ModelDownloadConditions(allowsCellularAccess: false, allowsBackgroundDownloading: true)
        [targetLanguageTag, Locale.current.identifier]
            .compactMap(supportedLanguage(for:))
            .reduce(into: [TranslateLanguage]()) { languages, language in
                if !languages.contains(language) { languages.append(language) }
            }
            .forEach { language in
                let model = TranslateRemoteModel.translateRemoteModel(language: language)
                _ = ModelManager.modelManager().download(model, conditions: conditions)
            }
    }

    func translate(
        notes: [IosTranslationNote],
        sourceLanguageTag: String,
        targetLanguageTag: String,
        callback: IosNoteTranslationCallback
    ) {
        work.async {
            self.startTranslation(
                notes: notes,
                sourceLanguageTag: sourceLanguageTag,
                targetLanguageTag: targetLanguageTag,
                callback: callback
            )
        }
    }

    private func startTranslation(
        notes: [IosTranslationNote],
        sourceLanguageTag: String,
        targetLanguageTag: String,
        callback: IosNoteTranslationCallback
    ) {
        guard let source = supportedLanguage(for: sourceLanguageTag),
              let target = supportedLanguage(for: targetLanguageTag) else {
            callback.onFailure()
            return
        }
        guard source != target else {
            callback.onSuccess(translations: notes)
            return
        }
        let modelManager = ModelManager.modelManager()
        let sourceModel = TranslateRemoteModel.translateRemoteModel(language: source)
        let targetModel = TranslateRemoteModel.translateRemoteModel(language: target)
        // Translation must never delay the waiter view. Background prefetch happens when the menu
        // opens; if either model is not ready now, retain the original note and use its fallback.
        guard modelManager.isModelDownloaded(sourceModel), modelManager.isModelDownloaded(targetModel) else {
            callback.onFailure()
            return
        }

        let translator = Translator.translator(
            options: TranslatorOptions(sourceLanguage: source, targetLanguage: target)
        )
        translate(notes, with: translator, at: 0, results: [], callback: callback)
    }

    private func supportedLanguage(for languageTag: String) -> TranslateLanguage? {
        let languageCode =
            Locale(identifier: languageTag).languageCode?.lowercased()
                ?? languageTag.split(separator: "-").first?.lowercased()
                ?? languageTag.lowercased()
        return TranslateLanguage.allLanguages().first { $0.rawValue.lowercased() == languageCode }
    }

    private func translate(
        _ notes: [IosTranslationNote],
        with translator: Translator,
        at index: Int,
        results: [IosTranslationNote],
        callback: IosNoteTranslationCallback
    ) {
        guard index < notes.count else {
            callback.onSuccess(translations: results)
            return
        }
        let note = notes[index]
        translator.translate(note.text) { [weak self] text, error in
            guard error == nil, let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                callback.onFailure()
                return
            }
            self?.translate(
                notes,
                with: translator,
                at: index + 1,
                results: results + [IosTranslationNote(key: note.key, text: text)],
                callback: callback
            )
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
