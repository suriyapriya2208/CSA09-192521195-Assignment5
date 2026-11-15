@echo off
javac -cp ".;..\lib\*" *.java
java -cp ".;..\lib\*" DamagedFileDemo
java -cp ".;..\lib\*" LoginDemo
pause
