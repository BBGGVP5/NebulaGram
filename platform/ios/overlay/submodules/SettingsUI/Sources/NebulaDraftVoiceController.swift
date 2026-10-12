import UIKit
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

@available(iOS 15.0, *)
public final class NebulaDraftVoiceController: UIViewController {
    private let context: AccountContext, file: URL, duration: Double, start: Double, end: Double, localOnly: Bool
    private let isCurrent: () -> Bool, remember: (String) -> Void, apply: (String) -> Void
    private let theme: PresentationTheme, russian: Bool, changed: Bool
    private let editor = UITextView(), status = UILabel(), recognize = UIButton(type: .system), useText = UIButton(type: .system)
    private var task: Task<Void, Never>?, watch: Foundation.Timer?, generation = 0, ready = false, busy = false
    public init(context: AccountContext, file: URL, duration: Double, start: Double, end: Double, localOnly: Bool,
                initialText: String?, changed: Bool, isCurrent: @escaping () -> Bool, remember: @escaping (String) -> Void, apply: @escaping (String) -> Void) {
        self.context=context;self.file=file;self.duration=duration;self.start=start;self.end=end;self.localOnly=localOnly
        self.isCurrent=isCurrent;self.remember=remember;self.apply=apply;self.changed=changed
        let data=context.sharedContext.currentPresentationData.with { $0 };theme=data.theme;russian=data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(nibName:nil,bundle:nil);editor.text=initialText ?? "";ready=initialText?.isEmpty == false
    }
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    private func text(_ ru:String,_ en:String)->String { russian ? ru : en }
    public override func viewDidLoad() {
        super.viewDidLoad();title=text("Голосовое в текст","Voice to text");view.backgroundColor=theme.list.blocksBackgroundColor
        navigationItem.leftBarButtonItem=UIBarButtonItem(barButtonSystemItem:.close,target:self,action:#selector(close))
        navigationItem.rightBarButtonItem=UIBarButtonItem(title:text("Сервис","Service"),style:.plain,target:self,action:#selector(settings))
        editor.font = .preferredFont(forTextStyle:.body);editor.adjustsFontForContentSizeCategory=true
        editor.textColor=theme.list.itemPrimaryTextColor;editor.backgroundColor=theme.list.itemBlocksBackgroundColor;editor.layer.cornerRadius=20;editor.textContainerInset=UIEdgeInsets(top:16,left:12,bottom:16,right:12)
        status.numberOfLines=0;status.font = .preferredFont(forTextStyle:.subheadline);status.textColor=theme.list.itemSecondaryTextColor
        status.text=changed ? text("Запись или фрагмент изменились. Расшифруйте заново.","The recording or fragment changed. Transcribe it again.") : text("Проверьте результат перед отправкой.","Review the result before sending.")
        recognize.setTitle(text("Расшифровать","Transcribe"),for:.normal);recognize.addTarget(self,action:#selector(transcribe),for:.touchUpInside)
        useText.setTitle(text("Использовать текст","Use text"),for:.normal);useText.addTarget(self,action:#selector(use),for:.touchUpInside)
        let keep=UIButton(type:.system);keep.setTitle(text("Оставить голосовое","Keep voice message"),for:.normal);keep.addTarget(self,action:#selector(close),for:.touchUpInside)
        let stack=UIStackView(arrangedSubviews:[status,editor,recognize,useText,keep]);stack.axis = .vertical;stack.spacing=12;stack.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(stack)
        for button in [recognize,useText,keep] { button.heightAnchor.constraint(equalToConstant:48).isActive=true;button.titleLabel?.font = .preferredFont(forTextStyle:.headline);button.tintColor=theme.list.itemAccentColor;button.backgroundColor=theme.list.itemBlocksBackgroundColor;button.layer.cornerRadius=16 }
        let bottom=view.keyboardLayoutGuide.topAnchor
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:view.leadingAnchor,constant:16),stack.trailingAnchor.constraint(equalTo:view.trailingAnchor,constant:-16),stack.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor,constant:16),stack.bottomAnchor.constraint(equalTo:bottom,constant:-12),editor.heightAnchor.constraint(greaterThanOrEqualToConstant:100)])
        NotificationCenter.default.addObserver(self,selector:#selector(cancel),name:UIApplication.didEnterBackgroundNotification,object:nil)
        watch=Foundation.Timer.scheduledTimer(withTimeInterval:0.25,repeats:true) { [weak self] _ in guard let self else{return};if !self.isCurrent(){self.close()} }
        refresh();if !ready && !changed { transcribe() }
    }
    private func refresh(){recognize.isEnabled = !busy;useText.isEnabled = ready && !busy;editor.isEditable = !busy}
    @objc private func transcribe() {
        guard !busy,isCurrent() else{return}
        let audio=NebulaAudioPreferences.shared,local=audio.localTranscription,locale=audio.transcriptionLocale
        if localOnly && !local { status.text=text("Для секретного чата выберите распознавание на устройстве.","Choose on-device recognition for a secret chat.");return }
        do {
            let service=local ? nil : try NebulaAudioService()
            cancel();generation+=1;let token=generation;busy=true;ready=false;refresh()
            status.text=text("Распознаём выбранный фрагмент…","Transcribing selected fragment…")
            task=Task { @MainActor [weak self] in
                guard let self else{return}
                let directory=FileManager.default.temporaryDirectory.appendingPathComponent("nebula-draft-\(UUID().uuidString)",isDirectory:true)
                do {
                    try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true,attributes:[.posixPermissions:0o700,.protectionKey:FileProtectionType.complete])
                    defer { try? FileManager.default.removeItem(at:directory) }
                    let selected=try await NebulaDraftVoiceAudio.prepare(source:self.file,directory:directory,duration:self.duration,start:self.start,end:self.end,cloud:!local)
                    try Task.checkCancellation();guard token==self.generation,self.isCurrent() else{return}
                    let result:String
                    if local {
                        try await NebulaLocalTranscription.authorize();try NebulaLocalTranscription.requireAvailable(locale:locale)
                        result=try await NebulaLocalTranscription.transcribe(pcm:selected,directory:directory,locale:locale) { [weak self] value in
                            guard let self,self.generation==token,self.isCurrent() else{return};self.editor.text=value
                        }
                    } else if let service { result=try await service.transcribe(file:selected,mime:"audio/mp4") }
                    else { throw NebulaLocalAudioPolicy.Failure.incomplete }
                    try Task.checkCancellation();guard token==self.generation,self.isCurrent() else{return}
                    self.editor.text=result;self.ready = !result.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty;self.busy=false;self.task=nil;self.refresh()
                    self.status.text=self.text("Проверьте текст перед отправкой","Review the text before sending");if self.ready{self.remember(result)}
                } catch {
                    guard token==self.generation,!Task.isCancelled else{return};self.busy=false;self.task=nil;self.refresh()
                    self.status.text=local ? NebulaLocalTranscription.message(for:error,russian:self.russian) : NebulaAiService.message(for:error,russian:self.russian)
                }
            }
        } catch { status.text=text("Выберите локальное распознавание или ИИ-сервис в «Аудио и голоса».","Choose on-device recognition or an AI service in Audio & voices.") }
    }
    @objc private func use(){let value=editor.text.trimmingCharacters(in:.whitespacesAndNewlines);guard ready,!busy,!value.isEmpty,isCurrent() else{return};let check=isCurrent,action=apply;cancel();dismiss(animated:true){guard check() else{return};action(value)}}
    @objc private func settings(){cancel();navigationController?.pushViewController(NebulaAudioSettingsController(russian:russian,theme:theme,context:context),animated:true)}
    @objc private func close(){cancel();watch?.invalidate();watch=nil;dismiss(animated:true)}
    @objc private func cancel(){let wasBusy=busy;generation+=1;task?.cancel();task=nil;busy=false;if wasBusy{ready=false;status.text=text("Расшифровка отменена","Transcription cancelled")};if isViewLoaded{refresh()}}
    public override func viewWillDisappear(_ animated:Bool){super.viewWillDisappear(animated);cancel()}
    deinit { task?.cancel();watch?.invalidate();NotificationCenter.default.removeObserver(self) }
}
