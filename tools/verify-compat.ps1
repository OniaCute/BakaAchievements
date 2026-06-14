Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Push-Location (Resolve-Path "$PSScriptRoot\..")
try {
    .\gradlew.bat build

    $fullWidthPattern = '[\uFF0C\u3002\uFF01\uFF1F\uFF08\uFF09\u3010\u3011\u3001\uFF1B\uFF1A]'
    $fullWidth = Select-String -Path "src\main\resources\messages.yml" `
        -Pattern $fullWidthPattern `
        -AllMatches
    if ($fullWidth) {
        $fullWidth | ForEach-Object {
            Write-Error "Full-width punctuation in messages.yml line $($_.LineNumber): $($_.Line)"
        }
    }

    $legacyVanillaMapping = Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java" |
        Select-String -SimpleMatch "replace('/', '_')" |
        Where-Object { $_.Path -notlike "*PathUtil.java" }
    if ($legacyVanillaMapping) {
        $legacyVanillaMapping | ForEach-Object {
            Write-Error "Vanilla path mapping should use PathUtil.vanillaNodePath at $($_.Path):$($_.LineNumber)"
        }
    }
}
finally {
    Pop-Location
}
