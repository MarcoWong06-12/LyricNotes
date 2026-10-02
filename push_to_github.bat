@echo off
chcp 65001 >nul
title LyricNotes 一键推送到 GitHub 自动编译

echo ========================================================
echo   LyricNotes - GitHub Actions 云端自动出包
echo ========================================================
echo.
echo 仓库已绑定：https://github.com/MarcoWong06-12/LyricNotes.git
echo.
set REPO_URL=https://github.com/MarcoWong06-12/LyricNotes.git

cd /d "%~dp0"

"C:\Program Files\Git\bin\git.exe" remote remove origin 2>nul
"C:\Program Files\Git\bin\git.exe" remote add origin %REPO_URL%
"C:\Program Files\Git\bin\git.exe" branch -M main

echo.
echo 正在推送到 GitHub...
"C:\Program Files\Git\bin\git.exe" push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   推送成功！GitHub Actions 云端服务器已开始自动打包！
    echo.
    echo   接下来只需两步获取 APK：
    echo   1. 打开你的 GitHub 仓库页面，点击上方的 [Actions] 标签页
    echo   2. 等待 2-3 分钟（变成绿色对勾后），点击进入这次构建
    echo   3. 在最下方的 Artifacts 区域，点击 [LyricNotes-v1.0.0-APK] 即可下载！
    echo ========================================================
) else (
    echo.
    echo [提示] 推送遇到问题，请检查网络连接或仓库权限是否正确。
)

echo.
pause
