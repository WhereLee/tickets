param(
    [int]$Concurrency = 100,
    [int]$UserPool = 3000,
    [int]$ActivityId = 1,
    [int]$Stock = 5000,
    [string]$TargetHost = 'localhost',
    [int]$Port = 8080,
    [switch]$SkipReset
)

$toolsDir = "C:\Users\lrs\Desktop\py\tickets\grab-system\tools"
$jmeter = "C:\Users\lrs\Desktop\tools\apache-jmeter-5.6.3\bin\jmeter.bat"

# 0. reset stock before test (so every round starts clean)
#    skip when target is remote (use reset script on the server instead)
if (-not $SkipReset) {
    $resetSql = "DELETE FROM grab_record; DELETE FROM grab_order; UPDATE activity SET available_stock = $Stock WHERE id = $ActivityId;"
    & mysql -u root -proot grab_system -e $resetSql | Out-Null
    # stage2: redis stock key must be cleared too (pre-deducted stock lives in redis)
    & "F:\Redis\redis-cli.exe" DEL "stock:$ActivityId" | Out-Null
    Write-Host "[1/3] stock reset to $Stock for activity $ActivityId (db + redis)"
} else {
    Write-Host "[1/3] reset skipped (remote target: $TargetHost`:$Port)"
}

# 1. generate jmx from template (plain string replace, avoid regex issues)
$template = Get-Content "$toolsDir\scale_template.jmx" -Raw
$jmxContent = $template.Replace("__THREADS__", $Concurrency.ToString()).Replace("__USERPOOL__", $UserPool.ToString()).Replace("__HOST__", $TargetHost).Replace("__PORT__", $Port.ToString())
$jmxPath = "$toolsDir\scale_$Concurrency.jmx"
[System.IO.File]::WriteAllText($jmxPath, $jmxContent, [System.Text.Encoding]::UTF8)
Write-Host "[2/3] jmx generated for $Concurrency threads"

# 2. run jmeter in non-GUI mode
$jtlPath = "$toolsDir\scale_$Concurrency.jtl"
$logPath = "$toolsDir\scale_$Concurrency.log"
$null = & $jmeter -n -t $jmxPath -l $jtlPath -j $logPath

# 3. parse results
$rows = Import-Csv $jtlPath
$total = $rows.Count
$errors = @($rows | Where-Object { $_.success -ne 'true' })
$ok = @($rows | Where-Object { $_.success -eq 'true' })

# response time (success samples only)
$elapsed = $ok | ForEach-Object { [int]$_.elapsed } | Sort-Object
$avg = if ($elapsed.Count -gt 0) { [math]::Round(($elapsed | Measure-Object -Average).Average) } else { 0 }
$p50 = if ($elapsed.Count -gt 0) { $elapsed[[int]($elapsed.Count * 0.5)] } else { 0 }
$p90 = if ($elapsed.Count -gt 0) { $elapsed[[int]($elapsed.Count * 0.9)] } else { 0 }
$p99 = if ($elapsed.Count -gt 0) { $elapsed[[int]($elapsed.Count * 0.99)] } else { 0 }
$max = if ($elapsed.Count -gt 0) { $elapsed[$elapsed.Count - 1] } else { 0 }

# error type distribution
$errTypes = $errors | Group-Object responseCode | ForEach-Object { "$($_.Name):$($_.Count)" }
$errTypeStr = if ($errTypes) { $errTypes -join "," } else { "none" }

# output one-line CSV for comparison
Write-Host "[3/3] RESULT|$Concurrency|$total|$($errors.Count)|$errTypeStr|$avg|$p50|$p90|$p99|$max"
