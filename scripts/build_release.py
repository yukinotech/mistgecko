#!/usr/bin/env python3
"""Build, inspect and verify the signed, self-contained ARM64 APK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--signing-config', type=Path, help='Private JSON file outside the repository')
    args = parser.parse_args()
    env = os.environ.copy()
    if args.signing_config:
        env.update(json.loads(args.signing_config.read_text()))
    required = ['MISTGECKO_KEYSTORE','MISTGECKO_STORE_PASSWORD','MISTGECKO_KEY_ALIAS','MISTGECKO_KEY_PASSWORD']
    if any(not env.get(key) for key in required):
        raise SystemExit('Missing signing configuration; see docs/RELEASING.md')
    subprocess.run(['python3', 'scripts/prepare_model.py'], cwd=ROOT, env=env, check=True)
    subprocess.run(['./gradlew', 'assembleAiRelease', 'lintAiRelease', '--console', 'plain'], cwd=ROOT, env=env, check=True)
    apk = ROOT / 'app/build/outputs/apk/ai/release/app-ai-release.apk'
    lock = json.loads((ROOT / 'model.lock.json').read_text())
    with zipfile.ZipFile(apk) as archive:
        with archive.open('assets/models/lama_fp32.onnx') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != lock['sha256']:
                raise SystemExit('APK model checksum mismatch')
        native = {name.split('/')[1] for name in archive.namelist() if name.startswith('lib/')}
        if native != {'arm64-v8a'}:
            raise SystemExit(f'Unexpected APK ABIs: {native}')
        if not any(name.startswith('assets/licenses/') for name in archive.namelist()):
            raise SystemExit('Missing bundled licenses')
    sdk = Path(env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT') or '')
    signers = list(sdk.glob('build-tools/*/apksigner'))
    if not signers:
        raise SystemExit('Set ANDROID_HOME to verify the release signature')
    signer = sorted(signers, key=lambda p: tuple(int(s) for s in p.parent.name.split('.') if s.isdigit()))[-1]
    subprocess.run([str(signer), 'verify', '--verbose', '--print-certs', str(apk)], check=True)
    version = dict(line.strip().split('=',1) for line in (ROOT/'version.properties').read_text().splitlines() if '=' in line)['versionName']
    dest = ROOT / 'dist' / f'MistGecko-{version}-arm64-v8a.apk'
    dest.parent.mkdir(exist_ok=True)
    shutil.copyfile(apk, dest)
    with dest.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    dest.with_suffix('.apk.sha256').write_text(f'{digest}  {dest.name}\n')
    shutil.copyfile(ROOT/'docs/THIRD_PARTY.md', dest.parent/'THIRD_PARTY.md')
    print(f'Complete offline APK: {dest} ({dest.stat().st_size / 1_000_000:.1f} MB)')

if __name__ == '__main__':
    main()
