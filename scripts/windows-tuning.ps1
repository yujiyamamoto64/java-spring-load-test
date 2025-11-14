<#
.SYNOPSIS
  Ajusta limites TCP/portas no Windows para suportar mais conexoes simultaneas durante testes de carga.

.DESCRIPTION
  Execute este script em um PowerShell com privilegios de administrador. Ele aumenta o range de portas efemeras,
  reduz TcpTimedWaitDelay e habilita recursos de rede uteis para o k6 martelar o localhost.
#>

if (-not ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Warning "Execute este script em um PowerShell (Admin). Abortando sem alterar o sistema."
    return
}

$tcpParams = "HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters"

Write-Host "Configurando portas efemeras..."
& netsh int ipv4 set dynamicportrange tcp start=10000 num=55535 | Out-Null
& netsh int ipv4 set dynamicportrange udp start=10000 num=55535 | Out-Null

Write-Host "Definindo MaxUserPort e TcpTimedWaitDelay..."
New-ItemProperty -Path $tcpParams -Name "MaxUserPort" -Value 65534 -PropertyType DWord -Force | Out-Null
New-ItemProperty -Path $tcpParams -Name "TcpTimedWaitDelay" -Value 30 -PropertyType DWord -Force | Out-Null

Write-Host "Ativando Receive-Side Scaling e autotuning..."
& netsh int tcp set global rss=enabled | Out-Null
& netsh int tcp set global autotuninglevel=normal | Out-Null

Write-Host "Aplicando aumento no backlog do loopback..."
& netsh int ipv4 set subinterface "Loopback Pseudo-Interface 1" mtu=1500 store=persistent | Out-Null

Write-Host "Tuning concluido. Reinicie o Windows para aplicar todas as configuracoes."
