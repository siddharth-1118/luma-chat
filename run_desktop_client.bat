@echo off
title LumaChat Desktop - Python Tkinter Client
echo Starting LumaChat Desktop Client...
python luma_desktop.py
if errorlevel 1 (
    echo.
    echo [Error] Failed to launch Tkinter GUI.
    echo Please ensure Python 3 is installed and available in PATH.
    pause
)
