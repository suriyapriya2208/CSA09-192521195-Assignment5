# CSA09 – Programming in Java – Assignment 5
## Data Persistence Engineering – Files and Databases

| | |
|---|---|
| Name | **<your name>** |
| Register number | **<your register number>** |
| Java version | run `java -version` and write it here (code needs Java 11 or later; tested with 21) |
| GitHub repository | https://github.com/<your-id>/CSA09-<RegNo>-Assignment5 |

> Rename this folder to `CSA09-<YourRegisterNumber>-Assignment5` before pushing.

## Folder structure
```
PartA/            Part A design answers (record layouts, pseudocode, analysis)  – no Java code
Task01/  (B1)     PassStore (TODO 1), tests, buffering + growth benchmark
Task02/  (B2)     SQL-injection demo, damaged-file demo, schema.sql
Task03/  (B3)     PassLoader (TODO 1-3), Statement / Prepared / Batch benchmark, schema.sql
Task04/  (B4)     Migration (file -> MySQL), DbInfo (DatabaseMetaData), schema.sql
lib/              put the MySQL Connector/J jar here (Tasks 02-04)
TaskXX/ANALYSIS.md  alternative-concept analysis + tables where YOU enter your measurements
TaskXX/screenshots/ put your output screenshots here
```

## One-time setup
1. Install JDK 11+ (`javac -version` must work).
2. Install MySQL, then run the scripts below. Use a **test** database only.
3. Download MySQL Connector/J and copy the `.jar` into `lib/`.
4. If your MySQL login is not `root` with an empty password, set environment variables
   `DB_USER`, `DB_PASS` (optional `DB_URL`). Windows: `set DB_USER=root` and `set DB_PASS=yourpassword`.

## How to compile and run each task
Open a terminal **inside the task folder**, then use `run.bat` (Windows) or `./run.sh` (Linux/Mac) – or the commands below (Windows classpath separator `;`, Linux/Mac `:`).

| Task | Setup | Commands |
|---|---|---|
| Task01 (B1) | none | `javac *.java` then `java PassStore`, `java Task01Tests`, `java Task01Benchmark` |
| Task02 (B2) | `mysql -u root -p < schema.sql` | `javac -cp ".;..\lib\*" *.java` then `java -cp ".;..\lib\*" DamagedFileDemo` and `LoginDemo` |
| Task03 (B3) | `mysql -u root -p < schema.sql` | `java -cp ".;..\lib\*" BatchBenchmark` (the 100,000-row Statement test can take minutes; you may pass sizes, e.g. `BatchBenchmark 1000 10000`) |
| Task04 (B4) | `mysql -u root -p < schema.sql` | `java -cp ".;..\lib\*" Migration` then `DbInfo` |

Measurement results are saved automatically in `Task01/results/*.csv` and `Task03/results/*.csv`.
Run each benchmark 2–3 times and report typical values; close other programs while measuring.

## Result of each task (fill in after running)
* Task01 – …
* Task02 – …
* Task03 – …
* Task04 – …

## Suggested commit order (one commit per task at least)
```
git init
git add PartA README.md .gitignore && git commit -m "Part A: design answers"
git add Task01 && git commit -m "B1: file store, tests, buffering and growth benchmark"
git add Task02 lib && git commit -m "B2: adversarial inputs"
git add Task03 && git commit -m "B3: Statement vs PreparedStatement vs batch"
git add Task04 && git commit -m "B4: migration and metadata"
git branch -M main
git remote add origin https://github.com/<your-id>/CSA09-<RegNo>-Assignment5.git
git push -u origin main
```
Add screenshots to each `TaskXX/screenshots/` folder and commit them (`git add . && git commit -m "Add screenshots"`).
Then add the course faculty as collaborator (if private).

## Notes
* The handout's expected output for Task01 shows `3800`; the given `add()` stores doubles, so the program prints `3800.0`.
* Part A files are drafts of the answers – read them, understand them and rewrite in your own words before submitting.
* The numbers in the ANALYSIS.md tables are intentionally blank – use your own machine's measurements.
