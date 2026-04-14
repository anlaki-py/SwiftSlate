# SwiftSlate

**A free, open-source AI text assistant that works everywhere on Android.**

Fork of [Musheer360/SwiftSlate](https://github.com/Musheer360/SwiftSlate).

---

SwiftSlate lets you fix, rewrite, translate, and transform text in any app without leaving what you're doing. Just type a short command at the end of your text and it gets replaced with the result instantly, right where you typed it.

No copy-pasting. No switching apps. It just works.

## Quick Examples

```
"Meeting is tommorow at 3 ?fix"       ->  "Meeting is tomorrow at 3."
"Can you send the report ?formal"     ->  "Could you please send the report at your earliest convenience?"
"Hello ?translate:es"                  ->  "Hola"
```

## What It Can Do

| Command | What it does |
|---------|-------------|
| `?fix` | Fix grammar and spelling |
| `?improve` | Make text clearer and better flowing |
| `?shorten` | Make it shorter |
| `?expand` | Add more detail |
| `?formal` | Rewrite in a professional tone |
| `?casual` | Rewrite in a casual tone |
| `?emoji` | Add fitting emojis |
| `?reply` | Generate a reply to the text |
| `?translate:XX` | Translate to any language (es, fr, de, ja, etc.) |
| `?undo` | Revert the last change |

You can also create your own custom commands, and set up instant text replacements that work offline (like expanding `?sig` into your full email signature).

## Getting Started

1. Download the APK from [Releases](https://github.com/anlaki-py/SwiftSlate/releases/latest)
2. Open the app and add an API key (free keys from [Google AI Studio](https://aistudio.google.com) or [Groq](https://console.groq.com))
3. Enable SwiftSlate in Accessibility Settings (there's a button on the Dashboard)
4. Disable battery optimization when prompted (keeps the service running reliably)
5. Start typing in any app

Supports Google Gemini, Groq, and any OpenAI-compatible provider including local models.

## Privacy

Text is only sent to the AI provider you choose. No analytics, no tracking, no other servers contacted. Text replacer commands run completely offline.

