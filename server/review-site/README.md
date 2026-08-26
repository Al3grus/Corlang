# corlang-review — the hosted review workbook

Cloudflare Pages project serving the native-speaker workbook at
**https://corlang-review.pages.dev**, with per-reviewer progress saved server-side so it can be
watched while the review is happening rather than waited for at the end.

The same HTML works three ways, which is the point: opened from disk it is a local file, opened
at the bare URL it saves only in the browser, and opened with `?k=<token>` it mirrors every save
to KV. A reviewer whose link stops working can still be emailed the file and carry on.

## Handing it to a reviewer

Send them the link with their token:

```
https://corlang-review.pages.dev/?k=<their-token>
```

That link is the whole access model. One named reviewer, one long random link: no accounts to
build, nothing to reset, nothing for them to remember. The content behind it is already public
in this repo, so the only thing the token protects is the reviewer's own work from being
overwritten by a passer-by.

## Watching their progress

```bash
npx wrangler kv key get review:<name> --namespace-id 8c5dcd3be8d84707958c0c8a6b9a9881 --remote
```

Every save is stamped server-side with `savedAt`, which is how you tell "stopped for the night"
from "stopped three weeks ago". A reviewer's own clock is not evidence of when work landed.

`npx wrangler kv key list --namespace-id 8c5dcd3be8d84707958c0c8a6b9a9881 --remote` lists everyone
with saved work.

## Redeploying after content changes

```bash
python tools/course/build_review_doc.py hr --out server/review-site/public/index.html
cd server/review-site
npx wrangler pages deploy public --project-name corlang-review --branch main --commit-dirty=true
```

Saved progress is keyed by content path, not by position, so a redeploy does not invalidate work
already done. A flag on a word whose id has not changed still points at that word.

`public/` is gitignored: it is a 3.3 MB build artifact and belongs in the deploy, not in history.

## Adding a second reviewer

Tokens live in one secret as `name:token` pairs, so a second reviewer is a secret update and no
code change. The name becomes the KV key, which is what keeps two people reviewing the same
course from overwriting each other.

```bash
python -c "import secrets; print(secrets.token_urlsafe(24))"   # make a token
cd server/review-site
echo "ana:<token1>,marko:<token2>" | npx wrangler pages secret put REVIEW_TOKENS --project-name corlang-review
```

Setting the secret replaces it wholesale, so always pass the full list including the reviewers
who already have links.

## Taking it down

When the review is in and applied, delete the project. A workbook is a snapshot of content that
keeps moving, and a live one that nobody is auditing is just a stale copy waiting to be handed
to somebody.

```bash
npx wrangler pages project delete corlang-review
npx wrangler kv namespace delete --namespace-id 8c5dcd3be8d84707958c0c8a6b9a9881
```

## What is where

| | |
|---|---|
| `wrangler.toml` | project config and the `REVIEW_KV` binding |
| `functions/api/review.js` | `GET`/`PUT /api/review?k=…`, token check, KV read/write |
| `public/index.html` | the workbook (generated, gitignored) |
| KV namespace | `8c5dcd3be8d84707958c0c8a6b9a9881`, one `review:<name>` entry per reviewer |
| secret | `REVIEW_TOKENS`, comma-separated `name:token` pairs |
