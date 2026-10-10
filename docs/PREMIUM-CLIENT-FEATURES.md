# Client feature inventory from the Telegram Premium references

Updated 2026-10-10. NebulaGram adds client tools; it does not mark the account as Premium or grant server entitlements. Source/build evidence and live-device/provider acceptance are separate.

| Screenshot feature | NebulaGram implementation or boundary |
|---|---|
| Voice/video-message transcription | Existing Gemini path expanded to independently selected OpenAI/Gemini service and model, protected recording checks, bounded upload and cancellation. Telegram transcription remains the native option. |
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

- Android/iOS: Nebula AI → Audio & voices. Choose a named OpenAI/Gemini connection and independent transcription/speech models. Device speech is explicitly selected, not a fallback to a different cloud service.
- Recording: Nebula transcription → original transcript / translate / summarize / read aloud. Android can replace the native transcription button through its existing Media transcription choice; iOS has a native context-menu entry.
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
