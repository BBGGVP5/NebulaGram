# AI in NebulaGram

Open **NebulaGram settings → AI assistant**. Choose OpenAI, Anthropic, Google Gemini or an OpenAI-compatible server. Enter an API key, load the available models or type a model identifier, and optionally enter a system prompt. Custom servers need an HTTPS base URL including the API prefix, for example `/v1`.

Press **Save settings** to retain the configuration. Leaving the key field empty keeps the saved key; **Remove saved key** deletes it. Credentials are encrypted using Android Keystore and stored in the app's directory excluded from Android backups. They are excluded from settings exports and diagnostic reports.

**Ask AI** in a message menu opens the request editor with that message's text. Only pressing **Send request** sends the displayed text and system prompt to the chosen provider. The response appears locally and can be copied; it is never posted into Telegram automatically. Request text and responses are not saved as chat history by this feature. Closing the screen cancels its request. Access and model availability depend on the API account.

Protocol references: [OpenAI text generation](https://developers.openai.com/api/docs/guides/text), [OpenAI model list](https://developers.openai.com/api/reference/resources/models/methods/list), [Claude Messages](https://platform.claude.com/docs/en/api/messages/create), [Gemini generation](https://ai.google.dev/api/generate-content), [Gemini models](https://ai.google.dev/api/models).

The client uses OpenAI Responses with `store: false`, Claude Messages, Gemini `generateContent`, or Chat Completions for a custom compatible server. Automatic retries and credential-bearing redirects are disabled. Provider response bodies and request content are not logged. Protocol checks use local deterministic fixtures; they do not use a paid account or send real messages.
