# Renders PhoneMon icon assets from source media using Python/Pillow
# Usage: pwsh tools/make-icon.ps1 -> agent/PhoneBox.Agent/phonebox.ico, logo.png, Android mipmaps
$pyScript = Join-Path $PSScriptRoot 'generate_assets.py'
python $pyScript
