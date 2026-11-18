"""Release planning shared by the manually dispatched beta and stable workflows."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess

EPOCH = 1704067200
ANDROID_MAX_CODE = 2100000000
ROOT = Path(__file__).resolve().parents[2]
CONFIG = json.loads((ROOT / '.github/scripts/release-config.json').read_text())


def run(*args):
    return subprocess.check_output(args, text=True, timeout=120).strip()


def next_tag(base, tags, channel):
    if not re.fullmatch(r'\d+\.\d+', base):
        raise ValueError('baseVersion must be major.minor')
    stable = re.compile(rf'v{re.escape(base)}\.(0|[1-9]\d*)')
    patches = [int(m[1]) for tag in tags if (m := stable.fullmatch(tag))]
    version = f'{base}.{max(patches, default=-1) + 1}'
    tag = f'v{version}'
    if channel == 'beta':
        beta = re.compile(rf'{re.escape(tag)}-beta\.([1-9]\d*)')
        builds = [int(m[1]) for t in tags if (m := beta.fullmatch(t))]
        tag += f'-beta.{max(builds, default=0) + 1}'
    return tag


def notes_for(text, section):
    matches = list(re.finditer(r'^## \[([^\]]+)\][^\n]*\n?', text, re.MULTILINE))
    selected = [i for i, match in enumerate(matches) if match[1] == section]
    if len(selected) != 1:
        raise ValueError(f'CHANGELOG.md must contain exactly one ## [{section}] section')
    index = selected[0]
    body = text[matches[index].end():matches[index + 1].start() if index + 1 < len(matches) else len(text)]
    meaningful = re.sub(r'<!--.*?-->', '', body, flags=re.DOTALL)
    if not any(line.strip() and not line.startswith('#') for line in meaningful.splitlines()):
        raise ValueError(f'CHANGELOG.md section [{section}] is empty; add actual release notes')
    return body


def next_code(now, previous):
    code = max(now - EPOCH, previous + 1)
    if not 1 <= code <= ANDROID_MAX_CODE:
        raise ValueError('Android versionCode is outside the supported range')
    return code


def releases():
    pages = json.loads(run('gh', 'api', '--paginate', '--slurp',
                           f'repos/{os.environ["GITHUB_REPOSITORY"]}/releases?per_page=100'))
    return [release for page in pages for release in page]


def published_codes(history):
    codes = [CONFIG['historical_version_code']]
    for release in history:
        if release['draft']:
            continue
        metadata = [a for a in release['assets'] if a['name'] == 'release-metadata.json']
        if metadata:
            # The API asset endpoint also works for private repositories.
            raw = run('gh', 'api', '-H', 'Accept: application/octet-stream', metadata[0]['url'])
            code = json.loads(raw)['version_code']
            if type(code) is not int or not 1 <= code <= ANDROID_MAX_CODE:
                raise ValueError(f'Invalid version_code on {release["tag_name"]}')
            codes.append(code)
        elif release.get('published_at'):
            # Legacy releases used minutes. Seconds at publication are a conservative
            # upper bound, including old releases outside the current baseVersion.
            published = dt.datetime.fromisoformat(release['published_at'].replace('Z', '+00:00'))
            codes.append(int(published.timestamp()) - EPOCH)
    return codes


def assert_available(tag, tags, history):
    if tag in tags or any(r['tag_name'] == tag for r in history):
        raise ValueError(f'Tag or release already exists: {tag}')


def plan(channel):
    base = re.search(r'^val baseVersion = "(\d+\.\d+)"$',
                     (ROOT / 'app/build.gradle.kts').read_text(), re.MULTILINE)
    if not base:
        raise ValueError('Cannot read baseVersion from app/build.gradle.kts')
    tags = run('git', 'tag', '--list').splitlines()
    history = releases()
    tag = next_tag(base[1], tags, channel)
    assert_available(tag, tags, history)
    version = tag[1:]
    notes = notes_for((ROOT / 'CHANGELOG.md').read_text(), 'Unreleased' if channel == 'beta' else version)
    code = next_code(int(dt.datetime.now(dt.timezone.utc).timestamp()), max(published_codes(history)))
    Path('release-notes.md').write_text(notes)
    result = dict(tag=tag, version_name=version, version_code=code, channel=channel,
                  prerelease=channel == 'beta', source_commit=os.environ['GITHUB_SHA'])
    Path('release-plan.json').write_text(json.dumps(result, indent=2) + '\n')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        for key in ('tag', 'version_name', 'version_code'):
            output.write(f'{key}={result[key]}\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('channel', choices=['beta', 'stable'])
    try:
        plan(parser.parse_args().channel)
    except (ValueError, KeyError, subprocess.SubprocessError) as error:
        raise SystemExit(f'Release planning failed: {error}') from error
