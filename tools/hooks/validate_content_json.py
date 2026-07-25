#!/usr/bin/env python3
"""PostToolUse hook: warn when an edited course-content JSON file no longer parses.

Reads the Claude Code hook payload on stdin. No-op unless the edited file is a
`.json` under app/src/main/assets/content/. On a parse failure it prints a
`systemMessage` JSON object so the warning surfaces in the session UI. Malformed
content silently breaks ContentRepository at runtime, so catching it at write
time keeps the skeleton/content contract honest.
"""
import json
import os
import sys


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return  # No/!JSON stdin — nothing to validate.

    tool_response = payload.get("tool_response") or {}
    tool_input = payload.get("tool_input") or {}
    path = tool_response.get("filePath") or tool_input.get("file_path") or ""

    norm = path.replace("\\", "/")
    if "app/src/main/assets/content/" not in norm or not norm.endswith(".json"):
        return  # Not a content JSON file — no-op.

    try:
        with open(path, encoding="utf-8") as fh:
            json.load(fh)
    except FileNotFoundError:
        return  # Deleted/moved between the edit and the hook — nothing to check.
    except Exception as exc:
        name = os.path.basename(path)
        print(json.dumps({
            "systemMessage": (
                f"⚠ Invalid JSON in {name}: {exc}. "
                f"The app will fail to load this content until it is fixed."
            )
        }))


if __name__ == "__main__":
    main()
