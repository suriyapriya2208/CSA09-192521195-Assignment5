# Task B3 – Statement vs PreparedStatement vs Batch

Setup: run `schema.sql` in MySQL (creates `transport_test.bus_pass`), put the MySQL connector jar in `../lib`, set `DB_USER` / `DB_PASS` environment variables if your MySQL user is not `root` with an empty password.

## B3.1 Correctness (10 rows)
`BatchBenchmark` prints, for each approach, the first 10 rows and the `ResultSetMetaData` (column name, SQL type). Expected types: `pass_id VARCHAR`, `reg_no VARCHAR`, `name VARCHAR`, `route_no INT`, `amount DOUBLE`; count must be 10 for all three.

## B3.2 / B3.3 Timings (results/b3_timings.csv – fill with YOUR numbers)
| Rows | Statement | PreparedStatement | Batch (auto-commit) | Batch (1 transaction) |
|---|---|---|---|---|
| 1,000 | | | | |
| 10,000 | | | | |
| 100,000 | | | | |

Typical pattern: Statement ≈ PreparedStatement (each row = one network round trip + one commit); batch is many times faster (1,000 rows per round trip; with `rewriteBatchedStatements=true` the driver even sends multi-row INSERTs); one transaction adds a further gain because the log is flushed once instead of once per batch.

## B3.3 Failure half-way (printed by the program)
| Mode | Rows in table after failure at row 5,500 of 10,000 |
|---|---|
| Auto-commit batches of 1,000 | 5,000 (first five batches already committed – **partial data**) |
| One transaction (PassLoader.loadAll) | **0** (rollback) |

## B3.4 Trade-off
* (a) Nightly import of 100,000 passes → **PreparedStatement batch inside one transaction** (fast, all-or-nothing, can be re-run after fixing the bad record).
* (b) A single pass at the counter → **plain PreparedStatement** with auto-commit: one row, no benefit from batching; safe against injection; the single statement is atomic by itself.
* Statement is never preferred: no speed benefit and injection risk.

## Alternative – multi-row INSERT or `LOAD DATA`
```
INSERT INTO bus_pass VALUES (?,?,?,?,?),(?,?,?,?,?), ... (b rows per statement)
LOAD DATA LOCAL INFILE 'passes.csv' INTO TABLE bus_pass FIELDS TERMINATED BY ',' ;
```
| | PreparedStatement batch | Multi-row INSERT | LOAD DATA |
|---|---|---|---|
| Total time for n rows | O(n), n/b round trips | O(n), n/b round trips, less parsing per row | O(n), **fastest constant** (bulk loader, one statement) |
| Memory for a batch of b rows | O(b) parameter sets kept in the driver | O(b) – one big SQL string; limited by `max_allowed_packet` | O(1) in the program (server streams the file; needs `local_infile` enabled) |
| Per-row error report | possible (BatchUpdateException) | whole statement fails | warnings only; less control |
| Validation in Java | yes | yes | not before loading (needs a staging table) |
