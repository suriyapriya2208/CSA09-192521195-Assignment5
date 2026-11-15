# Task B2 – Engineer an Adversarial Input

Setup: run `schema.sql` (or let LoginDemo create the table). Test database only.

## Observed behaviour (paste your own output)
| Input (id / pwd) | Unsafe concatenation | PreparedStatement | What it reveals |
|---|---|---|---|
| arun / secret123 | accepted | accepted | normal case works in both |
| arun / wrong | rejected | rejected | control case |
| `arun' -- ` / anything | **accepted** (password test commented out) | rejected | the id field lets input become SQL code |
| arun / `x' OR '1'='1` | **accepted** (condition always true) | rejected | the password field can also change the logic |
| `' OR 1=1 -- ` / x | **accepted** | rejected | no valid user needed at all |
| `arun\` / ` OR 1=1 -- ` | accepted by the "escape quotes" version | rejected | quote-escaping is bypassed with a backslash |

With a PreparedStatement the whole text is bound as a *value*, so the quote characters have no meaning for the SQL parser.

## Damaged bus-pass file (DamagedFileDemo.java)
| Line | Problem | Response |
|---|---|---|
| `P2,192311002,Meena,9` | wrong number of fields | rejected, reason printed, reading continues |
| `P3,...,abc` | non-numeric amount | rejected |
| (empty) | empty line | rejected |
| `P5,...,xx,...` | non-numeric route | rejected |
| `P7,192311007,Sam,3` | line cut short | rejected |
| missing file | – | "no passes yet", no crash |

What it reveals about the original design: the starter `find()` only checks the **field count**, not the data types – a line with 5 fields but an amount of `abc` would be accepted and fail later (`NumberFormatException`) when the amount is parsed. Validation of every field (as done in `validate()`) is needed.

## Alternative – escape quotes or validate with a regular expression
```
id  ← must match ^[A-Za-z0-9_]{3,30}$ , else reject
pwd ← replace ' with ''          then still concatenate into the SQL string
```
Why weaker:
* escaping rules differ per database / settings (MySQL also treats `\` as escape → `arun\` bypass shown above);
* character-set tricks and forgotten fields; one missed call = vulnerability;
* a regex cannot sensibly restrict passwords or free text (names like O'Brien are legal);
* the developer, not the driver, is responsible for correctness on every query.

Time cost: regex check ≈ microseconds (O(length of input)), escaping O(length); both are tiny compared with the database round trip (≈ 100 µs–ms), so they save nothing. A PreparedStatement adds no per-query validation cost, is O(length) to send the parameters, and can be re-used (parse once, execute many times). See the timing lines printed by `LoginDemo`.
