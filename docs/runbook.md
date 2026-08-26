# Corlang — operations runbook

Every command needed to build, validate, ship, and inspect the live services, in one place.

Until now these lived scattered across `CLAUDE.md`, `docs/PENDING.md`, `docs/road-to-play.md`,
`docs/server-ai.md`, `server/ai-proxy/README.md` and inline comments in `functions/api/*.js`.
Finding "how do I read the invite emails" meant remembering which of six files it was in.

**This file is the index, not the explanation.** Each section links to the doc that says *why*.
Commands here are run from the repo root unless stated. Shell is Git Bash.

---

## 1. Build and test

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"     # Android Studio JBR = JDK 21

# the gate: build + every unit test
./gradlew :app:assembleSideloadDebug :app:testSideloadDebugUnitTest --console=plain

# the Play artifact (AAB, updater compiled out)
./gradlew :app:bundlePlayRelease --console=plain
```

> **`BUILD SUCCESSFUL` is not proof the tests ran.** Gradle reports `UP-TO-DATE` and skips them
> when no *task input* changed, and `releases/version.json` is not an input to any task. That is
> exactly how a mismatched `versionCode` shipped once: the gate that checks it was never
> executed. When the answer matters (before a release, before saying "tests pass"), force it:
>
> ```bash
> ./gradlew :app:testSideloadDebugUnitTest --rerun-tasks --console=plain
> ```
>
> Then read the count, not the word SUCCESSFUL:
>
> ```bash
> python -c "
> import glob,re,io
> t=f=0
> for p in glob.glob('app/build/test-results/**/*.xml', recursive=True):
>     m=re.search(r'tests=\"(\d+)\".*?failures=\"(\d+)\".*?errors=\"(\d+)\"',
>                 io.open(p,encoding='utf-8',errors='ignore').read(2000))
>     if m: t+=int(m.group(1)); f+=int(m.group(2))+int(m.group(3))
> print('tests=%d failures=%d' % (t,f))"
> ```

Flavours (`distribution` dimension): `sideload` (in-app updater) and `play` (updater compiled
out). See `CLAUDE.md` → Build & release.

---

## 2. Content validators

Run before content reaches the app. Detail in `docs/course-gold-book.md`; every check exists
because a defect got through once (`docs/error-registry.md`).

```bash
python tools/course/check_batch.py <files...>      # shared invariants: no URLs, no em/en dashes
python tools/course/check_hr.py                   # Croatian drift (Serbianisms)
python tools/course/check_pt.py                   # European Portuguese drift
python tools/course/check_deck_examples.py        # every card has a unique, cloze-able example
python tools/course/check_deck_sync.py            # deck vs lessons
python tools/course/check_wrapup.py               # lesson wrap-up shape
python tools/course/proctor.py                    # course-wide audit; run before shipping
python tools/course/build_language.py             # assemble authored batches into _index.json
```

Human review is the check no script can do. Build the workbook a native speaker marks up:

```bash
python tools/course/build_review_doc.py hr        # -> docs/review/hr-review-workbook.html
```

One self-contained HTML file holding the whole course with every answer key shown. What comes
back is a small JSON of flags keyed by content path. See `docs/review/README.md` for the id
scheme and how to apply the result.

Hosted, so progress can be watched instead of waited for — https://corlang-review.pages.dev :

```bash
python tools/course/build_review_doc.py hr --out server/review-site/public/hr/index.html
python tools/course/build_review_doc.py pt --out server/review-site/public/pt/index.html
cd server/review-site
npx wrangler pages deploy public --project-name corlang-review --branch main --commit-dirty=true

# what a reviewer has done so far (keys are review:<course>:<name>)
npx wrangler kv key get review:hr:ana --namespace-id 8c5dcd3be8d84707958c0c8a6b9a9881 --remote
```

Reviewers get `https://corlang-review.pages.dev/?k=<token>` and pick a course, or go straight in at `/hr/?k=` or `/pt/?k=`. Full detail, including adding a
second reviewer and taking the site down afterwards: `server/review-site/README.md`.

Also live: a `PostToolUse` hook (`tools/hooks/validate_content_json.py`) that warns the moment an
edited `content/**/*.json` stops parsing. It catches malformed JSON only, never content defects.

---

## 3. Release (sideload)

Full rules in `CLAUDE.md`. The ordering below is not optional.

```bash
# 1. READ the current values first. Never bump by substituting a remembered number:
grep -n "versionCode\|versionName" app/build.gradle.kts

# 2. edit app/build.gradle.kts -> bump versionCode AND versionName
# 3. build
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleSideloadDebug :app:testSideloadDebugUnitTest --console=plain

# 4. ship the APK
cp app/build/outputs/apk/sideload/debug/app-sideload-debug.apk releases/corlang.apk

# 5. update releases/version.json -- versionCode MUST equal the built APK's
# 6. RE-READ both to assert they agree:
grep versionCode app/build.gradle.kts releases/version.json

# 7. commit + push (the repo must stay public: the in-app updater fetches
#    raw.githubusercontent.com/.../releases/version.json)
```

---

## 4. Website (Cloudflare Pages, project `corlang`)

```bash
python tools/site/build_site.py          # regenerates site/ : index, /privacy/, /terms/, /requests/
npx wrangler pages deploy site --project-name corlang --branch main --commit-dirty=true
```

Never hand-edit anything under `site/` — it is generated output and the next build overwrites it.
Edit `tools/site/build_site.py` (page copy, CSS, JS) or `PRIVACY.md` / `TERMS.md`, then rebuild.

