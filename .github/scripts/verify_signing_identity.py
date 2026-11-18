"""Fail before building if the configured keystore does not match SwiftSlate's release identity."""
import hashlib
from pathlib import Path
import sys

from release import CONFIG


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
    verify_certificate(Path(sys.argv[1]).read_bytes())
