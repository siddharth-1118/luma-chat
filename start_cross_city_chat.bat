@echo off
title LumaChat Cross-City Relay + Ngrok Launcher
echo ===================================================
echo   Starting LumaChat Cross-City Chat Relay + Ngrok
echo ===================================================
echo.
echo 1. Starting Node Relay Server...
start "LumaChat Server" cmd /k "node server/server.js"
timeout /t 2 /nobreak >nul

echo 2. Starting Ngrok Cloud Tunnel on Port 8080...
start "Ngrok Tunnel" cmd /k "ngrok http 8080"
timeout /t 3 /nobreak >nul

echo.
echo ===================================================
echo   Opening LumaChat Web Console...
echo ===================================================
start http://localhost:8080

echo.
echo Done! Both Server and Ngrok are live.
pause
