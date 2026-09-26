import SwiftUI
import ComposeApp
import MLKitTranslate

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
            noteTranslationBridge: MLKitNoteTranslationBridge()
        )
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
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
