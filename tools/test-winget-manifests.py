"""Exercise release manifest generation without writing a real version."""
import pathlib
import shutil
import subprocess
import sys
import tempfile

GENERATOR = pathlib.Path(__file__).resolve().parents[1] / 'packaging/winget/make-manifests.py'

with tempfile.TemporaryDirectory() as tmp:
    generator = pathlib.Path(shutil.copyfile(GENERATOR, pathlib.Path(tmp, GENERATOR.name)))
    version = '1.2.2'
    tag = 'v1.2.1-stable'
    subprocess.run([sys.executable, str(generator), version, 'a' * 64, tag], check=True)
    directory = pathlib.Path(tmp, 'manifests/s/Sarveshsivasankaran/PhoneMon', version)
    locale = (directory / 'Sarveshsivasankaran.PhoneMon.locale.en-US.yaml').read_text(encoding='utf-8')
    installer = (directory / 'Sarveshsivasankaran.PhoneMon.installer.yaml').read_text(encoding='utf-8')
    notes = f'ReleaseNotesUrl: https://github.com/Sarveshsivasankaran/PhoneMon/releases/tag/{tag}'
    assert f'/releases/download/{tag}/PhoneBoxAgent-{version}-win-x64.zip' in installer
    assert 'Publisher: Solo-P-Leveller' in locale.splitlines()
    assert notes in locale.splitlines(), 'WinGet default locale must carry the release notes URL'
    assert notes not in installer.splitlines(), 'WinGet installer has no release notes field'
    print('PASS: WinGet release notes live in the default locale')
