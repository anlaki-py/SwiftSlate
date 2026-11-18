"""Non-publishing regression tests, executed by CI without signing credentials."""
import re
import subprocess
import unittest

from release import assert_available, next_code, next_tag, notes_for, CONFIG, EPOCH, ANDROID_MAX_CODE
from release_assets import certificate, validate_badging


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tags = ['v1.0.48', 'v1.0.32-beta.test.32', 'v1.0.999-foo', 'v1x0.999',
                     'prefix-v1.0.999', 'v1.0.999junk']

    def test_beta_sequence_and_promotion(self):
        self.assertEqual(next_tag('1.0', self.tags, 'beta'), 'v1.0.49-beta.1')
        self.tags.append('v1.0.49-beta.1')
        self.assertEqual(next_tag('1.0', self.tags, 'beta'), 'v1.0.49-beta.2')
        self.assertEqual(next_tag('1.0', self.tags, 'stable'), 'v1.0.49')
        self.tags.append('v1.0.49')
        self.assertEqual(next_tag('1.0', self.tags, 'beta'), 'v1.0.50-beta.1')

    def test_real_tags(self):
        tags = subprocess.check_output(['git', 'tag', '--list'], text=True).splitlines()
        self.assertIn('v1.0.48', tags)
        stable = [int(t.rsplit('.', 1)[1]) for t in tags
                  if re.fullmatch(r'v1\.0\.\d+', t)]
        self.assertEqual(next_tag('1.0', tags, 'stable'), f'v1.0.{max(stable) + 1}')

    def test_empty_and_new_base(self):
        self.assertEqual(next_tag('2.0', self.tags, 'stable'), 'v2.0.0')
        with self.assertRaises(ValueError):
            next_tag('1x0', [], 'stable')

    def test_collisions(self):
        with self.assertRaises(ValueError):
            assert_available('v1.0.48', self.tags, [])
        with self.assertRaises(ValueError):
            assert_available('v1.0.49', [], [{'tag_name': 'v1.0.49'}])

    def test_version_code_monotonic_and_legacy(self):
        self.assertGreater(next_code(EPOCH + 90000000, 1314188), 1314188)
        self.assertEqual(next_code(EPOCH + 100, 100), 101)
        self.assertEqual(next_code(EPOCH + 100, 101), 102)
        with self.assertRaises(ValueError):
            next_code(EPOCH + 100, ANDROID_MAX_CODE)

    def test_changelog_exact_sections_and_preservation(self):
        text = '# Changelog\n\n## [Unreleased]\n\n### Added\n- Beta.\n\n## [1.0.49]\n\n- Stable.\n\n## [1.0.490]\n- Wrong.\n'
        self.assertEqual(notes_for(text, 'Unreleased'), '\n### Added\n- Beta.\n\n')
        self.assertEqual(notes_for(text, '1.0.49'), '\n- Stable.\n\n')
        for section in ['1.0.4', 'Missing']:
            with self.assertRaises(ValueError):
                notes_for(text, section)
        for body in ['', '\n### Added\n', '\n<!-- fill later -->\n']:
            with self.assertRaises(ValueError):
                notes_for('## [Unreleased]\n' + body, 'Unreleased')
        with self.assertRaises(ValueError):
            notes_for('## [Unreleased]\n- A\n## [Unreleased]\n- B', 'Unreleased')

    def test_signature_identity(self):
        self.assertEqual(certificate('Signer #1 certificate SHA-256 digest: ' + CONFIG['certificate_sha256']),
                         CONFIG['certificate_sha256'])
        for output in ['', 'Signer #1 certificate SHA-256 digest: deadbeef']:
            with self.assertRaises(ValueError):
                certificate(output)

    def test_package_and_version(self):
        plan = {'version_name': '1.0.49-beta.1', 'version_code': 90000000}
        badging = "package: name='swiftslate.anlaki.dev' versionCode='90000000' versionName='1.0.49-beta.1'\nsdkVersion:'23'\ntargetSdkVersion:'36'"
        validate_badging(badging, plan)
        with self.assertRaises(ValueError):
            validate_badging(badging.replace('swiftslate.anlaki.dev', 'swiftslate.anlaki.dev.debug'), plan)
        with self.assertRaises(ValueError):
            validate_badging(badging.replace('90000000', '1'), plan)


