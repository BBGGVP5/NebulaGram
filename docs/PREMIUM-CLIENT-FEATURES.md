# Client feature inventory from the Telegram Premium references

Updated 2026-10-10. NebulaGram adds client tools; it does not mark the account as Premium or grant server entitlements. Source/build evidence and live-device/provider acceptance are separate.

| Screenshot feature | NebulaGram implementation or boundary |
|---|---|
| Voice/video-message transcription | Existing Gemini path expanded to independently selected OpenAI/Gemini service and model, plus explicit on-device Gemini Nano/Android recognition. Protected recording checks, bounded media and cancellation are retained. Telegram transcription remains the native option. |
| AI voice translation | Original transcript retained; explicit translation to a selected language, summary and read-aloud actions. The original chat recording stays unchanged. |
| Text to natural speech | New OpenAI/Gemini generated WAV preview and explicit share/export. Separate voice, model, tone and pace. Installed system voices remain a separate choice with best-quality selection. Cloud audio is labelled AI-generated. API service/key/access may have its own costs; it is not Telegram's included transcription quota. |
| Real-time chat/channel translation | Existing incoming, outgoing and draft translation controls; explicit provider/language and protected-message exclusions. |
| AI editor and advanced formatting | Existing translate/styles/correction, rich attributed text/custom emoji preservation and explicit Apply. Supported native formatting is retained; server-only entity capabilities depend on Telegram. |
| Saved Messages tags | New account-scoped local labels with assign/remove/rename/delete, search by label name, browse linked message IDs and native message navigation. Labels store no message text/media, are not cloud reactions, and do not sync automatically. 64 labels, 8 per message, 3000 linked messages. |
| Task lists | Existing local tasks and reminders. Shared/server checklist editing still follows native Telegram permission checks. |
| Advanced chat management | Existing initial-tab/default-folder controls, folder panel style/placement/counters, account-scoped archive/filter behavior and local message retention. Server folder/pin quotas remain unchanged. |
| Premium app icons | Existing custom Nebula icons and imported icon packs; user-selectable client artwork. |
| Animated profile pictures, reactions, sticker/message effects | Existing native display/animation and client effect controls. Sending restricted reactions/stickers/custom emoji/effects and uploading restricted profile attributes still follow Telegram eligibility. |
| More accounts | Ten locally supported accounts are already available. This does not increase server channel/pin/public-link quotas. |
| Unlimited cloud storage and 4 GB uploads | Telegram cloud storage is already free; account-dependent per-file limits cannot be raised by the client. |
| Faster downloads | Transfer/network behavior remains native; server quotas, data center capacity and network bandwidth cannot be granted locally. |
| Stories limits, priority, incognito, viewer history | Server-owned account privileges are unchanged. Existing local story/archive visibility controls are distinct. |
| Business location/hours/greetings and cloud quick replies | Server Business configuration and recipient-facing behavior require actual Telegram eligibility. |
| Last-seen/read time while hiding own | Server privacy and availability are unchanged; no hidden information is fabricated. |
| Custom emoji sending, emoji statuses, name/profile colors, Premium badge | Native server entitlement checks remain. Client icon/theme choices do not alter another client's view of the account. |
| Wallpaper for both participants | Local wallpaper/appearance controls exist; recipient-side changes need Telegram support and permission. |
| Paid messages and no-forward/copy restrictions | Server payment/protection rules are retained. Local UI preferences do not collect Stars or change recipient access. |
| No sponsored messages | This batch does not modify Telegram's sponsored-message delivery. Browser ad blocking is an existing separate opt-in feature. |

## Where to find the additions

- Android/iOS: Nebula AI → Audio & voices. The transcription switch starts setup if no compatible connection is selected. The service popup offers saved connections plus Add OpenAI / Add Gemini even when only Gemini Nano exists. Saving this audio profile selects it for the requested operation; text-chat selection is unchanged. Independent transcription/speech models are editable. Device speech is explicitly selected, not a fallback to another cloud service.
- Recording: enable Nebula transcription in Audio & voices, choose/create the audio service, then use the recording transcription button or hold the message → Nebula transcription. The eligible native button opens Nebula while the switch is on; switching it off restores the native route. iOS updates its visible voice/round-video buttons on switch changes and return; normal videos use the menu. Transcript tools retain original text, translate, summarize and read aloud; Telegram Premium is not required for the independent service.
- Text tools: Read aloud opens the new speech preview, with explicit Generate, Stop, Listen and Share audio.
- Saved Messages: message context menu → Labels; Chats & tools → Saved Messages labels. Native cloud-message IDs are retained; deleted/unavailable messages remain subject to native navigation availability.

