$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$desktop = [Environment]::GetFolderPath('Desktop')
$target = Join-Path $PSScriptRoot 'GALAXAM.bat'
$shortcut = Join-Path $desktop 'GRH.lnk'
$logo = Join-Path $repo 'frontend\src\assets\logo\GLX_LOGO.jpg'
$icon = Join-Path $PSScriptRoot 'glx-logo.ico'
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile($logo)
try {
    $bitmap = New-Object System.Drawing.Bitmap 256, 256
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $ratio = [Math]::Min(256 / $source.Width, 256 / $source.Height)
            $width = [int][Math]::Round($source.Width * $ratio)
            $height = [int][Math]::Round($source.Height * $ratio)
            $graphics.DrawImage($source, [int]((256 - $width) / 2), [int]((256 - $height) / 2), $width, $height)
        } finally { $graphics.Dispose() }
        $png = New-Object System.IO.MemoryStream
        try {
            $bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
            $bytes = $png.ToArray()
            $stream = [System.IO.File]::Create($icon)
            try {
                $writer = New-Object System.IO.BinaryWriter $stream
                $writer.Write([byte[]](0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 32, 0))
                $writer.Write([int]$bytes.Length)
                $writer.Write([int]22)
                $writer.Write($bytes)
                $writer.Flush()
            } finally { $stream.Dispose() }
        } finally { $png.Dispose() }
    } finally { $bitmap.Dispose() }
} finally { $source.Dispose() }
$shell = New-Object -ComObject WScript.Shell
$installed = 'D:\GALAXAM-GRH'
if (Test-Path (Join-Path $installed 'scripts\GALAXAM.bat')) {
    $repo = $installed
    $target = Join-Path $installed 'scripts\GALAXAM.bat'
    $icon = Join-Path $installed 'scripts\glx-logo.ico'
}
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = $target
$link.WorkingDirectory = $repo
$link.Description = 'Ouvrir GALAXAM dans le navigateur'
$link.IconLocation = "$icon,0"
$link.Save()
Write-Host "Raccourci GRH mis a jour : $shortcut"
$stopShortcut = Join-Path $desktop ('Arr' + [char]0x00EA + 'ter GRH.lnk')
$stopLink = $shell.CreateShortcut($stopShortcut)
$shortcutScripts = if ($repo -eq $installed) { Join-Path $installed 'scripts' } else { $PSScriptRoot }
$stopLink.TargetPath = Join-Path $shortcutScripts 'Arreter-GRH.bat'
$stopLink.WorkingDirectory = $repo
$stopLink.Description = 'Arrêter le serveur et l interface GRH'
$stopLink.IconLocation = "$env:SystemRoot\System32\shell32.dll,27"
$stopLink.Save()
Write-Host "Raccourci d arret cree : $stopShortcut"
