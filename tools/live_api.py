"""Explicitly invoked acceptance helper; credentials are supplied only through environment variables.

Never imported by the Android application. Output contains response data, not credentials/cookies.
Usage: python tools/live_api.py sales GET current-trip
Admin paths are relative to api/admin; sales paths are relative to api/sales.
For writes pass JSON on stdin. There are no automatic mutation retries.
"""
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://www.superkingmyanmar.com/public/"


class Api:
    def __init__(self, portal):
        self.portal = portal
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.call("GET", "sanctum/csrf-cookie", absolute=True)
        self.call("POST", "api/auth/login", {
            "login": os.environ[f"SK_{portal.upper()}_LOGIN"],
            "password": os.environ[f"SK_{portal.upper()}_PASSWORD"],
            "portal": portal, "remember": False,
        }, absolute=True)

    def call(self, method, path, body=None, absolute=False, key=None):
        headers = {"Accept": "application/json", "Origin": "https://www.superkingmyanmar.com",
                   "Referer": BASE + self.portal + "/", "X-Requested-With": "XMLHttpRequest"}
        csrf = next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), None)
        if csrf:
            headers["X-XSRF-TOKEN"] = urllib.parse.unquote(csrf)
        if key:
            headers["Idempotency-Key"] = key
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        url = BASE + (path if absolute else f"api/{self.portal}/{path}")
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=50) as response:
                content = response.read()
                return json.loads(content) if content else {}
        except urllib.error.HTTPError as error:
            try:
                result = json.loads(error.read())
            except ValueError:
                result = {"message": error.reason}
            raise RuntimeError(json.dumps({"status": error.code, "path": path, "error": result})) from None


if __name__ == "__main__":
    portal, method, path = sys.argv[1:4]
    try:
        api = Api(portal)
        payload = json.load(sys.stdin) if method in ("POST", "PUT") else None
        print(json.dumps(api.call(method, path, payload), ensure_ascii=True))
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
