# Play Console — Data safety answers

Fill this in at **Play Console → App content → Data safety**. Every answer below is derived from
what the code actually does, not from what the app intends; the evidence is named against each
one so a future change can be checked against it.

**The privacy policy URL for the form: `https://corlang.app/privacy/`**

> **Why we do not tick "No data collected".** It is the tempting answer, because no learning data
> ever leaves the phone. But the AI tutor sends what you type to our endpoint and on to Anthropic,
> and Google counts transmitted-off-device as collected. The ephemeral-processing exemption is
> not safe to lean on here: it requires the data to live only in memory for the life of the
> request, and a third-party provider sits in the path. A wrong "no" is a misdeclaration, which
> is an app-removal risk rather than a correction request. So we declare.

---

## 1. Overview questions

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | The AI tutor transmits typed text, the profile name, a progress snapshot, and a purchase token. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | App → Cloudflare Worker is HTTPS; Worker → Anthropic is HTTPS; billing is handled by Google Play. |
| Do you provide a way for users to request that their data be deleted? | **Yes** | `support@corlang.app`. In-app data is removed by uninstalling or by Settings → Reset progress; the endpoint's counters expire on their own within two days. |
| Does your app have an account creation feature? | **No** | There is no account, no sign-in, no user database. So the account-deletion URL requirement does not apply. |

---

## 2. Data types — declare exactly these four

### Personal info → **Name**
- **Collected:** Yes · **Shared:** No
- **Processed ephemerally:** No
- **Required or optional:** *Optional* (only if the learner uses the AI tutor)
- **Purpose:** App functionality
- **Evidence:** the profile name is placed in the tutor's system prompt (`TalkScreen.kt`), so it
  is transmitted. It is whatever the learner typed at setup, never verified, and any nickname
  works.

### Messages → **Other in-app messages**
- **Collected:** Yes · **Shared:** No
- **Processed ephemerally:** No
- **Required or optional:** *Optional*
- **Purpose:** App functionality
- **Evidence:** tutor chat text is POSTed to the proxy and forwarded to Anthropic
  (`server/ai-proxy/worker.js`). The worker writes **no** message content to storage, but
  Anthropic is in the path, so this is declared rather than claimed ephemeral.
- **Not "Shared":** Anthropic processes it on our behalf as a service provider, which Google's
  definition excludes from sharing. Nothing is sold or handed to an advertiser or data broker.

### Financial info → **Purchase history**
- **Collected:** Yes · **Shared:** No
- **Processed ephemerally:** No
- **Required or optional:** *Required* (for subscribers using the AI tutor)
- **Purposes:** App functionality, **and** Fraud prevention, security and compliance
- **Evidence:** the Play subscription token is sent with tutor requests so the worker can confirm
  an active subscription, and it forms part of two short-lived KV keys — a daily message counter
  (`rl:<day>:sub:<token>`, 2-day TTL) and a cached yes/no verdict (`play:verdict:<token>`, at most
  6 hours). That is storage, so it is declared.

### App activity → **App interactions**
- **Collected:** Yes · **Shared:** No
- **Processed ephemerally:** No
- **Required or optional:** *Optional* (only if the learner uses the AI tutor)
- **Purpose:** App functionality
- **Evidence:** `buildTutorContext` (`TalkScreen.kt`) puts a progress snapshot into every tutor
  request: the current lesson's title and objective, how many words the learner has met, and
  their most recent words. The model needs it to stay inside vocabulary the learner actually
  knows, which is the single biggest lever on the tutor being usable at all.
- **Added 2026-08-22, after the first three were drafted.** The original draft declared Name,
  Messages and Purchase history and missed this, because the snapshot is assembled inside the
  system prompt rather than being an obvious separate field. It is still user data about
  in-app behaviour leaving the device, which is what Google's definition turns on. Declaring it
  costs a line on the store listing; NOT declaring it is a misdeclaration, which is an
  app-removal risk rather than a correction request.
- **Not ephemeral**, for the same reason as the chat text: Anthropic is in the path, and
  ephemeral means memory-only for the life of the request.

---

## 3. Do NOT declare these, and the reason for each

| Type | Why not |
|---|---|
| Location | Never requested or accessed. No location permission in the manifest. |
| Email address, User IDs, Address, Phone | No account exists; none is ever asked for in the app. |
| Photos, Videos, Audio files, Music | No camera or microphone permission. The mic was removed entirely, and there is no `RECORD_AUDIO` in the manifest and no speech-recognition code. |
| Files and docs | Backup export/import uses the system file picker (SAF). The app receives only the one file the user chooses, and never browses storage. |
| Contacts, Calendar | Not requested. |
| App activity: **Searches**, **Installed apps**, **Other user-generated content**, **Other actions** | Only **App interactions** is declared, and only because of the tutor's progress snapshot. Nothing searches, nothing enumerates installed apps, and the learner's typed text is declared under Messages rather than twice. |
| App info and performance, Crash logs, Diagnostics | **No analytics or crash SDK of any kind is linked.** The only non-AndroidX dependency is Play Billing. Play's own Android vitals is Google's collection, not the developer's, and is not declared here. |
| Web browsing history | The app has no browser. |
| Device or other IDs | No advertising ID, Android ID, IMEI or MAC address is read. The endpoint does use the **IP address** as a rate-limit key for two days, but IP is not one of Play's declarable types, and it is used only for abuse prevention. It *is* disclosed in the privacy policy. |

---

## 4. The website is separate

`corlang.app` collects an email address through the "Ask for a test invite" dialog. **That is not
part of this form** — Data safety describes the app. It is disclosed in the privacy policy under
"The website", and it is the same address you will use for the deletion requests you just promised
to honour.

---

## 4b. Android Auto Backup is NOT a declaration

The app allows Android's Auto Backup (`allowBackup="true"`, with `backup_rules.xml` and
`data_extraction_rules.xml` naming `corlang.db` and `datastore/`), so a reinstall restores the
learner's lessons and profile from **their own** Google Drive.

**This does not add anything to this form.** Auto Backup is a platform feature operated by Google
between the user and their own account; the developer never receives, stores or can reach the
data, and Google's Data safety guidance does not treat it as developer collection. What it DID
require was fixing the privacy policy, which claimed backups were "never created automatically
and are never sent anywhere" while this was switched on. Corrected 2026-08-22 after a field
report: a clean uninstall, reinstall and fresh APK still restored the learner's name.

## 5. Keep this true

If any of the following changes, this form has to be revisited **before** the next release:

- adding any analytics, attribution or crash-reporting SDK (that turns several "no"s into "yes");
- storing anything durable about a learner on a server;
- adding accounts or sign-in (that triggers Google's account-deletion URL requirement);
- restoring the microphone or any speech-input feature;
- sending progress or review data anywhere, which today never leaves the device.
