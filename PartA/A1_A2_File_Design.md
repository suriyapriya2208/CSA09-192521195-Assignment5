# PART A – Tasks A1 and A2 (draft answers – review and put them in your own words)

---------------------------------------------------------------------

## TASK A1 – DESIGN A FILE FORMAT (Bus-pass records)

### A1.1 Represent the Record

**Fields (order):** passId, regNo, name, routeNo, boardingStop, validityDate, amount

**(a) Delimited text file – one record per line**

```
passId,regNo,name,routeNo,boardingStop,validityDate,amount
P1001,192311001,Arun,7,Tambaram,2027-03-31,4500.00
```

| Item | Decision |
|---|---|
| Delimiter | comma `,` ; record terminator = newline `\n` |
| Date format | ISO `yyyy-MM-dd` (sorts and parses unambiguously) |
| Amount | decimal with 2 places, no thousands separator |
| Name containing a comma (e.g. `Kumar, Arun`) | escape rule: `,` is written `\,` and `\` is written `\\`; newline characters are removed on input. The reader splits only on an un-escaped comma and then un-escapes. (Alternative: wrap the field in quotes `"Kumar, Arun"` and double inner quotes.) |
| Field count | exactly 7 – any other count means the line is damaged |

**(b) Binary file written with `DataOutputStream`**

| # | Field | Method | Bytes |
|---|---|---|---|
| 1 | passId | `writeUTF` | 2 (length) + n |
| 2 | regNo | `writeUTF` | 2 + n |
| 3 | name | `writeUTF` | 2 + n |
| 4 | routeNo | `writeInt` | 4 |
| 5 | boardingStop | `writeUTF` | 2 + n |
| 6 | validityDate | `writeLong` (epoch-day) | 8 |
| 7 | amount | `writeDouble` | 8 |

* Strings are **length-prefixed**, so no delimiter is needed and a name containing a comma needs **no special handling**.
* The file must be read back in exactly the same field order with `DataInputStream`.
* Optionally a 4-byte *magic number + version* header is written once at the start of the file.

### A1.2 Design the Operations (text-file design)

**Stream choice and reasons**

| Operation | Classes | Why |
|---|---|---|
| add | `FileWriter` wrapped in `BufferedWriter`, opened in **append** mode | Text data → character stream; buffering reduces OS calls; append never rewrites old data |
| find / list | `FileReader` wrapped in `BufferedReader` (`readLine()`) | Text data; buffered, line-oriented reading |
| (not used) `FileInputStream/FileOutputStream` | – | raw bytes: only needed for binary layout (b) |

**Pseudocode**

```
ALGORITHM AddPass(file, pass)
  INPUT : file name, pass record
  OUTPUT: record appended
  1. IF any field contains newline THEN remove the newline characters
  2. line ← join(escape(field) for each field, ",")
  3. OPEN file for APPEND using BufferedWriter          // creates file if missing
  4. WRITE line, WRITE newline
  5. FLUSH and CLOSE the writer                         // try-with-resources
END

ALGORITHM FindPass(file, passId)
  INPUT : file name, passId
  OUTPUT: record or NOT_FOUND
  1. IF file does not exist THEN RETURN NOT_FOUND
  2. OPEN file using BufferedReader
  3. WHILE (line ← READLINE) is not END_OF_FILE DO
  4.     fields ← splitOnUnescapedComma(line)
  5.     IF count(fields) ≠ 7 THEN LOG "damaged line", CONTINUE
  6.     IF fields[0] = passId THEN CLOSE reader, RETURN fields
  7. END WHILE
  8. CLOSE reader
  9. RETURN NOT_FOUND
END

ALGORITHM ListByRoute(file, route)
  1. result ← empty list
  2. IF file does not exist THEN RETURN result
  3. OPEN file using BufferedReader
  4. WHILE (line ← READLINE) is not END_OF_FILE DO
  5.     fields ← split(line)
  6.     IF count(fields) ≠ 7 OR fields[3] is not an integer THEN LOG, CONTINUE
  7.     IF integer(fields[3]) = route THEN ADD fields TO result
  8. END WHILE
  9. CLOSE reader, RETURN result
