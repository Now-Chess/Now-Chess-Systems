@echo off
REM NNUE Training Pipeline for Windows

setlocal enabledelayedexpansion

echo.
echo === NNUE Training Pipeline ===
echo.

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0

cd /d "%SCRIPT_DIR%"

REM Step 1: Generate positions
echo Step 1: Generating 500,000 random positions...
python generate_positions.py positions.txt
if not exist positions.txt (
    echo ERROR: positions.txt not created
    exit /b 1
)
echo [OK] Positions generated
echo.

REM Step 2: Label positions with Stockfish
echo Step 2: Labeling positions with Stockfish (depth 12^)...
if "%STOCKFISH_PATH%"=="" (
    set STOCKFISH_PATH=stockfish
)
python label_positions.py positions.txt training_data.jsonl "%STOCKFISH_PATH%"
if not exist training_data.jsonl (
    echo ERROR: training_data.jsonl not created
    exit /b 1
)
echo [OK] Positions labeled
echo.

REM Step 3: Train NNUE model
echo Step 3: Training NNUE model (20 epochs^)...
python train_nnue.py training_data.jsonl nnue_weights.pt
if not exist nnue_weights.pt (
    echo ERROR: nnue_weights.pt not created
    exit /b 1
)
echo [OK] Model trained
echo.

REM Step 4: Export weights to Scala
echo Step 4: Exporting weights to Scala...
python export_weights.py nnue_weights.pt ..\src\main\scala\de\nowchess\bot\bots\nnue\NNUEWeights.scala
if not exist ..\src\main\scala\de\nowchess\bot\bots\nnue\NNUEWeights.scala (
    echo ERROR: NNUEWeights.scala not created
    exit /b 1
)
echo [OK] Weights exported
echo.

echo === Pipeline Complete ===
echo.
echo Next steps:
echo 1. Navigate to project root: cd ..\..
echo 2. Compile: .\compile.bat
echo 3. Test: .\test.bat
echo.

endlocal
