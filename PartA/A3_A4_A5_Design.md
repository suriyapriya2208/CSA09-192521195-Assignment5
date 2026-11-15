# PART A – Tasks A3, A4 and A5 (draft answers – review and put them in your own words)

---------------------------------------------------------------------

## TASK A3 – DISCOVER THE DATA ACCESS DESIGN PATTERN

### A3.1 Identify the Common Pattern

| Repeated in StudentDB, CourseDB, MarksDB | Different in each class |
|---|---|
| 1. open a connection | the SQL text (table and columns) |
| 2. build the SQL string | the number, order and types of parameters |
| 3. create Statement / PreparedStatement | the mapping `ResultSet row → Student / Course / Marks` |
| 4. execute | the type returned (Student, Course, Marks) |
| 5. loop over `ResultSet` and create objects | |
| 6. close ResultSet, Statement, Connection (also on error) | |
| 7. handle `SQLException` | |

This is the **Data Access Object (DAO)** pattern: the repeated "skeleton" (steps 1, 3, 4, 6, 7) is written once; only the SQL, the parameters and the row mapper change.

### A3.2 Generic Data-Access Interface

```
INTERFACE Dao<T, K>
    insert(T entity)          : integer          // rows inserted
    findById(K key)           : T or NULL
    findAll()                 : List<T>
    update(T entity)          : integer          // rows updated
    delete(K key)             : integer          // rows deleted
END INTERFACE

CLASS StudentDao IMPLEMENTS Dao<Student, String>
    CONSTRUCTOR(connectionProvider)

    insert(s):    SQL ← "INSERT INTO student(reg_no,name,dept,cgpa) VALUES(?,?,?,?)"
                  PREPARE SQL ; SET 1..4 ← s.regNo, s.name, s.dept, s.cgpa ; RETURN EXECUTE_UPDATE
    findById(k):  SQL ← "SELECT reg_no,name,dept,cgpa FROM student WHERE reg_no=?"
                  PREPARE SQL ; SET 1 ← k ; rs ← EXECUTE_QUERY
                  IF rs has row THEN RETURN mapRow(rs) ELSE RETURN NULL
    findAll():    SQL ← "SELECT ... FROM student" ; list ← []
                  FOR EACH row IN rs DO ADD mapRow(row) TO list ; RETURN list
    update(s):    SQL ← "UPDATE student SET name=?,dept=?,cgpa=? WHERE reg_no=?"
    delete(k):    SQL ← "DELETE FROM student WHERE reg_no=?"
    mapRow(rs):   RETURN new Student(rs.reg_no, rs.name, rs.dept, rs.cgpa)
END CLASS
```
Connections, statements and result sets are opened and closed in **one** helper (try-with-resources), shared by all DAOs. `CourseDao`, `MarksDao` implement the same interface with different SQL/mapper.

### A3.3 Map Objects to Tables

| Student class field | Java type | `student` table column | SQL type | Constraint |
|---|---|---|---|---|
| `regNo` | String | `reg_no` | VARCHAR(15) | **PRIMARY KEY** |
| `name` | String | `name` | VARCHAR(60) | NOT NULL |
| `dept` | String | `dept` | VARCHAR(30) | |
| `cgpa` | double | `cgpa` | DOUBLE / DECIMAL(4,2) | CHECK 0–10 |

One object = one row; one field = one column; the primary key (`reg_no`) is the `K` in `Dao<Student, String>`.

### Alternative Concept and Complexity Analysis – SQL written directly in service / GUI classes

**Solution:** each GUI/service class contains its own connection code, SQL strings and `ResultSet` loops (copy-paste of the pattern).

| | Generic `Dao<T,K>` | SQL in service / GUI classes |
|---|---|---|
| Places to change when column `cgpa` is renamed | **1** (StudentDao SQL + mapRow) | every class with that SQL: e.g. LoginScreen, ReportService, MarksService, ExportScreen … (k places, k grows with the project) |
| Risk of forgetting one place | low | high – runtime error only when that screen is used |
| Time per database operation | O(1) JDBC calls + query cost | same JDBC calls + same query cost |
| Extra cost of the DAO | one extra method call – nanoseconds, negligible | none |
| Space per operation | same objects (statement, result set, entity) | same |
| Testing | DAO can be mocked | needs a real database for every GUI test |

Conclusion: the **run-time cost is the same**; the difference is **maintenance** – 1 change versus k changes.

---------------------------------------------------------------------

## TASK A4 – WHAT SHOULD BE SERIALIZED?

### A4.1 Decide What to Persist

Object graph: `Hostel → List<Room> → List<Resident> → (name, regNo, password, connection)`

| Field | transient? | Reason |
|---|---|---|
| name, regNo | no | the real data to restore |
| **password** | **yes** | secret – must not be written to a file in readable form (serialized files can be read with a hex viewer); store a hash in the database instead |
| **database connection** | **yes** (mandatory) | `Connection` is not `Serializable` → `NotSerializableException`; also a connection is a live OS/network resource and is meaningless after the program ends. Re-open it after loading. |

**What happens to the object graph:** `ObjectOutputStream.writeObject(hostel)` writes the Hostel and then follows **every reference** – all Room objects, all Resident objects – recursively. Each object is written once; if an object is referenced twice, a back-reference is written, so shared objects and cycles are handled. All classes in the graph must implement `Serializable`, otherwise `NotSerializableException`. `static` and `transient` fields are skipped; after reading, a transient field has its default value (`null`, `0`, `false`).

