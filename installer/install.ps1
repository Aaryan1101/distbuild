# distbuild installer for Windows
# Usage: irm https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.ps1 | iex

param(
    [string]$Version = "1.0.0",
    [string]$InstallDir = "C:\distbuild",
    [switch]$Force
)

# Error handling
$ErrorActionPreference = "Stop"

# Colors for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    } else {
        $input | Write-Output
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Info($Message) {
    Write-ColorOutput Cyan "[INFO] $Message"
}

function Write-Success($Message) {
    Write-ColorOutput Green "[SUCCESS] $Message"
}

function Write-Warning($Message) {
    Write-ColorOutput Yellow "[WARNING] $Message"
}

function Write-Error($Message) {
    Write-ColorOutput Red "[ERROR] $Message"
}

# Check if running as Administrator
function Test-Administrator {
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentUser)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# Check system requirements
function Test-Requirements {
    Write-Info "Checking system requirements..."
    
    # Check PowerShell version
    if ($PSVersionTable.PSVersion.Major -lt 5) {
        Write-Error "PowerShell 5.0 or higher is required"
        exit 1
    }
    
    # Check if running on Windows
    if ($PSVersionTable.PSVersionTable.PSEdition -ne "Desktop") {
        Write-Warning "This installer is designed for Windows PowerShell"
    }
    
    Write-Success "System requirements check passed"
}

# Check Java installation
function Test-Java {
    Write-Info "Checking Java installation..."
    
    try {
        $javaVersion = & java -version 2>&1 | Select-Object -First 1
        if ($javaVersion -match '"(\d+)\.(\d+).*" -and [int]$matches[1] -ge 17) {
            Write-Success "Java $($matches[1]).$($matches[2]) found (>= 17 required)"
            return $true
        } else {
            Write-Warning "Java found but version 17+ is recommended"
        }
    } catch {
        Write-Warning "Java not found"
    }
    
    Write-Warning "Java 17+ not found. Installing OpenJDK 17..."
    
    # Try to install via winget (Windows 10/11)
    try {
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            Write-Info "Installing OpenJDK 17 via winget..."
            winget install EclipseAdoptium.Temurin.17.JDK --silent
            Write-Success "OpenJDK 17 installed successfully"
            return $true
        }
    } catch {
        Write-Warning "winget not available"
    }
    
    # Try to install via Chocolatey
    try {
        if (Get-Command choco -ErrorAction SilentlyContinue) {
            Write-Info "Installing OpenJDK 17 via Chocolatey..."
            choco install openjdk --yes
            Write-Success "OpenJDK 17 installed successfully"
            return $true
        }
    } catch {
        Write-Warning "Chocolatey not available"
    }
    
    Write-Error "Please install Java 17+ manually from https://adoptium.net/ and try again"
    return $false
}

# Check Redis (optional)
function Test-Redis {
    Write-Info "Checking Redis installation..."
    
    if (Get-Command redis-server -ErrorAction SilentlyContinue) {
        Write-Success "Redis found"
        return $true
    }
    
    Write-Warning "Redis not found. Redis is recommended for optimal performance."
    $response = Read-Host "Would you like to install Redis? (y/N)"
    
    if ($response -match '^[Yy]') {
        try {
            if (Get-Command choco -ErrorAction SilentlyContinue) {
                Write-Info "Installing Redis via Chocolatey..."
                choco install redis-64 --yes
                Write-Success "Redis installed successfully"
                return $true
            }
        } catch {
            Write-Warning "Chocolatey not available"
        }
        
        Write-Warning "Please install Redis manually from https://github.com/microsoftarchive/redis/releases"
    }
    
    return $false
}

# Download distbuild JAR
function Get-Distbuild {
    Write-Info "Downloading distbuild $Version..."
    
    $jarUrl = "https://github.com/Aaryan1101/distbuild/releases/download/v$Version/distbuild-$Version.jar"
    $jarPath = "$InstallDir\distbuild.jar"
    
    # Create install directory
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    
    try {
        # Download JAR
        Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath -UseBasicParsing
        Write-Success "distbuild $Version downloaded successfully"
    }
    catch {
        Write-Error "Failed to download distbuild JAR: $_"
        exit 1
    }
}