Verify what actually went live rather than trusting the deploy line:

```bash
curl -s https://corlang.app/ | grep -c "some phrase you just changed"
```

---

## 5. Reading the sign-ups (Cloudflare KV)

`--remote` is mandatory. Wrangler v4 reads **local** storage by default, so without it every
list comes back empty and looks precisely like a broken endpoint.

### Test invites — namespace `8126fcfb51954368a9ba136df17fb5af`

Keyed `invite:<device>:<email>`, so each list pulls on its own.

```bash
NS=8126fcfb51954368a9ba136df17fb5af

npx wrangler kv key list --namespace-id $NS --remote                          # everything
npx wrangler kv key list --namespace-id $NS --remote --prefix invite:android: # Android only
npx wrangler kv key list --namespace-id $NS --remote --prefix invite:ios:     # iPhone only

# one record (date, country code, device)
npx wrangler kv key get --namespace-id $NS --remote "invite:android:someone@example.com"

# remove one (a test entry, or an erasure request)
npx wrangler kv key delete --namespace-id $NS --remote "invite:ios:someone@example.com"
```

Keys beginning `rate:` are the per-IP hourly limiter and expire on their own — ignore them.
One legacy key is bare `invite:<email>`: it predates the device question and is Android.

### Language requests — namespace `f50b424a885f41d18d93978d31fec609`

Keyed `request:<language>:<email>`, so one person asking for two languages counts twice.

```bash
NS=f50b424a885f41d18d93978d31fec609
npx wrangler kv key list --namespace-id $NS --remote
npx wrangler kv key list --namespace-id $NS --remote --prefix request:german:
npx wrangler kv key get  --namespace-id $NS --remote "request:german:someone@example.com"
```

### Tally which language people actually want

The reason the endpoint exists: pick the next course from demand, not from a guess.

```bash
npx wrangler kv key list --namespace-id f50b424a885f41d18d93978d31fec609 --remote \
  | python -c "
import sys,json,collections
c=collections.Counter(k['name'].split(':')[1]
                      for k in json.load(sys.stdin) if k['name'].startswith('request:'))
for lang,n in c.most_common(): print('%4d  %s' % (n, lang))"
```

---

## 6. AI proxy worker (`server/ai-proxy`)

Worker changes are **not** in the APK; they need a deploy. Rationale in `docs/server-ai.md`.

```bash
cd server/ai-proxy
npx wrangler deploy                 # push worker.js
npx wrangler tail                   # live logs, for debugging a failing call
```

Secrets — never paste one into a chat, and never commit one:

```bash
npx wrangler secret list
npx wrangler secret put ANTHROPIC_API_KEY     # single-line values only
npx wrangler secret put APP_AUTH_TOKEN
```

> A **multi-line** secret (the Play service-account JSON) cannot go through `secret put`: the
> prompt is single-line and silently truncates at the first newline. Use bulk instead, from a
> file you delete afterwards:
>
> ```bash
> npx wrangler secret bulk secrets.json      # {"PLAY_SERVICE_ACCOUNT": "<the whole JSON string>"}
> rm secrets.json
> ```

Rate-limit counters live in KV namespace `7869cfd96a8f4851905855404e6d4df0` (binding `RATE_KV`),
one key per caller per day. The daily cap is `DAILY_LIMIT_PER_SUB` in `worker.js` (currently 30).

Check the Play subscription-verification path end to end:

```bash
python server/ai-proxy/check-play-access.py
```

It distinguishes an OAuth failure (bad key / API not enabled) from a 401/403 (service account
lacks permission) from a 400/404/410 (working correctly — the fake token was rejected as it
should be). Read its verdict line; two different 404s mean two different things.

---

## 7. Store assets

```bash
python docs/store-assets/make_assets.py     # icon 512, feature graphic 1024x500
python tools/store/make_store_shots.py      # raw captures -> 1080x1920 framed listing images
```

`make_store_shots.py` never edits the screenshot itself — Play requires listing images to show
the real app, so everything added lives outside the bezel. Capture guidance and the known-weak
shots: `docs/store-assets/README.md`.

---

## 8. Where things are

| Thing | Where |
|---|---|
| Authoring rules (the WHAT, ship checklist) | `docs/language-standard.md` |
| Authoring procedure (the HOW, stage by stage) | `docs/course-gold-book.md` |
| Every defect class ever found, and its check | `docs/error-registry.md` |
| Lesson composition spec | `tools/course/LESSON_SPEC.md` |
| Placement test spec | `tools/course/PLACEMENT_SPEC.md` |
| Launch status, single source of truth | `docs/PENDING.md` |
| Play Console walkthrough | `docs/road-to-play.md` |
| Data safety declarations, with evidence | `docs/play-data-safety.md` |
| Pricing / entitlement model | `docs/monetization-roadmap.md` |
| Model choice, token efficiency, workflow | `docs/WORKFLOW.md` |

| Account | Detail |
|---|---|
| Cloudflare Pages project | `corlang` → https://corlang.app |
| Worker | `corlang-ai-proxy` |
| KV: invites | `8126fcfb51954368a9ba136df17fb5af` |
| KV: language requests | `f50b424a885f41d18d93978d31fec609` |
| KV: AI rate limits | `7869cfd96a8f4851905855404e6d4df0` |
| Support address | support@corlang.app (Cloudflare Email Routing) |
