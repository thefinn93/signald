#!/usr/bin/env python3
import requests
import os
import json

apiBase = os.getenv("CI_API_V4_URL")
token = os.getenv("CI_JOB_TOKEN")
projectID = os.getenv("CI_PROJECT_ID")
version = os.getenv("CI_COMMIT_TAG")

with open("releases/{}.md".format(version)) as f:
    description = f.read()

# This script is invoked from CI when a new tag is created
resp = requests.post("{}/projects/{}/releases".format(apiBase, projectID), headers={"Content-Type": "application/json", "Job-Token": token}, data=json.dumps({
    "name": version,
    "tag_name": version,
    "description": description,
}))

print(resp.text)

resp.raise_for_status()