@echo off
javac -cp ".;..\lib\*" *.java
java -cp ".;..\lib\*" Migration
java -cp ".;..\lib\*" DbInfo
pause
