@echo off
title LumaChat Desktop - Python Tkinter Client
echo ===================================================
echo 🌸 Starting LumaChat Python Tkinter Desktop Client...
echo ===================================================
python luma_desktop.py
if errorlevel 1 (
    echo.
    echo [Error] Failed to launch Tkinter GUI.
    echo Please ensure Python 3 is installed and added to PATH.
    pause
)
