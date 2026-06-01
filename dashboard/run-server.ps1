# ===============================================================
#  AI Counseling - Developer Dashboard local server
#  Reads dev_session.json from the phone via ADB and serves
#  the dashboard at http://localhost:8770
#
#  Run:  powershell -ExecutionPolicy Bypass -File run-server.ps1
# ===============================================================

$ErrorActionPreference = "Stop"
$PORT   = 8770
$ADB        = "D:\종합설계\platform-tools\adb.exe"
$HTML       = "D:\종합설계\dashboard\dashboard.html"
$REMOTE     = "/sdcard/Android/data/com.psychocare/files/dev_session.json"

if (-not (Test-Path $ADB)) {
    Write-Host "[ERROR] ADB not found: $ADB" -ForegroundColor Red
    Read-Host "Press Enter to exit"; exit 1
}

Write-Host "=== Connected devices ===" -ForegroundColor Cyan
& $ADB devices

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$PORT/")
$listener.Start()

Write-Host ""
Write-Host "===============================================" -ForegroundColor Green
Write-Host "  Dashboard server started" -ForegroundColor Green
Write-Host "  Open in browser ->  http://localhost:$PORT" -ForegroundColor Yellow
Write-Host "  Press Ctrl+C in this window to stop" -ForegroundColor Gray
Write-Host "===============================================" -ForegroundColor Green
Write-Host ""

Start-Process "http://localhost:$PORT"

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response
        $path = $req.Url.AbsolutePath

        try {
            if ($path -eq "/data") {
                $json = & $ADB exec-out cat $REMOTE 2>$null
                if ([string]::IsNullOrWhiteSpace($json)) {
                    $res.StatusCode = 404
                    $buf = [Text.Encoding]::UTF8.GetBytes('{"error":"no session yet"}')
                } else {
                    $res.StatusCode = 200
                    $res.ContentType = "application/json; charset=utf-8"
                    $buf = [Text.Encoding]::UTF8.GetBytes(($json -join "`n"))
                }
                $res.Headers.Add("Cache-Control","no-store")
            }
            elseif ($path -eq "/" -or $path -eq "/index.html") {
                $res.StatusCode = 200
                $res.ContentType = "text/html; charset=utf-8"
                $buf = [System.IO.File]::ReadAllBytes($HTML)
            }
            else {
                $res.StatusCode = 404
                $buf = [Text.Encoding]::UTF8.GetBytes("not found")
            }

            $res.ContentLength64 = $buf.Length
            $res.OutputStream.Write($buf, 0, $buf.Length)
        }
        catch {
            Write-Host "[request error] $_" -ForegroundColor Red
        }
        finally {
            $res.OutputStream.Close()
        }
    }
}
finally {
    $listener.Stop()
    Write-Host "Server stopped" -ForegroundColor Gray
}





