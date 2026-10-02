# AI in NebulaGram

Open **NebulaGram settings → AI assistant**. Choose OpenAI, Anthropic, Google Gemini or an OpenAI-compatible server. Enter an API key, load the available models or type a model identifier, and optionally enter a system prompt. Custom servers need an HTTPS base URL including the API prefix, for example `/v1`.

Press **Save settings** to retain the configuration. Leaving the key field empty keeps the saved key; **Remove saved key** deletes it. Credentials are encrypted using Android Keystore and stored in the app's directory excluded from Android backups. They are excluded from settings exports and diagnostic reports.

**Ask AI** in a message menu opens the request editor with that message's text. Only pressing **Send request** sends the displayed text and system prompt to the chosen provider. The response appears locally and can be copied; it is never posted into Telegram automatically. AI conversation history is optional and local; its separate history setting controls whether conversations are retained. Closing the screen cancels its request. Access and model availability depend on the API account.

Protocol references: [OpenAI text generation](https://developers.openai.com/api/docs/guides/text), [OpenAI model list](https://developers.openai.com/api/reference/resources/models/methods/list), [Claude Messages](https://platform.claude.com/docs/en/api/messages/create), [Gemini generation](https://ai.google.dev/api/generate-content), [Gemini models](https://ai.google.dev/api/models).

The client uses OpenAI Responses with `store: false`, Claude Messages, Gemini `generateContent`, or Chat Completions for a custom compatible server. Automatic retries and credential-bearing redirects are disabled. Provider response bodies and request content are not logged. Protocol checks use local deterministic fixtures; they do not use a paid account or send real messages.


## AI in a chat (Android and iOS)

Open **Message tools → Settings → AI in chats** for a particular chat. On iOS the chat menu also contains **AI in this chat**. Global AI connection settings link to the composer shortcut setting; chat-specific translation is configured from that chat.

- **AI button in composer** adds tools on the right. Tap it to work with the current draft; hold it to open that chat's AI settings.
- **Incoming messages** automatically translates visible incoming text using the selected provider. Its target language is independent of the draft language. Turning it off restores original messages.
- **Translate while typing** previews a translation after a configurable 0.5, 1 or 2 second pause. **Apply** replaces only the matching current draft. It never sends a Telegram message, and editing or leaving the chat invalidates an older request.
- Both language pickers offer Russian and English first, followed by the language catalog. **Provider and model** opens the existing AI connection settings.

All three switches are off by default. Translation consent and languages are stored per account and chat, outside settings exports. Enabled automatic modes process text without an extra Run tap; remote providers receive that text, while a selected supported on-device model stays local. Incoming translation excludes outgoing, protected and secret content. Backgrounding or leaving the chat cancels pending work. Provider failures preserve original messages and the current draft.

These controls are wired in source; see the build manifests and `platform/ios/PARITY.md` for the exact validated revision and device-testing status.
