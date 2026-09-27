#!/usr/bin/env python3
"""
Send one push straight to a device, using only the service account.

Deliberately independent of the backend and the app: if this works, the Firebase project
and credentials are sound and any remaining problem is in QuickPool's own code. If it
fails, nothing downstream can possibly work.

Usage:
    python3 scripts_fcm_test.py                 # credentials check only
    python3 scripts_fcm_test.py <device-token>  # also sends a real push
"""
import json
import sys

import google.auth.transport.requests
import requests
from google.oauth2 import service_account

CREDS = "/Users/abhuday/Projects/QuickPool/firebase-service-account.json"
SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

with open(CREDS) as fh:
    project_id = json.load(fh)["project_id"]

creds = service_account.Credentials.from_service_account_file(CREDS, scopes=[SCOPE])
creds.refresh(google.auth.transport.requests.Request())
print(f"credentials OK  project={project_id}  token acquired")

if len(sys.argv) < 2:
    print("no device token given — skipping send. Pass one to test delivery.")
    sys.exit(0)

resp = requests.post(
    f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send",
    headers={"Authorization": f"Bearer {creds.token}"},
    json={"message": {
        "token": sys.argv[1],
        "notification": {"title": "QuickPool test", "body": "Push is working."},
        "android": {"priority": "HIGH", "notification": {"channel_id": "quickpool_rides"}},
    }},
    timeout=20,
)
print(resp.status_code, resp.text)
