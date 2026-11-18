"""Publish validated assets. Never update an existing tag or release."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

from release import assert_available, published_codes, releases, run


def main():
    metadata = json.loads(Path('dist/release-metadata.json').read_text())
    tag = metadata['tag']
    if metadata['source_commit'] != os.environ['GITHUB_SHA']:
        raise ValueError('Artifact commit does not match workflow commit')
    history = releases()
    # Refresh remote refs immediately before publication; never force-update tags.
    run('git', 'fetch', 'origin', '--tags')
    assert_available(tag, run('git', 'tag', '--list').splitlines(), history)
    if metadata['version_code'] <= max(published_codes(history)):
        raise ValueError('A newer versionCode was published; run the workflow again')
    for line in Path('dist/checksums-sha256.txt').read_text().splitlines():
        digest, filename = line.split('  ', 1)
        path = Path('dist') / filename
        if path.parent != Path('dist') or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f'Invalid checksum asset: {filename}')
    Path('release-notes.md').write_text(metadata['release_notes'])
    run('git', 'tag', tag, metadata['source_commit'])
    run('git', 'push', 'origin', f'refs/tags/{tag}')
    args = ['gh', 'release', 'create', tag, '--verify-tag', '--title', f'SwiftSlate {tag}',
            '--notes-file', 'release-notes.md']
    if metadata['prerelease']:
        args += ['--prerelease', '--latest=false']
    else:
        args += ['--latest']
    args += [str(path) for path in sorted(Path('dist').iterdir())]
    # gh uploads files to a draft before publishing it. On API failure, retain
    # any draft/tag for diagnosis rather than overwrite or remove history.
    subprocess.run(args, check=True, timeout=300)


if __name__ == '__main__':
    main()
