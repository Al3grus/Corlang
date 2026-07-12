# Launch plan: from solo testing to Google Play

The ordered checklist for going public. Order matters: the in-app updater depends on the repo
being PUBLIC (raw.githubusercontent URLs), and the AI proxy secrets must never sit in a public
repo, so the steps below are sequenced to never break the running setup.

## Phase 0: now (solo testing)

Keep everything as-is: public repo, in-app updater, developer API key on-device. No secrets
live in the repo, so this is safe. Just keep testing.

## Phase 1: accounts (no code changes, do in any order)

1. Google Play Console developer account (one-time $25).
2. Cloudflare account for the AI proxy (free tier).
3. Anthropic account owned by Corlang with a fresh API key for the proxy.

## Phase 2: pre-publication audit

4. Run the evaluation prompts in docs/evaluation.md and fix what they surface.
5. Native-speaker review pass of the Croatian content (wife-approved export).
6. Privacy policy (hosted page): what's stored (all local), backups, and the AI disclosure
   (Premium text is processed by Anthropic via the Corlang proxy).

## Phase 3: the switch (this is the coupled, ordered part)

Do these together, in this order, in one release cycle:

7.  Make the GitHub repo PRIVATE. This immediately breaks the in-app updater for existing
    installs, which is fine because...
8.  Remove the in-app updater: update/Updater.kt, the UpdateDialog in MainActivity, the
    Settings "App updates" section, REQUEST_INSTALL_PACKAGES from the manifest, and
    releases/ from the repo. Play forbids self-updating apps anyway.
9.  Deploy the AI proxy (server/ai-proxy/README.md): wrangler secrets, deploy, get the URL.
10. Point the app at the proxy: AiConfig.proxyBaseUrl = worker URL; inject PROXY_AUTH_TOKEN
    from local.properties via BuildConfig (never hardcode it, even in the now-private repo).
11. Release build hardening: minifyEnabled true + shrinkResources, generate an upload
    keystore, switch from debug APKs to signed release AABs.

## Phase 4: Play + billing

12. Play Console: app listing, data-safety form (declare the AI text processing), content
    rating, privacy policy URL.
13. Billing: create the `premium` subscription (monthly + yearly), add billing-ktx, write
    billing/PlayBillingConnector.kt, add /v1/verify to the worker. Full detail:
    docs/server-ai.md, "Step 2".
14. Swap the Talk "Subscribe to Premium" dialog and the Settings Premium card for the real
    purchase flow. Remove (or DEBUG-gate) the dev-key entitlement fallback in PremiumManager.
15. Internal testing track first (your own device installs from Play now), then closed
    testing (wife + friends), then production.

## Phase 5: after launch

16. Decide pricing before the store listing goes live (anchors in docs/server-ai.md).
17. Monitor the worker's usage/cost dashboards; guardrails are built into the proxy.
18. Post-launch: updates ship through Play releases instead of the old updater.