class AssetGenerationTests(unittest.TestCase):
    def test_single_apk_metadata_checksums_and_summary(self):
        import hashlib
        import json
        import os
        from pathlib import Path
        import tempfile
        from unittest.mock import patch
        import release_assets

        with tempfile.TemporaryDirectory() as folder:
            before = Path.cwd()
            try:
                os.chdir(folder)
                apk_dir = Path('app/build/outputs/apk/release')
                apk_dir.mkdir(parents=True)
                (apk_dir / 'app-release.apk').write_bytes(b'a' * 100001)
                tools = Path('sdk/build-tools/36.0.0')
                tools.mkdir(parents=True)
                plan = dict(tag='v1.0.49-beta.1', version_name='1.0.49-beta.1',
                            version_code=90000000, source_commit='abc', channel='beta', prerelease=True)
                Path('release-plan.json').write_text(json.dumps(plan))
                Path('release-notes.md').write_text('\n- Actual notes.\n')
                signature = 'Signer #1 certificate SHA-256 digest: ' + CONFIG['certificate_sha256']
                badging = "package: name='swiftslate.anlaki.dev' versionCode='90000000' versionName='1.0.49-beta.1'\nsdkVersion:'23'\ntargetSdkVersion:'36'"
                env = dict(ANDROID_HOME=str(Path('sdk').absolute()),
                           GITHUB_REPOSITORY='anlaki-py/SwiftSlate', GITHUB_STEP_SUMMARY='summary.md')
                with patch.dict(os.environ, env), patch.object(release_assets, 'run', side_effect=[signature, badging]):
                    release_assets.main()
                metadata = json.loads(Path('dist/release-metadata.json').read_text())
                self.assertEqual(metadata['package_name'], CONFIG['package_name'])
                self.assertEqual(metadata['release_notes'], '\n- Actual notes.\n')
                self.assertEqual(metadata['channel'], 'beta')
                self.assertTrue(metadata['prerelease'])
                self.assertEqual(len(metadata['assets']), 1)
                self.assertEqual(metadata['assets'][0]['file'], 'SwiftSlate-v1.0.49-beta.1-universal.apk')
                for line in Path('dist/checksums-sha256.txt').read_text().splitlines():
                    digest, filename = line.split('  ')
                    self.assertEqual(hashlib.sha256((Path('dist') / filename).read_bytes()).hexdigest(), digest)
                # Missing APK and unexpected splits must fail before verification.
                (apk_dir / 'extra.apk').write_bytes(b'b' * 100001)
                with self.assertRaises(ValueError):
                    release_assets.main()
                for apk in apk_dir.iterdir():
                    apk.unlink()
                with self.assertRaises(ValueError):
                    release_assets.main()
            finally:
                os.chdir(before)


class PublishedVersionTests(unittest.TestCase):
    def test_legacy_and_metadata_codes(self):
        from unittest.mock import patch
        from release import published_codes
        import json
        history = [
            dict(tag_name='v1.0.48', draft=False, published_at='2026-07-01T15:12:58Z', assets=[]),
            dict(tag_name='v1.0.49', draft=False, assets=[dict(name='release-metadata.json', url='https://api.github.com/asset')]),
            dict(draft=True, assets=[]),
        ]
        with patch('release.run', return_value=json.dumps(dict(version_code=100000000))):
            codes = published_codes(history)
        self.assertEqual(max(codes), 100000000)
        self.assertGreater(codes[1], CONFIG['historical_version_code'])

    def test_invalid_published_code_blocks_release(self):
        from unittest.mock import patch
        from release import published_codes
        history = [dict(tag_name='v1.0.49', draft=False, assets=[dict(name='release-metadata.json', url='https://api.github.com/asset')])]
        with patch('release.run', return_value='{"version_code": "invalid"}'):
            with self.assertRaises(ValueError):
                published_codes(history)


class AndroidSignerFormatTests(unittest.TestCase):
    def test_current_and_legacy_signer_formats_preserve_the_same_identity(self):
        digest = CONFIG['certificate_sha256']
        for prefix in ['Signer #1', 'V2 Signer:', 'V3 Signer:', 'V3.1 Signer:']:
            output = f'Verifies\nNumber of signers: 1\n{prefix} certificate SHA-256 digest: {digest}\n'
            self.assertEqual(digest, certificate(output))
        both = f'V2 Signer: certificate SHA-256 digest: {digest}\nV3 Signer: certificate SHA-256 digest: {digest}'
        self.assertEqual(digest, certificate(both))

    def test_different_or_unrecognized_additional_signers_fail_closed(self):
        digest = CONFIG['certificate_sha256']
        valid = f'V2 Signer: certificate SHA-256 digest: {digest}\n'
        for extra in [f'V3 Signer: certificate SHA-256 digest: {"0" * 64}',
                      f'Unknown Signer: certificate SHA-256 digest: {digest}', 'Number of signers: 2']:
            with self.assertRaises(ValueError):
                certificate(valid + extra)


class KeystoreIdentityTests(unittest.TestCase):
    def test_identity_matches_and_empty_or_different_certificates_fail(self):
        import hashlib
        from unittest.mock import patch
        from verify_signing_identity import verify_certificate
        with patch.dict(CONFIG, certificate_sha256=hashlib.sha256(b'public-certificate').hexdigest()):
            self.assertEqual(CONFIG['certificate_sha256'], verify_certificate(b'public-certificate'))
            with self.assertRaises(ValueError):
                verify_certificate(b'other-public-certificate')
            with self.assertRaises(ValueError):
                verify_certificate(b'')


if __name__ == '__main__':
    unittest.main()
