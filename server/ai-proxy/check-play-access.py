# -*- coding: utf-8 -*-
"""
Prove the Play service account actually works, without printing any part of the key.

Why this exists: worker.js `verifySubscription` FAILS OPEN. A signing failure, a disabled API or
a missing Play Console permission all end in `return true`, so a broken setup does not raise an
error anywhere; it silently stops checking entitlement and lets everyone through. Nothing in the
app or the logs would tell you. This is the only way to find out.

Run it once after setting PLAY_SERVICE_ACCOUNT, and again if the key is ever rotated:

    python check-play-access.py "C:\\path\\to\\your-service-account.json"

It mints an OAuth token exactly the way the worker does, then calls the same subscriptions
endpoint with a deliberately invalid purchase token. What comes back separates the three failure
modes that otherwise look identical.

Needs `pip install pyjwt cryptography requests` (or run it anywhere those exist). It reads the
key file, sends the assertion to Google and nothing else, and prints only status codes.
"""
import json
import sys

try:
    import jwt                    # pyjwt
    import requests
except ImportError:
    sys.exit("Install first:  pip install pyjwt cryptography requests")

SCOPE = "https://www.googleapis.com/auth/androidpublisher"
TOKEN_URI = "https://oauth2.googleapis.com/token"
PACKAGE = "com.corlang.app"       # must match PACKAGE_NAME in worker.js


def main(path):
    with open(path, encoding="utf-8") as fh:
        sa = json.load(fh)

    email = sa.get("client_email", "")
    print("service account :", email or "MISSING client_email")
    print("package         :", PACKAGE)

    # 1. Does the key sign, and is the API enabled on its project?
    import time
    now = int(time.time())
    assertion = jwt.encode(
        {"iss": email, "scope": SCOPE, "aud": TOKEN_URI, "iat": now, "exp": now + 3600},
        sa["private_key"], algorithm="RS256",
    )
    r = requests.post(TOKEN_URI, data={
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion,
    }, timeout=30)

    if r.status_code != 200:
        print("\nOAuth FAILED", r.status_code)
        print(r.text[:300])
        print("\n-> The key itself, or the Google Play Android Developer API not being enabled")
        print("   on that Cloud project. Fix this before anything else.")
        return 1
    print("\nOAuth           : OK, the key signs and the API is enabled")

    # 2. Does Play Console actually let this account read purchases?
    url = ("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
           f"{PACKAGE}/purchases/subscriptionsv2/tokens/deliberately-invalid-token")
    r = requests.get(url, headers={"Authorization": "Bearer " + r.json()["access_token"]},
                     timeout=30)
    print("purchases call  :", r.status_code)

    if r.status_code in (400, 404, 410):
        print("\nALL GOOD. The token was rejected because it is fake, which is the point: the")
        print("call was authorised, so the account can read real purchases.")
        return 0
    if r.status_code in (401, 403):
        print("\nNOT AUTHORISED.", r.text[:200])
        print("\n-> Play Console, Users and permissions, invite this account's email and grant")
        print("   'View financial data, orders, and cancellation survey responses'. A fresh")
        print("   grant can take a little while to propagate, so retry once before digging.")
        return 1
    print("\nUnexpected:", r.text[:200])
    return 1


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    raise SystemExit(main(sys.argv[1]))
