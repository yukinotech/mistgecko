#!/usr/bin/env python3
"""Resolve release identity from the checked-out version file; no shell evaluation."""
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
values = dict(line.strip().split('=', 1) for line in (root/'version.properties').read_text().splitlines() if '=' in line)
version = values['versionName']
if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
    raise SystemExit('versionName must use stable MAJOR.MINOR.PATCH format')
code = int(values['versionCode'])
if not 0 < code < 2100000000:
    raise SystemExit('Invalid Android versionCode')
tag = 'v' + version
event_tag = os.environ.get('EVENT_TAG') or (os.environ.get('REF_NAME') if os.environ.get('REF_TYPE') == 'tag' else '')
if event_tag and event_tag != tag:
    raise SystemExit(f'Event tag must match version.properties: expected {tag}')
# Existing tags must identify this commit; never move a release tag to another source.
resolved = subprocess.run(['git','rev-parse','--verify',f'refs/tags/{tag}^{{commit}}'],cwd=root,capture_output=True,text=True)
if resolved.returncode == 0:
    head = subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    if resolved.stdout.strip() != head:
        raise SystemExit(f'{tag} already exists on another commit; bump the version')
print(f'version={version}\ntag={tag}')
