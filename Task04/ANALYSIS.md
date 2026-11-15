# Task B4 – Final Engineering Decision

## B4.1 Migration
`Migration.java` reads `passes.csv` (generates a 20,000-row sample with damaged rows if the file does not exist), validates each line (field count, numeric reg no, route, amount, duplicate ID), writes rejected lines with line number and reason to `failed_records.txt`, and loads the valid rows with `PreparedStatement` batches of 1,000 inside one transaction (rollback on any error). It finishes with a read / loaded / rejected report.

## B4.2 DatabaseMetaData (DbInfo.java) – why useful
It reports database product/version, driver name/version, JDBC version, supported features (transactions, batch) and the tables/columns/keys. When the application is moved to another server this shows immediately whether the right DBMS and driver version are connected, whether features the program relies on are available, and whether the schema was created – without reading the server's documentation or using a separate tool; code can even adapt (e.g. use batch only if `supportsBatchUpdates()`).

## B4.3 Production decision (fill numbers from your own measurements)
**Decision: use a relational database (MySQL) accessed through JDBC.**

| Requirement | Text file | Serialization | Database |
|---|---|---|---|
| 100 × 8,000 = 800,000 passes, search | O(n) scan per search – B1.4 showed time growing linearly with n | whole object graph must be loaded into memory | indexed primary key → O(log n) search |
| Several counters at the same time | no safe concurrent writes (corrupt/interleaved lines) | one writer overwrites the other | transactions + locking (ACID) |
| Safety | damaged / half-written lines | `InvalidClassException`, whole-file corruption | rollback, constraints, backups |
| Bulk loading | – | – | batch + transaction (B3: much faster than row-by-row) |
| Queries (list a route, report) | manual code | manual code | SQL + index on `route_no` |

## Alternative – each campus keeps its own serialized file
* **Search all passes:** the program must open and deserialize 12 files (or contact 12 machines): O(N) total where N = all passes, plus 12 file loads; a pass held by another campus is unreachable unless files are copied around.
* **Space:** O(N) for the data, but each file holds class metadata and has to be loaded fully into memory (≈ O(N/12) per campus); duplicates appear when files are copied between campuses.
* **Concurrent counters:** within a campus two counters writing one serialized file overwrite each other (last write wins) unless access is serialized by a file lock or one single server process; across campuses there is no consistent view.
* **Shared database:** one set of tables; the DBMS handles concurrency (row locks, transactions), one search via index, one backup.
