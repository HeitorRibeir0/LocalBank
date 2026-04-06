import os
import json
import urllib.request
import subprocess

project_id = os.environ['FIREBASE_PROJECT_ID']
version_code = os.environ['VERSION_CODE']
version_name = os.environ['VERSION_NAME'].lstrip('v')
apk_url = os.environ['APK_URL']

print(f"Updating Remote Config: version {version_name} (code {version_code})")
print(f"APK URL: {apk_url}")

# Get access token via gcloud
token = subprocess.check_output(['gcloud', 'auth', 'print-access-token']).decode().strip()

base_url = f'https://firebaseremoteconfig.googleapis.com/v1/projects/{project_id}/remoteConfig'

# GET current config to get ETag
req = urllib.request.Request(base_url, headers={'Authorization': f'Bearer {token}'})
with urllib.request.urlopen(req) as r:
    etag = r.headers.get('ETag') or r.headers.get('etag') or '*'
    config = json.loads(r.read())

print(f"ETag: {etag}")

# Update only our parameters (preserves everything else)
params = config.get('parameters', {})
params['latest_version_code'] = {'defaultValue': {'value': version_code}}
params['latest_version_name'] = {'defaultValue': {'value': version_name}}
params['apk_download_url'] = {'defaultValue': {'value': apk_url}}
config['parameters'] = params

# PUT updated config
data = json.dumps(config).encode()
req = urllib.request.Request(
    base_url,
    data=data,
    headers={
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json; UTF-8',
        'If-Match': etag
    },
    method='PUT'
)
with urllib.request.urlopen(req) as r:
    print(f"Remote Config updated successfully (HTTP {r.status})")
