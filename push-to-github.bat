@echo off
echo Pushing Smart AI Library Management System to GitHub...
git push -u origin main
if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] Code successfully pushed to GitHub!
    echo Repo URL: https://github.com/Shanmugapriyan25-R/smart-ai-library-system
) else (
    echo.
    echo [ERROR] Push failed. Make sure the repository 'smart-ai-library-system' exists on your GitHub account.
)
pause

