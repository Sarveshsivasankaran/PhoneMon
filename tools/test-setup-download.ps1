$ErrorActionPreference = 'Stop'

$setupPath = Join-Path $PSScriptRoot '../setup.ps1'
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($setupPath, [ref]$tokens, [ref]$errors)
if ($errors -and $errors.Count -gt 0) {
    throw "setup.ps1 syntax errors: $($errors | ForEach-Object { $_.Message })"
}

# Load only the helper functions (excluding the top-level main invocation and param block) into an isolated scope.
$fns = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -in @(
    'Write-Info', 'Write-Caution', 'Write-Ok', 'Get-ExpectedChecksum', 'Get-Download', 'Assert-Checksum', 'Test-Provenance'
) }, $false)
foreach ($fn in $fns) { . ([scriptblock]::Create($fn.Extent.Text)) }

$fixture = Join-Path ([IO.Path]::GetTempPath()) ('phonemon-dl-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture | Out-Null
try {
    $goodBytes = [Text.Encoding]::UTF8.GetBytes('valid-phonemon-archive-payload')
    $goodFile = Join-Path $fixture 'good.bin'
    [IO.File]::WriteAllBytes($goodFile, $goodBytes)
    $goodHash = (Get-FileHash -LiteralPath $goodFile -Algorithm SHA256).Hash.ToLowerInvariant()

    $targetName = 'PhoneBoxAgent-1.2.3-win-x64.zip'
    $sums = Join-Path $fixture 'SHA256SUMS-1.2.3.txt'
    Set-Content -LiteralPath $sums -Value "$goodHash  $targetName" -Encoding ascii

    # Case 1: first download is corrupted/truncated, second attempt via fallback route succeeds cleanly.
    $script:ReleaseAssetApiUrls = @{}
    $script:attempts = 0
    function global:Invoke-CurlDownload([string]$Url, [string]$Path, [string]$Accept = '') {
        $script:attempts++
        [IO.File]::WriteAllText($Path, 'truncated-bad-bytes')
        return $true
    }
    function global:Invoke-WebDownload([string]$Url, [string]$Path, [string]$Accept = '') {
        $script:attempts++
        [IO.File]::WriteAllBytes($Path, $goodBytes)
    }
    function global:Get-Command {
        param([string]$Name, $ErrorAction)
        if ($Name -eq 'curl.exe') { return [pscustomobject]@{ Name = 'curl.exe'; Source = 'curl.exe' } }
        Microsoft.PowerShell.Core\Get-Command @PSBoundParameters
    }

    $outZip = Join-Path $fixture $targetName
    Get-Download "https://example.invalid/$targetName" $outZip -SumsFile $sums
    if ($script:attempts -ne 2) { throw "Expected retry after corrupted download (attempts=$($script:attempts))" }
    Assert-Checksum $outZip $sums

    # Case 2: persistent mismatch fails and never leaves a corrupt partial file behind.
    function global:Invoke-WebDownload([string]$Url, [string]$Path, [string]$Accept = '') {
        [IO.File]::WriteAllText($Path, 'still-corrupt')
    }
    $threw = $false
    try {
        Get-Download "https://example.invalid/$targetName" $outZip -SumsFile $sums
    } catch {
        $threw = ($_.Exception.Message -match 'Checksum mismatch')
    }
    if (-not $threw) { throw 'Expected checksum mismatch exception when every route fails' }
    if (Test-Path -LiteralPath $outZip) { throw 'Corrupt download file was left behind after failure' }

    # Case 3: unreachable attestation server skips gracefully, while signature mismatch still throws.
    $Repo = 'Sarveshsivasankaran/PhoneMon'
    function global:Get-Command {
        param([string]$Name, $ErrorAction)
        if ($Name -eq 'gh') { return [pscustomobject]@{ Name = 'gh'; Source = 'gh.exe' } }
        Microsoft.PowerShell.Core\Get-Command @PSBoundParameters
    }
    $script:provMode = 'network'
    function global:Invoke-Native([string]$File, [string[]]$Arguments) {
        if ($Arguments[0] -eq 'auth') { return [pscustomobject]@{ Code = 0; Output = @('ok') } }
        if ($Arguments[0] -eq '--version') { return [pscustomobject]@{ Code = 0; Output = @('gh version 2.97.0') } }
        if ($script:provMode -eq 'network') {
            return [pscustomobject]@{ Code = 1; Output = @('Error: failed to fetch bundle: dial tcp: lookup tmaproduction.blob.core.windows.net: no such host') }
        }
        return [pscustomobject]@{ Code = 1; Output = @('Error: verification failed: digest mismatch') }
    }
    Test-Provenance $goodFile
    $script:provMode = 'mismatch'
    $provThrew = $false
    try { Test-Provenance $goodFile } catch { $provThrew = ($_.Exception.Message -match 'build provenance') }
    if (-not $provThrew) { throw 'Expected Test-Provenance to throw on real verification failure' }

    Write-Output 'PASS: setup.ps1 retries corrupted/incomplete downloads, handles unreachable attestation hosts cleanly, and removes failed files'
} finally {
    foreach ($fn in 'Invoke-WebDownload', 'Invoke-CurlDownload', 'Get-Command', 'Invoke-Native') {
        Microsoft.PowerShell.Management\Remove-Item "Function:\global:$fn" -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

