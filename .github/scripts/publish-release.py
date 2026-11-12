"""Sign and publish immutable APKs from a successful, tag-matching Android CI run."""
import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


def command(*args, env=None):
    return subprocess.check_output(args, text=True, env=env).strip()


def api(path):
    return json.loads(command('gh', 'api', path))


def find_build_tools():
    build_tools_dir = Path(os.environ.get('ANDROID_HOME', '')) / 'build-tools'
    if build_tools_dir.exists():
        candidates = sorted([p for p in build_tools_dir.iterdir() if p.is_dir()], key=lambda p: p.name)
        if candidates:
            return candidates[-1]
    return build_tools_dir / '36.0.0'


def tool_binary(tools_dir: Path, name: str) -> str:
    if os.name == 'nt':
        for suffix in ('.bat', '.exe', '.cmd', ''):
            candidate = tools_dir / f'{name}{suffix}'
            if candidate.exists():
                return str(candidate)
    return str(tools_dir / name)


def decode_keystore(raw: str) -> bytes:
    """Normalize Base64 keystore input across Windows/Linux clipboards, PEM headers, and URL-safe variants."""
    text = (raw or '').lstrip('\ufeff').strip()
    if len(text) >= 2 and text[0] == text[-1] and text[0] in ('"', "'"):
        text = text[1:-1].strip()
    if text.lower().startswith('data:') and ',' in text:
        text = text.split(',', 1)[1]
    lines = [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.strip().startswith('-----')
    ]
    compact = re.sub(r'\s+', '', ''.join(lines)).replace('-', '+').replace('_', '/').rstrip('=')
    if not compact:
        raise ValueError('Decoded keystore input is empty')
    padded = compact + ('=' * ((-len(compact)) % 4))
    try:
        data = base64.b64decode(padded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError('Invalid Base64 in RELEASE_KEYSTORE_BASE64') from exc
    if len(data) < 32:
        raise ValueError(f'Decoded keystore is unexpectedly small ({len(data)} bytes)')
    return data


def verify_apk_cert_sha256(apk_path: Path, tools: Path) -> str:
    certs = command(tool_binary(tools, 'apksigner'), 'verify', '--print-certs', str(apk_path))
    match = re.search(r'certificate SHA-256 digest:\s*([0-9a-f]{64})', certs)
    if not match:
        raise ValueError(f'Could not verify certificate SHA-256 for {apk_path.name}')
    return match.group(1)


def sign_apk_file(unsigned_apk: Path, output_apk: Path, tools: Path | None = None) -> str | None:
    """Sign an unsigned APK if RELEASE_KEYSTORE_BASE64 is configured; never pass secrets on CLI argv."""
    keystore_b64 = (os.environ.get('RELEASE_KEYSTORE_BASE64') or '').strip()
    if not keystore_b64:
        print('No signing keystore configured; skipping signing.')
        return None

    keystore_pass = (os.environ.get('RELEASE_KEYSTORE_PASSWORD') or '').strip('\r\n')
    key_alias = (os.environ.get('RELEASE_KEY_ALIAS') or '').strip()
    key_pass = (os.environ.get('RELEASE_KEY_PASSWORD') or '').strip('\r\n') or keystore_pass
    if not keystore_pass or not key_alias:
        raise ValueError('RELEASE_KEYSTORE_PASSWORD and RELEASE_KEY_ALIAS must be configured with RELEASE_KEYSTORE_BASE64')

    if not unsigned_apk.is_file():
        raise FileNotFoundError(f'Unsigned APK not found: {unsigned_apk}')

    tools = tools or find_build_tools()
    keystore_bytes = decode_keystore(keystore_b64)
    output_apk.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        ks_file = tmp_dir / 'release-keystore.p12'
        ks_file.write_bytes(keystore_bytes)
        try:
            os.chmod(ks_file, 0o600)
        except OSError:
            pass
        aligned_apk = tmp_dir / 'aligned-unsigned.apk'
        command(tool_binary(tools, 'zipalign'), '-p', '-f', '4', str(unsigned_apk), str(aligned_apk))

        sign_env = os.environ.copy()
        sign_env['_WS_KS_PASS'] = keystore_pass
        sign_env['_WS_KEY_PASS'] = key_pass
        command(
            tool_binary(tools, 'apksigner'), 'sign',
            '--ks', str(ks_file),
            '--ks-key-alias', key_alias,
            '--ks-pass', 'env:_WS_KS_PASS',
            '--key-pass', 'env:_WS_KEY_PASS',
            '--out', str(output_apk),
            str(aligned_apk),
            env=sign_env,
        )

    digest = verify_apk_cert_sha256(output_apk, tools)
    print(f'Signed and verified {output_apk} (cert SHA-256: {digest})')
    return digest


def sign():
    unsigned = Path('app/build/outputs/apk/release/app-release-unsigned.apk')
    signed = Path('app/build/outputs/apk/release/app-release.apk')
    sign_apk_file(unsigned, signed)


def inputs():
    if os.environ.get('INSPECT_RUN_ID'):
        raise ValueError('Choose publication or report inspection, not both')
    repo = os.environ['GH_REPO']
    tag = os.environ['RELEASE_TAG']
    run_id = os.environ['BUILD_RUN_ID']
    if not re.fullmatch(r'[\w.-]+/[\w.-]+', repo):
        raise ValueError('Invalid repository')
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', tag):
        raise ValueError('Release tag must be a version such as v1.3.0')
    if not re.fullmatch(r'[1-9]\d{0,19}', run_id):
        raise ValueError('A completed Android CI run ID is required')
    return repo, tag, run_id


def is_prerelease_release(tag: str, release_signed: bool) -> bool:
    return ('-' in tag) or (not release_signed)


def validate_run(run, repo, sha):
    if run['repository']['full_name'] != repo or run.get('head_repository', {}).get('full_name') != repo:
        raise ValueError('Build must belong to this repository')
    if run['status'] != 'completed' or run['conclusion'] != 'success':
        raise ValueError('Build and device tests must finish successfully before publication')
    if run['event'] not in ('workflow_dispatch', 'push'):
        raise ValueError('Use a branch/tag build, not a pull request merge build')
    if run['head_sha'] != sha:
        raise ValueError('Build commit does not match the release tag')
    if run['path'] != '.github/workflows/android.yml':
        raise ValueError('Build must come from Android CI')


def validate():
    repo, tag, run_id = inputs()
    ref = api(f'repos/{repo}/git/ref/tags/{tag}')['object']
    for _ in range(5):
        if ref['type'] != 'tag':
            break
        ref = api(f'repos/{repo}/git/tags/{ref["sha"]}')['object']
    if ref['type'] != 'commit' or not re.fullmatch(r'[0-9a-f]{40}', ref['sha']):
        raise ValueError('Tag must resolve to a commit')
    sha = ref['sha']
    run = api(f'repos/{repo}/actions/runs/{run_id}')
    validate_run(run, repo, sha)
    jobs = api(f'repos/{repo}/actions/runs/{run_id}/jobs?per_page=100&filter=latest')['jobs']
    for name in ('Build, test and lint', 'Android 35 device tests'):
        if not any(j['name'] == name and j['status'] == 'completed' and j['conclusion'] == 'success' for j in jobs):
            raise ValueError(f'Missing successful job: {name}')
    artifacts = api(f'repos/{repo}/actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    expected = ('WebShortcuts-debug', 'WebShortcuts-release-unsigned', 'verification-reports', 'device-test-reports')
    selected = []
    for name in expected:
        matches = [a for a in artifacts if a['name'] == name and not a['expired']]
        if len(matches) != 1:
            raise ValueError(f'Missing or ambiguous artifact: {name}')
        selected.append({k: matches[0].get(k) for k in ('id', 'name', 'digest')})
    optional_signed = [a for a in artifacts if a['name'] == 'WebShortcuts-release' and not a['expired']]
    if len(optional_signed) > 1:
        raise ValueError('Ambiguous artifact: WebShortcuts-release')
    if len(optional_signed) == 1:
        selected.append({k: optional_signed[0].get(k) for k in ('id', 'name', 'digest')})
    value = {
        'repository': repo,
        'tag': tag,
        'commit': sha,
        'buildRunId': int(run_id),
        'buildAttempt': run['run_attempt'],
        'buildUrl': run['html_url'],
        'artifacts': selected,
    }
    Path('release-validation.json').write_text(json.dumps(value, indent=2) + '\n')
    print(f'Validated {tag} -> {sha}, Android CI run {run_id}')


def validation():
    value = json.loads(Path('release-validation.json').read_text())
    repo, tag, run_id = inputs()
    if (value['repository'], value['tag'], str(value['buildRunId'])) != (repo, tag, run_id):
        raise ValueError('Publication inputs changed after validation')
    return value


def test_counts(directory):
    files = list(Path(directory).rglob('TEST-*.xml'))
    counts = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
    for path in files:
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == 'testsuite' else list(root.iter('testsuite'))
        for suite in suites:
            for key in counts:
                counts[key] += int(suite.get(key, '0'))
    if counts['tests'] == 0 or counts['failures'] or counts['errors']:
        raise ValueError(f'Missing or failing test reports in {directory}: {counts}')
    return counts


def verify_apk_metadata(apk_path: Path, expected_tag: str, expected_commit: str | None, tools: Path):
    if expected_commit is not None:
        with zipfile.ZipFile(apk_path) as apk:
            embedded = apk.read('META-INF/version-control-info.textproto').decode()
            if expected_commit not in re.findall(r'revision:\s*"([0-9a-f]{40})"', embedded):
                raise ValueError(f'{apk_path.name} embeds a different source commit')
    badging = command(tool_binary(tools, 'aapt'), 'dump', 'badging', str(apk_path))
    version = re.search(r"versionName='([^']+)'", badging).group(1)
    version_code = int(re.search(r"versionCode='(\d+)'", badging).group(1))
    if version != expected_tag.removeprefix('v') or "name='io.github.homura251.webshortcuts'" not in badging:
        raise ValueError(f'APK package/version does not match the requested release: {apk_path.name}')
    return version, version_code


def package():
    value = validation()
    tools = find_build_tools()
    paths = {
        'debug': Path('downloaded-apks/WebShortcuts-debug/app-debug.apk'),
        'release-unsigned': Path('downloaded-apks/WebShortcuts-release-unsigned/app-release-unsigned.apk'),
    }
    value['tests'] = {'jvm': test_counts('downloaded-reports/jvm'), 'android35': test_counts('downloaded-reports/device')}
    value['publicationRunId'] = os.environ['GITHUB_RUN_ID']
    value['publicationWorkflowCommit'] = os.environ['GITHUB_SHA']
    value['publicationUrl'] = f'https://github.com/{value["repository"]}/actions/runs/{value["publicationRunId"]}'
    prefix = f'WebShortcuts-{value["tag"]}-{value["commit"][:7]}-ci-{value["buildRunId"]}-{value["buildAttempt"]}'
    output = Path('release-assets')
    output.mkdir(exist_ok=False)
    value['apks'] = []
    version = ''
    version_code = 0

    for mode, path in paths.items():
        expected_commit = value['commit'] if mode == 'release-unsigned' else None
        version, version_code = verify_apk_metadata(path, value['tag'], expected_commit, tools)
        name = f'{prefix}-{mode}.apk'
        shutil.copyfile(path, output / name)
        value['apks'].append({
            'file': name,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'bytes': path.stat().st_size,
            'versionName': version,
            'versionCode': version_code,
        })

    value['debugCertificateSha256'] = verify_apk_cert_sha256(paths['debug'], tools)
    value['releaseSigned'] = False

    signed_name = f'{prefix}-release.apk'
    signed_target = output / signed_name
    ci_signed_artifact = Path('downloaded-apks/WebShortcuts-release/app-release.apk')
    release_cert_sha256 = None

    if ci_signed_artifact.is_file():
        verify_apk_metadata(ci_signed_artifact, value['tag'], value['commit'], tools)
        release_cert_sha256 = verify_apk_cert_sha256(ci_signed_artifact, tools)
        shutil.copyfile(ci_signed_artifact, signed_target)
    else:
        release_cert_sha256 = sign_apk_file(paths['release-unsigned'], signed_target, tools)

    if release_cert_sha256:
        value['releaseCertificateSha256'] = release_cert_sha256
        value['releaseSigned'] = True
        value['apks'].append({
            'file': signed_name,
            'sha256': hashlib.sha256(signed_target.read_bytes()).hexdigest(),
            'bytes': signed_target.stat().st_size,
            'versionName': version,
            'versionCode': version_code,
        })
    else:
        signed_name = None

    (output / f'{prefix}-BUILD-INFO.json').write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    hashes = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(output.iterdir()))
    (output / f'{prefix}-SHA256SUMS.txt').write_text(hashes)
    debug_name = value['apks'][0]['file']
    jvm, device = value['tests']['jvm'], value['tests']['android35']

    if signed_name:
        download_section = (
            f"- **正式签名发布版（推荐日常安装）**：`{signed_name}`，启用 R8 优化并使用仓库正式发布密钥签名（证书 SHA-256：`{release_cert_sha256[:16]}…`），后续正式版本可直接覆盖升级。\n"
            f"- **CI 测试版**：`{debug_name}`，使用临时 CI 测试签名，仅供调试排查。\n"
            f"- `release-unsigned.apk`：R8 未签名产物。\n"
            f"- `SHA256SUMS.txt` 与 `BUILD-INFO.json`：完整 SHA-256 校验值、签名指纹与构建溯源记录。\n\n"
            "若此前安装过早期未正式签名的 CI 测试包，首次切换到正式签名发布版前请先在应用内导出完整 ZIP 备份，卸载旧测试包后安装正式版并恢复备份。"
        )
    else:
        download_section = (
            f"- **直接安装（CI 测试版）**：`{debug_name}`，由 GitHub Actions 构建并使用 CI 测试签名。\n"
            f"- `release-unsigned.apk`：R8 未签名包，需要自行正式签名。\n"
            f"- `SHA256SUMS.txt` 与 `BUILD-INFO.json`：实际文件校验值、来源提交及验证记录。\n\n"
            "CI 测试签名与此前本地交付包不同。更换签名前，请先在旧版导出 ZIP 备份，再卸载旧版安装；如签名相同可直接覆盖安装。"
        )

    notes = (
        f"WebShortcuts Android 构建产物，来源版本、提交及验证记录如下。\n\n"
        f"版本：{version}（{version_code}） · 提交：`{value['commit']}`\n\n"
        f"### 下载\n\n{download_section}\n\n"
        f"### 验证\n\n"
        f"[Android CI]({value['buildUrl']}) 已成功：JVM/Robolectric/Compose {jvm['tests']} 项测试，"
        f"Android 35 模拟器 {device['tests']} 项测试（跳过 {device['skipped']} 项），失败和错误均为 0。"
        f"Debug、R8 Release、Lint 及权限检查通过。模拟器结果不代表 OEM 真机验收。\n"
    )
    Path('RELEASE-NOTES.md').write_text(notes)
    print(f'Prepared {len(list(output.iterdir()))} immutable assets for {value["tag"]}')


def publish():
    value = validation()
    repo, tag, run_id = inputs()
    # Recheck the tag and source run immediately before any remote release write.
    validate()
    if validation() != value:
        raise ValueError('Release tag, build attempt or artifacts changed during publication')
    build_info_files = list(Path('release-assets').glob('*-BUILD-INFO.json'))
    release_signed = bool(build_info_files and json.loads(build_info_files[0].read_text()).get('releaseSigned'))
    prerelease = is_prerelease_release(tag, release_signed)

    found = subprocess.run(
        ['gh', 'release', 'view', tag, '--repo', repo, '--json', 'isDraft,assets,url'],
        capture_output=True, text=True,
    )
    if found.returncode:
        if 'not found' not in found.stderr.lower() and '404' not in found.stderr:
            raise RuntimeError(found.stderr)
        create_args = [
            'gh', 'release', 'create', tag, '--repo', repo, '--verify-tag', '--draft',
            '--title', f'WebShortcuts {tag}', '--notes-file', 'RELEASE-NOTES.md',
        ]
        if prerelease:
            create_args.append('--prerelease')
        command(*create_args)
        found = subprocess.run(
            ['gh', 'release', 'view', tag, '--repo', repo, '--json', 'isDraft,assets,url'],
            capture_output=True, text=True, check=True,
        )
    release = json.loads(found.stdout)
    existing = {a['name']: a for a in release['assets']}
    files = sorted(Path('release-assets').iterdir())
    existing_downloads = Path('existing-downloads')
    existing_downloads.mkdir(exist_ok=False)
    for path in files:
        if path.name in existing:
            command('gh', 'release', 'download', tag, '--repo', repo, '--pattern', path.name, '--dir', str(existing_downloads))
            if hashlib.sha256((existing_downloads / path.name).read_bytes()).digest() != hashlib.sha256(path.read_bytes()).digest():
                raise ValueError(f'Refusing to replace existing asset: {path.name}')
        else:
            command('gh', 'release', 'upload', tag, str(path), '--repo', repo)
    verify = Path('verified-downloads')
    verify.mkdir(exist_ok=False)
    for path in files:
        command('gh', 'release', 'download', tag, '--repo', repo, '--pattern', path.name, '--dir', str(verify))
        if hashlib.sha256((verify / path.name).read_bytes()).digest() != hashlib.sha256(path.read_bytes()).digest():
            raise ValueError(f'Release download checksum mismatch: {path.name}')
    if release['isDraft']:
        edit_args = [
            'gh', 'release', 'edit', tag, '--repo', repo,
            '--draft=false',
            f'--prerelease={"true" if prerelease else "false"}',
            '--notes-file', 'RELEASE-NOTES.md',
        ]
        if not prerelease:
            edit_args.append('--latest')
        command(*edit_args)
    url = command('gh', 'release', 'view', tag, '--repo', repo, '--json', 'url', '--jq', '.url')
    print(f'Published and verified {url}')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f'Published and downloaded back all {len(files)} assets: {url}\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('phase', choices=['sign', 'validate', 'package', 'publish'])
    {'sign': sign, 'validate': validate, 'package': package, 'publish': publish}[parser.parse_args().phase]()
