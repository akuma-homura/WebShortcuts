import base64
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).parents[1] / 'publish-release.py')
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublicationGuardsTest(unittest.TestCase):
    def setUp(self):
        self.repo = 'owner/WebShortcuts'
        self.sha = 'a' * 40
        self.run = {
            'repository': {'full_name': self.repo},
            'head_repository': {'full_name': self.repo},
            'status': 'completed',
            'conclusion': 'success',
            'head_sha': self.sha,
            'path': '.github/workflows/android.yml',
            'event': 'workflow_dispatch',
        }

    def test_successful_exact_commit_build_is_accepted(self):
        publisher.validate_run(self.run, self.repo, self.sha)

    def test_another_commit_cannot_be_released_under_this_tag(self):
        with self.assertRaisesRegex(ValueError, 'does not match'):
            publisher.validate_run(self.run, self.repo, 'b' * 40)

    def test_failed_or_incomplete_build_cannot_publish(self):
        for state, conclusion in [('in_progress', None), ('completed', 'failure'), ('completed', 'cancelled')]:
            run = dict(self.run, status=state, conclusion=conclusion)
            with self.assertRaisesRegex(ValueError, 'finish successfully'):
                publisher.validate_run(run, self.repo, self.sha)

    def test_artifacts_from_a_fork_cannot_publish(self):
        run = dict(self.run, head_repository={'full_name': 'fork/WebShortcuts'})
        with self.assertRaisesRegex(ValueError, 'this repository'):
            publisher.validate_run(run, self.repo, self.sha)

    def test_pull_request_merge_build_is_not_a_tag_build(self):
        run = dict(self.run, event='pull_request')
        with self.assertRaisesRegex(ValueError, 'merge build'):
            publisher.validate_run(run, self.repo, self.sha)

    def test_unrelated_workflow_cannot_publish(self):
        run = dict(self.run, path='.github/workflows/other.yml')
        with self.assertRaisesRegex(ValueError, 'Android CI'):
            publisher.validate_run(run, self.repo, self.sha)

    def test_conflicting_manual_modes_fail_instead_of_reporting_false_success(self):
        with patch.dict('os.environ', {'INSPECT_RUN_ID': '42'}):
            with self.assertRaisesRegex(ValueError, 'not both'):
                publisher.inputs()

    def test_junit_suite_and_skip_counts_are_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'TEST-jvm.xml').write_text('<testsuite tests="4" failures="0" errors="0" skipped="1"/>')
            (root / 'TEST-device.xml').write_text('<testsuites><testsuite tests="3" failures="0" errors="0"/></testsuites>')
            self.assertEqual({'tests': 7, 'failures': 0, 'errors': 0, 'skipped': 1}, publisher.test_counts(root))

    def test_empty_or_failing_junit_reports_block_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                publisher.test_counts(directory)
            (Path(directory) / 'TEST-failed.xml').write_text('<testsuite tests="3" failures="1" errors="0"/>')
            with self.assertRaises(ValueError):
                publisher.test_counts(directory)

    def test_keystore_base64_normalizes_crlf_pem_headers_urlsafe_and_unpadded_inputs(self):
        raw_bytes = b'\x30\x82\x01\x22' + bytes(range(64))
        standard = base64.b64encode(raw_bytes).decode()
        crlf_wrapped = '\ufeff"-----BEGIN CERTIFICATE-----\r\n' + standard[:24] + '\r\n  ' + standard[24:] + '\r\n-----END CERTIFICATE-----"\r\n'
        urlsafe_unpadded = base64.urlsafe_b64encode(raw_bytes).decode().rstrip('=')
        self.assertEqual(raw_bytes, publisher.decode_keystore(crlf_wrapped))
        self.assertEqual(raw_bytes, publisher.decode_keystore(urlsafe_unpadded))
        with self.assertRaisesRegex(ValueError, 'Invalid Base64'):
            publisher.decode_keystore('not!valid@base64$$$')

    def test_signing_passes_passwords_via_environment_not_argv(self):
        raw_bytes = b'\x30\x82\x01\x22' + bytes(range(64))
        encoded = base64.b64encode(raw_bytes).decode() + '\r\n'
        calls = []

        def fake_command(*args, env=None):
            calls.append((args, env))
            if 'verify' in args:
                return 'Signer #1 certificate SHA-256 digest: ' + ('c' * 64)
            Path(args[args.index('--out') + 1] if '--out' in args else args[-1]).write_bytes(b'apk')
            return ''

        with tempfile.TemporaryDirectory() as tmp:
            unsigned = Path(tmp) / 'unsigned.apk'
            signed = Path(tmp) / 'signed.apk'
            unsigned.write_bytes(b'unsigned-apk-bytes')
            env_vars = {
                'RELEASE_KEYSTORE_BASE64': encoded,
                'RELEASE_KEYSTORE_PASSWORD': 'super-secret-store-pass\r\n',
                'RELEASE_KEY_ALIAS': ' release-key \r\n',
                'RELEASE_KEY_PASSWORD': 'super-secret-key-pass\r\n',
            }
            with patch.dict('os.environ', env_vars, clear=False), patch.object(publisher, 'command', side_effect=fake_command):
                digest = publisher.sign_apk_file(unsigned, signed, Path('/sdk/build-tools/36.0.0'))

        self.assertEqual('c' * 64, digest)
        sign_call_args, sign_call_env = calls[1]
        joined_args = ' '.join(sign_call_args)
        self.assertNotIn('super-secret', joined_args)
        self.assertIn('env:_WS_KS_PASS', sign_call_args)
        self.assertEqual('release-key', sign_call_args[sign_call_args.index('--ks-key-alias') + 1])
        self.assertEqual('super-secret-store-pass', sign_call_env['_WS_KS_PASS'])
        self.assertEqual('super-secret-key-pass', sign_call_env['_WS_KEY_PASS'])

    def test_unsigned_fork_build_skips_signing_without_invoking_tools(self):
        with patch.dict('os.environ', {'RELEASE_KEYSTORE_BASE64': ''}), patch.object(publisher, 'command') as tool:
            self.assertIsNone(publisher.sign_apk_file(Path('missing.apk'), Path('unused.apk')))
        tool.assert_not_called()

    def test_incomplete_signing_configuration_fails_before_invoking_tools(self):
        env = {'RELEASE_KEYSTORE_BASE64': base64.b64encode(bytes(range(64))).decode(),
               'RELEASE_KEYSTORE_PASSWORD': '', 'RELEASE_KEY_ALIAS': 'release'}
        with patch.dict('os.environ', env), patch.object(publisher, 'command') as tool:
            with self.assertRaisesRegex(ValueError, 'must be configured'):
                publisher.sign_apk_file(Path('missing.apk'), Path('unused.apk'))
        tool.assert_not_called()

    def test_signed_stable_tag_is_published_as_official_release(self):
        self.assertFalse(publisher.is_prerelease_release('v1.5.0', True))
        self.assertTrue(publisher.is_prerelease_release('v1.5.0-rc1', True))
        self.assertTrue(publisher.is_prerelease_release('v1.5.0', False))


if __name__ == '__main__':
    unittest.main()
