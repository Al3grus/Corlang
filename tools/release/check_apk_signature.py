# -*- coding: utf-8 -*-
"""Is the artefact signed by OUR key?

Every sideload release from the first one up to v0.87.3 shipped `app-sideload-debug.apk`: an APK
signed with the shared ~/.android/debug.keystore, certificate `CN=Android Debug`. It installed
without complaint, the in-app updater replaced it happily, and nothing anywhere said the app was
signed by a key that identifies no developer and that every Android developer on earth also
holds. It surfaced only when Google's Android developer verification deadline (2026-09-30) made
the package-name/signing-key pair the thing that has to be registered - and a debug key cannot be.

Registry C30. So this checks the one thing nobody was looking at.

    python tools/release/check_apk_signature.py releases/corlang.apk
    python tools/release/check_apk_signature.py app/build/outputs/bundle/playRelease/app-play-release.aab

Exit 0 = signed by the Corlang release key. Anything else is a hard failure.
"""
import glob
import os
import re
import subprocess
import sys

# A certificate fingerprint is public by construction - it ships inside every APK - so it belongs
# in the repo. The KEYSTORE never does.
#
# SHIPPING is the key a learner's phone sees on a sideload install, and the one registered with
# Google for developer verification. It is the only key this repo may sign an APK with.
SHIPPING = ("1ad87d86e25e8bab7afb30014f33277cc53b4387b5a1d0bd4e63bd6857128524",
            "corlang-release-be.jks, CN=Corlang O=Corlang C=BE")

# Retired, but not dead. Play re-signs every upload with its OWN app signing key, so a bundle's
# signature proves only "this upload is from Corlang" and never reaches a phone. If Play already
# recorded the 2026-07-16 PT certificate as the upload key for com.corlang.app, that key must keep
# signing bundles until Google resets it, and failing the AAB over it would be wrong. So bundles
# accept either key; an APK accepts only SHIPPING.
RETIRED_UPLOAD = {
    "e06a369ba0f091e4c1e74691ff84ff73a40ecb395b339183818038bc32835539":
        "the retired 2026-07-16 upload key (CN=Corlang O=Corlang C=PT)",
}

# What we shipped by mistake for 229 releases. Named so the failure message can say so.
ANDROID_DEBUG_CN = "CN=Android Debug"


def norm(fp):
    return fp.replace(":", "").replace(" ", "").lower()


def find_apksigner():
    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.path.expanduser("~/AppData/Local/Android/Sdk"),
        os.path.expanduser("~/Android/Sdk"),
        os.path.expanduser("~/Library/Android/sdk"),
    ]
    found = []
    for root in roots:
        if not root:
            continue
        for name in ("apksigner.bat", "apksigner"):
            found += glob.glob(os.path.join(root, "build-tools", "*", name))
    # Highest build-tools version wins.
    found.sort(key=lambda p: [int(x) for x in re.findall(r"\d+", os.path.basename(os.path.dirname(p)))])
    return found[-1] if found else None


JBR_FALLBACKS = [
    r"C:\Program Files\Android\Android Studio\jbr",
    "/c/Program Files/Android/Android Studio/jbr",
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home",
]


def ensure_java_home():
    """apksigner and keytool both need a JDK; forgetting the export should not read as 'unsigned'."""
    if os.environ.get("JAVA_HOME") and os.path.isdir(os.environ["JAVA_HOME"]):
        return
    for cand in JBR_FALLBACKS:
        if os.path.isdir(cand):
            os.environ["JAVA_HOME"] = cand
            return


def run(cmd):
    ensure_java_home()
    return subprocess.run(cmd, capture_output=True, text=True)


def certs_of_apk(path):
    """(fingerprints, subjects) from apksigner - the only tool that reads v2/v3 signatures."""
    tool = find_apksigner()
    if not tool:
        fail("apksigner not found. Set ANDROID_HOME, or install Android SDK build-tools.")
    out = run([tool, "verify", "--print-certs", path])
    text = out.stdout + out.stderr
    fps = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", text)
    dns = re.findall(r"certificate DN:\s*(.+)", text)
    if not fps:
        fail("apksigner found NO signature in %s.\n%s" % (path, text.strip()[:600]))
    return fps, dns


def certs_of_aab(path):
    """An AAB carries a v1 (JAR) signature; apksigner does not read bundles, keytool does."""
    ensure_java_home()
    java_home = os.environ.get("JAVA_HOME", "")
    keytool = os.path.join(java_home, "bin", "keytool") if java_home else "keytool"
    out = run([keytool, "-printcert", "-jarfile", path])
    text = out.stdout + out.stderr
    fps = re.findall(r"SHA256:\s*([0-9a-fA-F:]+)", text)
    dns = re.findall(r"Owner:\s*(.+)", text)
    if not fps:
        fail(
            "No signature found in %s.\nA bundle built without keystore.properties is unsigned "
            "and Play will reject it.\n%s" % (path, text.strip()[:600])
        )
    return fps, dns


def fail(msg):
    print("FAIL: " + msg)
    sys.exit(1)


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    path = argv[1]
    if not os.path.exists(path):
        fail("no such artefact: %s" % path)

    is_bundle = path.endswith(".aab")
    fps, dns = certs_of_aab(path) if is_bundle else certs_of_apk(path)

    accepted = {SHIPPING[0]: SHIPPING[1]}
    if is_bundle:
        accepted.update(RETIRED_UPLOAD)

    for fp, dn in zip(fps, dns + [""] * len(fps)):
        if norm(fp) in accepted:
            print("OK  %s" % os.path.basename(path))
            print("    signed by %s" % accepted[norm(fp)])
            if norm(fp) != SHIPPING[0]:
                print("    NOTE: a retired key, accepted only because Play re-signs uploads.")
                print("          An APK signed with it would FAIL this check.")
            return 0

    got = ", ".join(norm(f)[:16] + "..." for f in fps)
    debug = any(ANDROID_DEBUG_CN in d for d in dns)
    msg = "%s is NOT signed by a Corlang key.\n" % os.path.basename(path)
    msg += "    expected SHA-256 %s...\n" % SHIPPING[0][:16]
    msg += "    found            %s\n" % got
    for d in dns:
        msg += "    subject          %s\n" % d.strip()
    if debug:
        msg += (
            "\n    That is the shared Android DEBUG key. Build the release variant:\n"
            "        ./gradlew :app:assembleSideloadRelease\n"
            "        cp app/build/outputs/apk/sideload/release/app-sideload-release.apk releases/corlang.apk"
        )
    fail(msg)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
