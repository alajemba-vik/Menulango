import SwiftUI
import ComposeApp
import MLKitTranslate
import UniformTypeIdentifiers

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
            betaTools: (info["MenuLangoBetaTools"] as? String) == "YES",
            noteTranslationBridge: MLKitNoteTranslationBridge(),
            backupFileBridge: BackupFileBridge()
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

/// Translates one note at a time to keep ML Kit's on-device model memory bounded.
private final class MLKitNoteTranslationBridge: NSObject, IosNoteTranslationBridge {
    func prefetch(targetLanguageTag: String) {
        guard let language = supportedLanguage(for: targetLanguageTag) else { return }
        let model = TranslateRemoteModel.translateRemoteModel(language: language)
        let conditions = ModelDownloadConditions(allowsCellularAccess: false, allowsBackgroundDownloading: true)
        _ = ModelManager.modelManager().download(model, conditions: conditions)
    }

    func translate(
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

        let translator = Translator.translator(
            options: TranslatorOptions(sourceLanguage: source, targetLanguage: target)
        )
        let conditions = ModelDownloadConditions(allowsCellularAccess: false, allowsBackgroundDownloading: true)
        translator.downloadModelIfNeeded(with: conditions) { [weak self] (error: Error?) in
            guard error == nil else {
                callback.onFailure()
                return
            }
            self?.translate(notes, with: translator, at: 0, results: [], callback: callback)
        }
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
