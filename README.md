<div align="center">

<img src="playstore-icon.png" width="100" alt="SwiftSlate Icon" />

# SwiftSlate

System-wide AI text assistant for Android. Users may input a specific trigger, such as `?fix`, at the end of any text string to initiate an immediate replacement.

> "A more stable and refined fork of [Musheer Alam](https://github.com/Musheer360)'s original project"

[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)](#getting-started)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#tech-stack)
[![License: MIT](https://img.shields.io/badge/MIT-blue?style=flat-square)](LICENSE)

</div>

## Quick Demo

| You type | Result |
|:---------|:-------|
| `i dont no whats hapening ?fix` | `I don't know what's happening.` |
| `hey can u send me that file ?formal` | `Could you please share the file at your earliest convenience?` |
| `Hello, how are you? ?tr:es` | `Hola, ¿cómo estás?` |

## What It Does

SwiftSlate runs in the background and helps you write better text anywhere on your phone. Type your message, add a trigger at the end, and it replaces your text with the improved version. No copying, no switching apps.

Two kinds of commands:

- **AI commands** send your text to an AI service (Gemini, Groq, or your own) and return the result
- **Text replacer commands** work offline instantly, no API key needed

## Built-in Commands

| Trigger | What it does |
|:--------|:-------------|
| `?fix` | Fix grammar, spelling, and punctuation |
| `?improve` | Make it clearer and easier to read |
| `?shorten` | Cut it down while keeping the meaning |
| `?expand` | Add more detail |
| `?formal` | Sound more professional |
| `?casual` | Sound more relaxed and friendly |
| `?emoji` | Add relevant emojis |
| `?reply` | Generate a reply to what someone said |
| `?undo` | Go back to what you originally typed |
| `?tr:XX` | Translate to another language (e.g., `?tr:fr` for French) |

## Text Replacer Commands

Create your own shortcuts that insert text instantly, no internet needed:

| Trigger | Inserts |
|:--------|:--------|
| `?sig` | Your signature |
| `?ty` | A thank you message |
| `?addr` | Your address |

## AI Providers

| Provider | Notes |
|:---------|:------|
| Google Gemini | Free tier available |
| Groq | Fast, free tier available |
| Custom | Any OpenAI-compatible service, including local models like Ollama or LM Studio |

## Setup

1. Download the APK from [Releases](https://github.com/anlaki-py/SwiftSlate/releases/latest)
2. Install and open the app
3. Add an API key in the **Keys** tab (get one free from Google or Groq)
4. Enable SwiftSlate in your phone's Accessibility settings
5. Open any app, type your text, add a trigger like `?fix`, and done

## Custom Commands

Make your own triggers in the **Commands** tab. Choose between:

- **AI**: Sends text to the AI with your custom instructions
- **Text Replacer**: Instantly replaces your trigger with fixed text

Examples: `?eli5` to simplify, `?bullet` for bullet points, `?tldr` for a short summary

## Managing Multiple Keys

Add several API keys and SwiftSlate will rotate between them automatically. If one hits a rate limit, it switches to the next. Keys are encrypted on your device.

## Backup and Restore

Export your custom commands to a file and import them later. Useful when switching phones. API keys are not included in backups.

## Screens

- **Dashboard**: Check if the service is running
- **Keys**: Add and manage API keys
- **Commands**: View and edit your commands
- **Settings**: Pick your AI provider, change the trigger prefix, backup commands

## Privacy

- Only processes text when you type a trigger; everything else is ignored
- Never touches password fields
- Sends text only to your chosen AI provider
- No tracking or analytics
- API keys stay encrypted on your device

## Requirements

Android 6.0 or newer.

## Building

```bash
git clone https://github.com/anlaki-py/SwiftSlate.git
cd SwiftSlate
./gradlew assembleDebug
```

## Known Issues

- Some apps with unusual text fields may not work perfectly
- Some phone brands hide third-party accessibility services; search for "SwiftSlate" in your accessibility settings if you don't see it

## License

MIT License. See [LICENSE](LICENSE).

---

Made by [anlaki](https://anlaki.dev)
