param(
    [Parameter(Mandatory = $true)][string]$TargetIPv4,
    [int[]]$Ports = @(21, 22, 80, 443),
    [Parameter(Mandatory = $true)][switch]$Authorized,
    [string]$OutputDirectory = ".\hogarscan-reference"
)

# Run only against the single domestic IPv4 explicitly authorized by its owner.
if (-not $Authorized) { throw "Confirma la autorización con -Authorized." }
$parsed = [System.Net.IPAddress]::Parse($TargetIPv4)
if ($parsed.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { throw "Se requiere IPv4." }
$octets = $parsed.GetAddressBytes()
$privateAddress = $octets[0] -eq 10 -or
    ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
    ($octets[0] -eq 192 -and $octets[1] -eq 168)
if (-not $privateAddress) { throw "La comparación se limita a una IPv4 privada de la red autorizada." }
$allowed = @(21, 22, 23, 80, 443, 445, 3389)
if ($Ports.Count -lt 1 -or $Ports.Count -gt 6 -or ($Ports | Where-Object { $_ -notin $allowed })) {
    throw "Selecciona de uno a seis puertos del piloto."
}
if (-not (Get-Command nmap -ErrorAction SilentlyContinue)) { throw "Nmap debe estar instalado y disponible en PATH." }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reference = Join-Path $OutputDirectory "nmap-$stamp.xml"
$portList = ($Ports | Sort-Object -Unique) -join ','
# TCP connect plus limited service identification; no scripts, OS detection or exploitation.
& nmap -sT -sV --version-light -Pn --reason -p $portList -oX $reference $TargetIPv4
if ($LASTEXITCODE -ne 0) { throw "Nmap no completó la comparación." }
Write-Host "Referencia guardada en $reference"
Write-Host "Compara con una revisión NUEVA de HogarScan: misma IP, puertos, Wi-Fi y servicios activos."
Write-Host "Registra ACCEPTED/open, REFUSED/closed e inconclusos/filtered; los tiempos pueden diferir."
