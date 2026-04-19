# Setup and run NNUE training pipeline

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$VenvDir = Join-Path $ScriptDir ".venv"

# Check if virtual environment exists
if (-not (Test-Path $VenvDir)) {
    Write-Host "Creating virtual environment..."
    python -m venv $VenvDir
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to create virtual environment. Make sure python is installed."
        exit 1
    }
}

# Activate virtual environment
Write-Host "Activating virtual environment..."
$ActivateScript = Join-Path $VenvDir "Scripts\Activate.ps1"
& $ActivateScript

# Install/update dependencies if requirements.txt exists
$RequirementsFile = Join-Path $ScriptDir "requirements.txt"
if (Test-Path $RequirementsFile) {
    Write-Host "Installing dependencies..."
    pip install -q -r $RequirementsFile
}

# Run nnue.py
Write-Host "Starting NNUE Training Pipeline..."
python (Join-Path $ScriptDir "nnue.py")
