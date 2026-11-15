@echo off
javac -cp ".;..\lib\*" *.java
java -cp ".;..\lib\*" BatchBenchmark
pause
