@echo off
title MySQL Database - smart_library_db
echo =====================================================================
echo          OPENING MYSQL DATABASE: smart_library_db
echo =====================================================================
echo.
echo Database: smart_library_db
echo User    : root
echo.
echo Useful SQL queries to try:
echo   SHOW TABLES;
echo   SELECT * FROM users;
echo   SELECT id, title, author, available_copies, total_copies FROM books;
echo   SELECT * FROM circulation_transactions;
echo   SELECT * FROM library_seats;
echo   EXIT;
echo.
echo ---------------------------------------------------------------------
echo.
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -proot123 --database=smart_library_db
if %errorlevel% neq 0 (
    echo.
    echo If access was denied, check your MySQL root password.
    pause
)
