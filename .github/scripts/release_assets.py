"""Validate the single signed SwiftSlate APK and prepare its public release assets."""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

from release import CONFIG, run


def certificate(output):
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', output, re.MULTILINE)
    if len(fingerprints) != 1 or fingerprints[0].lower() != CONFIG['certificate_sha256']:
        raise ValueError(f'Expected certificate {CONFIG["certificate_sha256"]}; apksigner reported {fingerprints or "no recognized signer digest"}. See apk-signature-verification.txt.')
    return fingerprints[0].lower()


def validate_badging(badging, plan):
    expected = {
        'name': CONFIG['package_name'],
        'versionName': plan['version_name'],
        'versionCode': str(plan['version_code']),
    }
    package = next((line for line in badging.splitlines() if line.startswith('package: ')), '')
    for key, value in expected.items():
        match = re.search(rf"\b{key}='([^']*)'", package)
        if not match or match[1] != value:
            raise ValueError(f'APK {key} does not match release plan')
    for key, expected_sdk in [('sdkVersion', 'min_sdk'), ('targetSdkVersion', 'target_sdk')]:
        if f"{key}:'{CONFIG[expected_sdk]}'" not in badging.splitlines():
            raise ValueError(f'Unexpected APK {key}')


def main():
    plan = json.loads(Path('release-plan.json').read_text())
    apks = list(Path('app/build/outputs/apk/release').glob('*.apk'))
    if len(apks) != 1 or apks[0].stat().st_size < 100000:
        raise ValueError('Expected exactly one nonempty universal release APK of at least 100000 bytes')
    apk = apks[0]
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools'
    versions = sorted((p for p in tools.iterdir() if re.fullmatch(r'\d+\.\d+\.\d+', p.name)),
                      key=lambda p: tuple(map(int, p.name.split('.'))))
    if not versions:
        raise ValueError('No stable Android SDK build tools installed')
    verification = run(str(versions[-1] / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk))
    Path('apk-signature-verification.txt').write_text(verification + '\n')
    fingerprint = certificate(verification)
    validate_badging(run(str(versions[-1] / 'aapt'), 'dump', 'badging', str(apk)), plan)
    dist = Path('dist')
    dist.mkdir(exist_ok=True)
    target = dist / f'SwiftSlate-{plan["tag"]}-universal.apk'
    shutil.copyfile(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    repository = os.environ['GITHUB_REPOSITORY']
    url = f'https://github.com/{repository}/releases'
    metadata = dict(plan, package_name=CONFIG['package_name'], min_sdk=CONFIG['min_sdk'],
                    target_sdk=CONFIG['target_sdk'],
                    release_date=dt.datetime.now(dt.timezone.utc).isoformat(),
                    release_url=f'{url}/tag/{plan["tag"]}',
                    changelog_url=f'https://github.com/{repository}/blob/{plan["source_commit"]}/CHANGELOG.md',
                    release_notes=Path('release-notes.md').read_text(), signing_certificate_sha256=fingerprint,
                    assets=[dict(file=target.name, url=f'{url}/download/{plan["tag"]}/{target.name}',
                                 sha256=digest, size=target.stat().st_size)])
    (dist / 'release-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    checksums = []
    for path in sorted(dist.iterdir()):
        checksums.append(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n')
    (dist / 'checksums-sha256.txt').write_text(''.join(checksums))
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
        summary.write(f'## {plan["tag"]}\n\nPackage: `{CONFIG["package_name"]}`\n\n'
                      f'Version code: {plan["version_code"]}\n\nCertificate SHA-256: `{fingerprint}`\n\n'
                      f'APK: {target.name}, {target.stat().st_size} bytes\n\nSHA-256: `{digest}`\n')


if __name__ == '__main__':
    main()