END
```

### A1.3 Design for Damaged Data

| Problem | Detection | Handling |
|---|---|---|
| **Damaged line** | wrong field count, non-numeric route/amount, bad date, empty line | skip the line, write its line number to a log/error list, continue with the next line (one bad line never stops the whole read) |
| **Missing file** | `Files.exists()` / `NoSuchFileException` | `find`/`list` treat it as "no passes yet"; `add` creates a new file |
| **Incomplete write (power failure)** | last line has no newline or too few fields; optional checksum field at line end | the damaged last line is rejected on read. For safety: write to `passes.tmp`, call `flush()`/`FileDescriptor.sync()`, then atomically rename (`Files.move(..., ATOMIC_MOVE)`) over the real file; keep a daily backup copy |

### Alternative Concept and Complexity Analysis – `RandomAccessFile`, fixed-length records

**Solution:** every record has the same size, so record *i* starts at byte `i × recordSize`.

```
passId 10 chars (20 B) | regNo 12 chars (24 B) | name 30 chars (60 B) | route int (4 B)
stop 30 chars (60 B)   | validity long (8 B)    | amount double (8 B)  →  recordSize = 184 B
```

```
FindByIndex(i):  seek(i × 184); read 184 bytes             // O(1)
Add:             seek(length); write 184 bytes             // O(1)
Find by passId:  if passId = running number (P1001 → index 1000) → direct seek O(1)
                 otherwise still a scan, O(n), unless an index (e.g. HashMap passId→index) is kept
```

| | Delimited text | Fixed-length `RandomAccessFile` |
|---|---|---|
| Find by position/ID (if ID → index) | O(n) scan | **O(1)** seek |
| Find by non-key field (route) | O(n) | O(n) |
| Add | O(1) append | O(1) append |
| Space for 8,000 records | ≈ 8,000 × ~55 B ≈ 0.4 MB (only real length used) | 8,000 × 184 B ≈ **1.47 MB** |
| Wasted padding | none | name avg 15 of 30 chars, stop avg 12 of 30, passId 6 of 10 … ≈ 100 of 184 B per record are padding (≈ 55 %) |
| Variable-length names | easy | truncated if longer than 30 chars |
| Human readable / editable | yes | no |

Space complexity of both is O(n); the fixed-length file has a bigger constant factor (padding).

---------------------------------------------------------------------

## TASK A2 – DON'T READ WHAT YOU DON'T NEED

### A2.1 Predict the Cost

A 50 MB file = 50 × 1,048,576 = **52,428,800 bytes**. `FileInputStream.read()` returns one byte per call, so the program makes about **52.4 million `read()` calls** (+1 for the end-of-file value). In Java each call goes through JNI and normally causes a system call into the operating system (context switch user → kernel → user, cost typically 0.1–1 µs or more). The actual work per byte (compare with `'\n'`) is almost nothing, so the program is dominated by call overhead: expected time ≈ tens of seconds. It is slow because of **call overhead, not because of the amount of data**.

*(Compare with your measurement in B1.3: record the real time and number of calls.)*

### A2.2 Redesign Using Buffering

```
ALGORITHM CountLines(file)
  1. lines ← 0 ; buffer ← new byte array of 8192
  2. OPEN file using FileInputStream
  3. WHILE (n ← READ(buffer)) ≠ END_OF_FILE DO
  4.     FOR i ← 0 TO n-1 DO  IF buffer[i] = '\n' THEN lines ← lines + 1
  5. CLOSE file ; RETURN lines
END
```

(or wrap the stream in `BufferedInputStream` / `BufferedReader`.)

* Number of OS reads: 52,428,800 / 8,192 = **6,400** instead of 52,428,800 → **8,192× fewer system calls**.
* The loop in step 4 still looks at every byte, but it works on memory (cheap) instead of making an OS call per byte.
* A larger buffer (64 KB) reduces calls further, with diminishing returns.

### A2.3 When Buffering Does Not Help

* When the program **already reads large blocks** (e.g. `read(byte[1 MB])` or `Files.copy`) – an extra buffer only adds a copy.
* When reading a few bytes from random positions (`RandomAccessFile.seek`) – the buffer would be filled with data that is never used.
* Interactive or network streams delivering a few bytes at a time – the buffer cannot fill until data arrives.
* Tiny files (a few bytes): buffer allocation costs more than it saves.

### Alternative Concept and Complexity Analysis – `Files.readAllLines()`

**Solution:** `long lines = Files.readAllLines(path).size();`

| | Buffered stream / byte[] | `Files.readAllLines` |
|---|---|---|
| Time | O(n) – n = file size | O(n) – plus creating one `String` object per line |
| Memory | **O(buffer size)** = 8 KB, independent of file size | **O(file size)** – 50 MB of bytes becomes ≈ 100–200 MB of Strings and objects on the heap (Java `String` + object headers) |
| Failure risk | none | `OutOfMemoryError` for files bigger than heap |
| Constant time factor | small | larger (decoding + allocation + garbage collection) |

* Use **buffered streaming** for large files, one-pass processing (counting, filtering, searching), logs.
* Use **readAllLines** for small files (config, a few thousand lines) when the program needs all lines at once (sorting, random access to lines) and the code simplicity matters.
