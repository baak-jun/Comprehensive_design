# ───────────────────────────────────────────────────────────────
#  AI 심리상담 — 개발자 대시보드 로컬 서버
#
#  핸드폰 앱이 기록한 dev_session.json 을 ADB로 읽어
#  http://localhost:8770 에서 대시보드로 보여줍니다.
#
#  실행: PowerShell에서 이 파일 우클릭 → "PowerShell에서 실행"
#        또는  powershell -ExecutionPolicy Bypass -File 서버실행.ps1
# ───────────────────────────────────────────────────────────────

$ErrorActionPreference = "Stop"
$PORT     = 8770
$ADB      = "D:\종합설계\platform-tools\adb.exe"
$HTML     = Join-Path $PSScriptRoot "dashboard.html"
$REMOTE   = "/sdcard/Android/data/com.psychocare/files/dev_session.json"

# ADB 존재 확인
if (-not (Test-Path $ADB)) {
    Write-Host "[오류] ADB를 찾을 수 없습니다: $ADB" -ForegroundColor Red
    Read-Host "엔터를 누르면 종료"; exit 1
}

# 기기 연결 확인
$devices = & $ADB devices
Write-Host "=== 연결된 기기 ===" -ForegroundColor Cyan
Write-Host ($devices -join "`n")

# HTTP 서버 시작
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$PORT/")
$listener.Start()

Write-Host ""
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  대시보드 서버 시작됨" -ForegroundColor Green
Write-Host "  브라우저에서 열기 →  http://localhost:$PORT" -ForegroundColor Yellow
Write-Host "  종료하려면 이 창에서 Ctrl+C" -ForegroundColor Gray
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""

# 브라우저 자동 실행
Start-Process "http://localhost:$PORT"

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response
        $path = $req.Url.AbsolutePath

        try {
            if ($path -eq "/data") {
                # 핸드폰에서 JSON 읽기 (adb exec-out cat)
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
                $html = Get-Content $HTML -Raw -Encoding UTF8
                $res.StatusCode = 200
                $res.ContentType = "text/html; charset=utf-8"
                $buf = [Text.Encoding]::UTF8.GetBytes($html)
            }
            else {
                $res.StatusCode = 404
                $buf = [Text.Encoding]::UTF8.GetBytes("not found")
            }

            $res.ContentLength64 = $buf.Length
            $res.OutputStream.Write($buf, 0, $buf.Length)
        }
        catch {
            Write-Host "[요청 처리 오류] $_" -ForegroundColor Red
        }
        finally {
            $res.OutputStream.Close()
        }
    }
}
finally {
    $listener.Stop()
    Write-Host "서버 종료됨" -ForegroundColor Gray
}
