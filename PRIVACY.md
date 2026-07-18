# Corlang — Privacy Policy

**Effective date:** 2026-07-18

Corlang is built to be **private by design**. It has no account, no sign-in, no advertising,
no analytics, and no third-party tracking of any kind. This policy explains, plainly, what that
means.

## The short version

Your learning data never leaves your device unless *you* choose to move it. We do not collect,
transmit, sell, or share personal information — because we do not have an account system or any
server that stores your data.

## What Corlang stores, and where

All of the following is stored **only on your device**, in the app's private storage:

- Your profile: the name you enter at setup (any name or nickname you like, it is never
  verified) and which word forms the course uses for you.
- Your progress: streak, days completed, current level, per-day task checks.
- Your vocabulary review data (the spaced-repetition schedule for each word).
- Your quiz and mock-exam attempts and can-do self-checks.
- Your settings: daily goal, reminder time, and voice/haptics preferences.

We never upload any of this. There is no Corlang user database.

## When Corlang uses the network

Corlang works fully offline for learning. It makes network requests only in these cases:

1. **Update check.** On launch, the app fetches a small `version.json` file (and, if you choose
   to update, the app package) from the project's public GitHub repository over HTTPS. This is an
   ordinary file download. No personal data is sent; as with any web request, the server you
   download from can see standard request metadata such as your IP address.
2. **Optional AI practice (Premium).** If you use the AI tutor / AI writing feedback, the text you
   submit is sent through Corlang's managed endpoint to the AI provider (Anthropic) to generate a
   reply. What is sent: the text you type, the name from your profile (so the tutor can address
   you; any nickname works), and, for subscribers, your Google Play subscription token so the
   server can confirm an active subscription and apply the daily fair-use limit. No progress or
   review data is ever attached. If you never use the AI feature, no such request is ever made.
3. **Speech and audio.** Pronunciation playback and speech input use your device's built-in
   Android text-to-speech and speech-recognition services. Speech recognition may be processed by
   your device's speech provider (e.g. Google) according to your device settings — this happens
   outside Corlang and is governed by that provider's own policy. Corlang does not record, store,
   or transmit your audio itself.

## Backups

Backup and restore is entirely manual and local. When you export a backup, Corlang writes a file
to a location **you** pick; when you import, it reads a file **you** select. Backups are never
created automatically and are never sent anywhere.

## Permissions

- **Internet** — used only for the update check and the optional AI feature described above.
- **Microphone / record audio** — used only when you actively use speech-input exercises.

## Children

Corlang is a general-audience language-learning tool and is not directed at children. Regardless,
it collects no personal information from anyone.

## Changes to this policy

If this policy changes, the effective date above will be updated and the revised policy published
in this repository.

## Contact

Questions about privacy: **support@corlang.app**
