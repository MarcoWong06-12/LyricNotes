@echo off
chcp 65001 >nul
title LinerNotes 一键推送到 GitHub 自动编译

echo ========================================================
echo   LinerNotes (唱片内页) - GitHub Actions 云端自动出包
echo ========================================================
echo.
echo 请在浏览器打开 https://github.com/new 建立一个新仓库 (名字填 LinerNotes)
echo 创建后，把仓库地址复制并粘贴在下方：
echo (示例：https://github.com/你的用户名/LinerNotes.git)
echo.
set /p REPO_URL="请输入 GitHub 仓库地址: "

if "%REPO_URL%"=="" (
    echo [错误] 仓库地址不能为空！
    pause
    exit /b
)

cd /d "%~dp0"

"C:\Program Files\Git\bin\git.exe" remote remove origin 2>nul
"C:\Program Files\Git\bin\git.exe" remote add origin %REPO_URL%
"C:\Program Files\Git\bin\git.exe" branch -M main

echo.
echo 正在推送到 GitHub，如果是首次推送，系统会弹出浏览器窗口供你一键授权登录...
"C:\Program Files\Git\bin\git.exe" push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   推送成功！GitHub Actions 云端服务器已开始自动打包！
    echo.
    echo   接下来只需两步获取 APK：
    echo   1. 打开你的 GitHub 仓库页面，点击上方的 [Actions] 标签页
    echo   2. 等待 2-3 分钟（变成绿色对勾后），点击进入这次构建
    echo   3. 在最下方的 Artifacts 区域，点击 [LinerNotes-v1.0-Debug-APK] 即可下载！
    echo ========================================================
) else (
    echo.
    echo [提示] 推送遇到问题，请检查网络连接或仓库权限是否正确。
)

echo.
pause