## Evidence

- Java production request/response and HTTPS fixture transport: 42 assertions; local label metadata: 20 assertions. Existing AI protocols/services and 78-setting checks pass.
- Final macOS bootstrap 38000181948 passed **143 XCTest cases**. Android run 38000181843 and full iOS IPA run 38000183038 succeeded at **028a35d**. Verified deliveries: APK 1000469 and iOS IPA 90; exact hashes, archive provenance and device limitations are recorded in [the implementation plan](superpowers/plans/2026-10-10-premium-client-and-audio.md).
- Physical rendering, live service model/key access, audio quality and native sharing acceptance remain pending until tested on actual devices. No generated paid API request was made from developer credentials.

## Primary references

- [Telegram Premium FAQ](https://telegram.org/faq_premium/)
- [OpenAI file transcription API](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create)
- [OpenAI text-to-speech](https://developers.openai.com/api/docs/guides/text-to-speech)
- [Gemini audio understanding](https://ai.google.dev/gemini-api/docs/audio)
- [Gemini speech generation](https://ai.google.dev/gemini-api/docs/speech-generation)


## Audio setup and header correction checkpoint

Source 77d7efa fixes a shared settings-link binder that replaced custom long presses. Service/role/label holds now keep their feature action; plain settings rows still copy their navigation link. Direct audio service creation, explicit transcription controls and instructions were added on both platforms. Android patch 0193 restores the original chat back/avatar material drawables with search/selection fading. 144 XCTest cases passed. Verified deliveries are APK **1000478** with these corrections and local speech, plus unsigned iOS IPA **92** at 9710eae with the matching audio-setup fixes. Exact artifact evidence is tracked in [the correction plan](superpowers/plans/2026-10-10-audio-settings-and-header-fixes.md).


## On-device voice/video recording transcription

- Android transcription choices now include **Gemini Nano on device (Advanced)** and **Android on device (Basic)**. They use the dedicated ML Kit Speech Recognition SDK, independent of the text Prompt API; audio models are not inferred from text Nano readiness. No API key or automatic cloud fallback is used for these modes.
- The October 7 Google guide documents Advanced audio for Pixel 10/11 and Basic on Android 12+. Runtime availability is checked per language; the explicit Local model screen can download the required model. The Advanced choice is limited to the documented Google device families to avoid mislabelling a Basic fallback as Nano. [Google Speech Recognition guide](https://developers.google.com/ml-kit/genai/speech-recognition/android).
- Existing OGG voice notes and MP4 round/video messages are decoded locally, mixed/resampled to **16 kHz mono PCM16**, and piped at real-time rate. Maximum 14 MB / 10 minutes. Partial text is displayed but only finalized speech is published, and early/incomplete processing is rejected. Stop, leaving the screen and backgrounding cancel processing and remove temporary PCM.
- The completed transcript uses the existing translation/summary/speech actions. Their engines remain explicit separate choices. Gemini Nano audio is Android-only; iOS does not provide Google's AICore runtime.
- The iOS continuation adds **Apple on-device transcription** as a separate choice, recording-language and permission/model status, and normal video-audio eligibility. OGG/MP4 use Telegram's existing software decoder; local Speech requests require a supported on-device model and have no implicit cloud fallback. Up to 14 MB / 10 minutes; only all finalized segments become a result. Source/compile/device evidence is recorded in [the iOS local audio plan](superpowers/plans/2026-10-10-ios-local-recording-transcription.md); IPA 92 predates this addition.
- **122 PCM/pacing/transcript/policy assertions**, real Kotlin coroutine deadline/cancellation tests and compilation against pinned official Android/ML Kit SDKs pass. Live speech quality and device-model availability remain unverified because the phone is unavailable. Verified delivery: **APK 1000478** at d3a475c, run 38037535453. Exact digest/signature evidence is tracked in [the Nano implementation plan](superpowers/plans/2026-10-10-nano-recording-transcription.md).
