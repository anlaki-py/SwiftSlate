"""Fail before building if the configured keystore does not match SwiftSlate's release identity."""
import argparse
import hashlib
import os
from pathlib import Path

from release import CONFIG, run
from release_assets import certificate


def verify_certificate(certificate):
    if not certificate:
        raise ValueError('The keystore exported an empty certificate')
    actual = hashlib.sha256(certificate).hexdigest()
    expected = CONFIG['certificate_sha256']
    print(f'Configured keystore certificate SHA-256: {actual}')
    print(f'Expected published certificate SHA-256: {expected}')
    if actual != expected:
        raise ValueError('Signing identity mismatch. Restore the existing SwiftSlate release keystore; do not rotate the certificate.')
    return actual


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('certificate', type=Path)
    parser.add_argument('--prior-apk', type=Path)
    args = parser.parse_args()
    if args.prior_apk:
        tools = Path(os.environ['ANDROID_HOME']) / 'build-tools'
        versions = sorted((p for p in tools.iterdir() if p.name.replace('.', '').isdigit()),
                          key=lambda p: tuple(map(int, p.name.split('.'))))
        report = run(str(versions[-1] / 'apksigner'), 'verify', '--verbose', '--print-certs', str(args.prior_apk))
        Path('prior-signature-verification.txt').write_text(report + '\n')
        certificate(report)
        for line in report.splitlines():
            if 'certificate SHA-256 digest:' in line:
                print(f'Previously published APK: {line}')
    verify_certificate(args.certificate.read_bytes())