### A4.2 Plan for Change

A `phone` field is added to `Resident` after files were saved.

| Situation | Result when the old file is read |
|---|---|
| `serialVersionUID` is **declared explicitly** and kept the same (e.g. `private static final long serialVersionUID = 1L;`) | Compatible change (adding a field). Old file loads successfully; `phone` = `null` (default value). |
| `serialVersionUID` is **not declared** | Java computes it from the class structure; adding a field changes it → `InvalidClassException: local class incompatible (stream classdesc serialVersionUID = x, local class serialVersionUID = y)`. Old files cannot be read. |
| Field removed / type changed | removal is tolerated (value ignored); type change is incompatible → `InvalidClassException` even with the same UID. |

Plan: always declare `serialVersionUID`, change it only for incompatible changes, and give new fields a sensible default in a `readObject()` method.

### Alternative Concept and Complexity Analysis – CSV / JSON instead of serialization

**Solution:** write `regNo,name,phone` lines (CSV) or `{"regNo":..,"name":..}` (JSON) using a writer or a JSON library; load by parsing the text and rebuilding the objects (Hostel → Rooms → Residents rebuilt from room-number columns / nested arrays).

| | Java serialization | CSV | JSON |
|---|---|---|---|
| File size (n residents) | medium, includes class metadata, ≈ O(n) | smallest (≈ 40–60 B/resident) | larger (field names repeated), ≈ O(n) |
| Time to save n | O(n) – automatic graph walk, uses reflection | O(n) – simple text writing | O(n) – library overhead |
| Time to load n | O(n) – reflection, object creation | O(n) – parse + build objects | O(n) – parse (slowest of the three) |
| Space in memory while loading | O(n) | O(n) (or O(1) streaming) | O(n) |
| Adding a new field | needs `serialVersionUID` discipline; old files default to `null` | add a column; parser must handle lines with fewer columns | extra key is ignored by old readers; missing key → default |
| Readable / editable | no (binary) | yes | yes |
| Cross-language, secure against object-injection | no (Java only, deserialization attacks) | yes | yes |

All are O(n); the differences are in constants, portability and versioning.

---------------------------------------------------------------------

## TASK A5 – DESIGN FOR CORRECTNESS AND SAFETY

### A5.1 Transaction Design – transfer ₹500

```
ALGORITHM TransferMoney(con, fromWallet, toWallet, amount = 500)
  1. SET autoCommit ← false                               // start a transaction
  2. TRY
  3.     balance ← SELECT balance FROM wallet WHERE id = fromWallet   (FOR UPDATE)
  4.     IF balance < amount THEN THROW "insufficient funds"
  5.     UPDATE wallet SET balance = balance - amount WHERE id = fromWallet   // hostel wallet
  6.     UPDATE wallet SET balance = balance + amount WHERE id = toWallet     // canteen wallet
  7.     COMMIT                                           // both changes become permanent together
  8. CATCH any error
  9.     ROLLBACK                                         // both changes undone
 10.     REPORT failure
 11. FINALLY
 12.     RESTORE autoCommit ← true
END
```
If the program fails between steps 5 and 6, **COMMIT is never reached**: the database rolls back the uncommitted update (explicitly in the CATCH, or automatically on connection loss). Money is never lost or created.

### A5.2 Injection Analysis

```
String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + pwd + "'";
```

Crafted input: `id = admin' --` , `pwd = anything`

Resulting SQL: `SELECT * FROM users WHERE id='admin' -- ' AND pwd='anything'`
Everything after `--` is a comment, so the password test disappears and the query returns the admin row → login succeeds. Another input: `pwd = x' OR '1'='1` makes the WHERE condition always true.

Cause: user data is **mixed into the SQL text**, so the database cannot tell data from commands.

**Redesign:**
```
sql ← "SELECT 1 FROM users WHERE id = ? AND pwd = ?"
ps  ← PREPARE sql                  // SQL structure is fixed and compiled first
ps.setString(1, id) ; ps.setString(2, pwd)   // values are sent as DATA only
rs  ← ps.executeQuery()
IF rs has a row THEN login OK ELSE login rejected
```
The input `admin' --` is now searched for literally as an id and finds nothing. (Real systems also store a salted password hash, not the password.)

### Alternative Concept and Complexity Analysis – two separate auto-commit statements

**Outcome if the program fails between the two statements:** statement 1 (debit ₹500 from hostel wallet) was already committed on its own; statement 2 never runs. Result: ₹500 **disappears** – the hostel wallet is ₹500 lower and the canteen wallet did not receive it. The database is now inconsistent and nothing can undo it automatically.

| | One transaction | Two auto-commit statements |
|---|---|---|
| Commits (log flushes to disk) | 1 | 2 |
| Time | slightly lower or equal (one commit); locks are held a bit longer until commit | two commits ≈ twice the commit cost |
| Space | undo-log entries for both updates until commit, O(1) here | O(1) |
| Correctness | atomic – all or nothing | not atomic – money can be lost/created |
| Complexity (Big-O) | O(1) statements either way | O(1) |

Conclusion: the time difference is negligible; the correctness risk of auto-commit is not acceptable for money.
