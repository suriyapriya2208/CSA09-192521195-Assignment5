# Task B1 – Implement and Instrument the File Store

## B1.1 Test table (Task01Tests.java – paste your own output screenshot in /screenshots)
| Test | Input | Expected | What it verifies |
|---|---|---|---|
| T1/T2 | find existing ID (normal case) | fields returned | add + find work, field order correct |
| T3 | find `P999` (not in file) | `null` | not-found is reported, no exception |
| T4/T5 | find an ID whose line is damaged (too few / too many fields) | `null` | damaged line skipped, not returned |
| T6 | find an ID stored *after* damaged lines | found | a damaged line does not stop the scan |
| T7/T8 | list route 7 / route 99 | 2 passes / empty list | list-by-route ignores damaged lines |
| T9/T10 | file deleted | `null` / empty list | missing file handled |

**Why the damaged line P1002 does not stop the search (TODO 1):** `find()` splits each line and `continue`s when the field count is not 5, so the loop simply moves to the next line; only the matching ID returns. Reading goes on until P1003.

**Note on expected output:** the starter `add()` uses `String.valueOf(double)`, so 3800 is stored as `3800.0` and the program prints `[P1003, 192311003, Divya, 7, 3800.0]` (the handout shows `3800`). The given `add()` was not changed.

## B1.2 Compare with the Part A design
| Part A design | Implementation | Reason for change |
|---|---|---|
| 7 fields (stop, validity date) | 5 fields (starter code) | the handout's `PassStore` stores five fields; the layout code is otherwise the same |
| escape rule for commas in names | not implemented – a name with a comma would be seen as 6 fields and skipped as damaged | kept simple; mention as limitation |
| `listByRoute` | added to `PassStore` | needed for operation (iii) |
| missing file → "no passes" | `catch NoSuchFileException` | as designed |

## B1.3 Buffering measurements (fill with YOUR numbers from results/b1_3_buffering.csv)
| Size | Unbuffered (ms) | BufferedInputStream (ms) | byte[8192] (ms) | readAllLines (ms) |
|---|---|---|---|---|
| 1 MB | | | | |
| 10 MB | | | | |
| 50 MB | | | | |

Interpretation guide: unbuffered time grows linearly with size (≈ 52 million `read()` calls for 50 MB, as predicted in A2.1); buffered versions make ≈ 6,400 OS reads for 50 MB, so they are many times faster. Compare the factor you measured with the predicted 8,192× fewer OS calls; the real speed-up is smaller because the per-byte loop work remains and the JVM's `read()` on a `FileInputStream` is a native call but the OS caches file data.

## B1.4 Growth measurements (results/b1_4_growth.csv)
| Records | find (ms) | HashMap load (ms) | HashMap lookup (ms) |
|---|---|---|---|
| 1,000 | | | |
| 10,000 | | | |
| 100,000 | | | |

Trend: `find()` reads lines until it finds the ID, so time grows **linearly, O(n)**; a missing ID or the last record is the worst case. A HashMap lookup is O(1) and independent of n, after a one-time O(n) load.

## Alternative – HashMap loaded once at start-up
```
startup:  FOR each valid line DO map.put(passId, fields)        // O(n) once
find(id): RETURN map.get(id)                                    // O(1) average
```
| | Scan per search | HashMap |
|---|---|---|
| q searches on n passes | **O(q·n)** | **O(n + q)** |
| Memory | O(buffer) ≈ 8 KB | **O(n)** – every record as String[] + String objects (100,000 passes ≈ tens of MB) |
| Staleness | always current | must be updated when a pass is added (map.put + file append) |

Use the HashMap when there are many searches (q large); use the scan when memory is tight or searches are rare.