# Create launcher script
function New-Launcher {
    Write-Info "Creating distbuild launcher..."
    
    $launcherPath = "$InstallDir\distbuild.bat"
    
    $launcherContent = @"
@echo off
setlocal
set DISTBUILD_JVM_OPTS=-Xmx512m
if defined DISTBUILD_JVM_OPTS_EXTRA (
    set DISTBUILD_JVM_OPTS=%DISTBUILD_JVM_OPTS% %DISTBUILD_JVM_OPTS_EXTRA%
)
java %DISTBUILD_JVM_OPTS% -jar "$InstallDir\distbuild.jar" %*
endlocal
"@
    
    try {
        Set-Content -Path $launcherPath -Value $launcherContent -Encoding ASCII
        Write-Success "Launcher script created at $launcherPath"
    }
    catch {
        Write-Error "Failed to create launcher script: $_"
        exit 1
    }
}

# Add to PATH
function Add-ToPath {
    Write-Info "Adding distbuild to PATH..."
    
    try {
        $currentPath = [Environment]::GetEnvironmentVariable("PATH", "User")
        if ($currentPath -notlike "*$InstallDir*") {
            $newPath = $currentPath + ";$InstallDir"
            [Environment]::SetEnvironmentVariable("PATH", $newPath, "User")
            Write-Success "Added $InstallDir to user PATH"
            Write-Warning "Restart your terminal or PowerShell session to use distbuild"
        } else {
            Write-Success "$InstallDir already in PATH"
        }
    }
    catch {
        Write-Error "Failed to update PATH: $_"
        exit 1
    }
}

# Verify installation
function Test-Installation {
    Write-Info "Verifying installation..."
    
    try {
        # Test distbuild command
        $result = & "$InstallDir\distbuild.bat" version
        if ($LASTEXITCODE -eq 0) {
            Write-Success "distbuild is working correctly"
            return $true
        } else {
            Write-Error "distbuild is not working correctly"
            return $false
        }
    }
    catch {
        Write-Error "Failed to test distbuild: $_"
        return $false
    }
}

# Show next steps
function Show-NextSteps {
    Write-Success "distbuild installation completed!"
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. Restart your PowerShell session"
    Write-Host "2. Run: distbuild doctor"
    Write-Host "3. Start the coordinator: distbuild coordinator start"
    Write-Host "4. Start a worker: distbuild worker start"
    Write-Host "5. Check status: distbuild status"
    Write-Host ""
    Write-Host "For help: distbuild --help"
    Write-Host "For documentation: https://github.com/YOUR_USERNAME/distbuild"
}

# Check if installation directory exists
if (Test-Path $InstallDir -and -not $Force) {
    Write-Warning "Installation directory already exists: $InstallDir"
    $response = Read-Host "Continue anyway? This will overwrite existing files (y/N)"
    if ($response -notmatch '^[Yy]') {
        Write-Info "Installation cancelled"
        exit 0
    }
}

# Main installation flow
function Main {
    Write-Host "distbuild Installer v$Version"
    Write-Host "================================"
    Write-Host ""
    
    if (-not (Test-Administrator)) {
        Write-Warning "Not running as Administrator. Some features may not work correctly."
        $continue = Read-Host "Continue anyway? (y/N)"
        if ($continue -notmatch '^[Yy]') {
            Write-Info "Installation cancelled"
            exit 0
        }
    }
    
    Test-Requirements
    if (-not (Test-Java)) {
        exit 1
    }
    Test-Redis
    Get-Distbuild
    New-Launcher
    Add-ToPath
    
    if (Test-Installation) {
        Show-NextSteps
    } else {
        Write-Error "Installation verification failed"
        exit 1
    }
}

# Run installation
Main
