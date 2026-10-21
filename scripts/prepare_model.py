#!/usr/bin/env python3
"""Build-time only. Download or copy the pinned model, verify, then atomically install it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'model.lock.json').read_text())
TARGET = ROOT / 'app/src/ai/assets/models/lama_fp32.onnx'

def verify(path):
    if not path.exists() or path.stat().st_size != LOCK['size']:
        return False
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() == LOCK['sha256']

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, help='Use an existing local model instead of downloading')
    args = parser.parse_args()
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    if not verify(TARGET):
        pending = TARGET.with_suffix('.pending')
        try:
            if args.source:
                shutil.copyfile(args.source, pending)
            else:
                with urllib.request.urlopen(LOCK['url'], timeout=120) as source, pending.open('wb') as dest:
                    shutil.copyfileobj(source, dest)
            if not verify(pending):
                raise SystemExit('Model SHA-256/size mismatch; refusing to package')
            os.replace(pending, TARGET)
        finally:
            pending.unlink(missing_ok=True)
    metadata = {'sha256': LOCK['sha256'], 'size': LOCK['size'], 'name': LOCK['name']}
    (TARGET.parent / 'model.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(f'Verified bundled model: {LOCK["sha256"]}')

if __name__ == '__main__':
    main()
