
SwiftSlate is a free, open-source system-wide AI text assistant for Android.
This is a fork of the original [Musheer360/SwiftSlate](https://github.com/Musheer360/SwiftSlate).

## How It Works

SwiftSlate runs as an Android Accessibility Service, monitoring text input across all apps. When you type a trigger (e.g., `?fix`) at the end of a sentence, it sends the text to your chosen AI provider, then replaces your input with the processed result inline. No copy-pasting. No app switching.

**Processing Flow:**
1. Type text followed by a trigger command
2. SwiftSlate detects the trigger and shows a processing indicator
3. AI processes the text via your configured API (Gemini, Groq, or custom)
4. Original text is replaced with the AI result in the same input field

## Installation

**Requirements:** Android 6.0+ (API 23)

1. Download the latest APK from [Releases](https://github.com/Musheer360/SwiftSlate/releases/latest)
2. Install and open the app
3. Add an API key in the **Keys** tab (get free keys from [Google AI Studio](https://aistudio.google.com) or [Groq](https://console.groq.com))
4. Enable **SwiftSlate Assistant** in Android Accessibility Settings (via the Dashboard)
5. Start typing in any app

## Usage

Type your text, add a trigger at the end, and press space or wait briefly:

```
"Meeting is tommorow at 3 ?fix" → "Meeting is tomorrow at 3."
"Can you send the report ?formal" → "Could you please send the report at your earliest convenience?"
"Hello ?translate:es" → "Hola"
```

## Built-in Commands

| Trigger | Action |
|---------|--------|
| `?fix` | Correct grammar and spelling |
| `?improve` | Enhance clarity and flow |
| `?shorten` | Make concise |
| `?expand` | Add detail |
| `?formal` | Professional tone |
| `?casual` | Conversational tone |
| `?emoji` | Add relevant emojis |
| `?reply` | Generate contextual reply |
| `?translate:XX` | Translate (use language code: es, fr, de, ja, etc.) |
| `?undo` | Revert last change |

## Custom Commands

Create personal shortcuts in the **Commands** tab:

- **AI Commands**: Custom prompts (e.g., `?tl;dr` to summarize)
- **Text Replacers**: Instant offline replacements (e.g., `?sig` inserts your signature, `?addr` inserts your address). No API key required. No delay.

## Configuration

**API Keys:**
- Add multiple keys for automatic rotation if rate limits are hit
- Keys encrypted with AES-256-GCM using Android Keystore
- Supports Google Gemini, Groq, or any OpenAI-compatible endpoint (including local models via localhost/10.0.2.2)

**Provider Settings:**
Change provider and model in the **Settings** tab. For local LLMs (Ollama, LM Studio), select "Custom" and enter your endpoint URL.

## Privacy Note

Text is sent only to the configured AI provider. No other servers are contacted. No analytics or tracking. Commands run entirely offline if using Text Replacer type.

## License

MIT License. Original project by [Musheer360](https://github.com/Musheer360).