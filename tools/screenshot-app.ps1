param([string]$OutPng = "$env:TEMP\opencode\tp-shot.png", [int]$WaitSec = 30)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
    public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    // PW_RENDERFULLCONTENT (2/3) captures DWM-composited (GPU) windows even when behind others
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
}
"@

Add-Type -AssemblyName System.Drawing

$deadline = (Get-Date).AddSeconds($WaitSec)
while ((Get-Date) -lt $deadline) {
    $p = Get-Process | Where-Object { $_.ProcessName -in @("java", "javaw") -and $_.MainWindowTitle -like "Trashpanda*" } | Select-Object -First 1
    if ($p -and $p.MainWindowHandle -ne 0) {
        $hw = $p.MainWindowHandle
        [Win]::ShowWindow($hw, 4) | Out-Null   # SW_SHOWNOACTIVATE
        Start-Sleep -Milliseconds 600
        [Win]::SetForegroundWindow($hw) | Out-Null
        Start-Sleep -Milliseconds 700
        $fg = [Win]::GetForegroundWindow()
        $r = New-Object Win+RECT
        [Win]::GetWindowRect($hw, [ref]$r) | Out-Null
        $w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
        if ($w -lt 50 -or $h -lt 50) { Start-Sleep -Seconds 2; continue }

        $bmp = New-Object System.Drawing.Bitmap($w, $h)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $hdc = $g.GetHdc()
        $ok = [Win]::PrintWindow($hw, $hdc, 3)
        $g.ReleaseHdc($hdc)
        if ($ok) { $bmp.Save($OutPng, [System.Drawing.Imaging.ImageFormat]::Png); Write-Output "PrintWindow ok ${w}x${h} (foreground=$($fg -eq $hw)) -> $OutPng"; exit 0 }
        if ($fg -eq $hw) {
            $bg = [System.Drawing.Graphics]::FromImage($bmp)
            $bg.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
            $bmp.Save($OutPng, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "screen-copy ${w}x${h} -> $OutPng"; exit 0
        }
        Write-Output "foreground refused; PrintWindow failed"
    }
    Start-Sleep -Seconds 2
}
Write-Output "window not found within $WaitSec s"
exit 1
