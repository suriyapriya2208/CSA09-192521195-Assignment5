import java.nio.file.*;
import java.util.*;

/** B1.1 - test table: normal case, missing ID, damaged line, empty line, missing file. */
public class Task01Tests {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        System.out.printf("%-4s %s%n", ok ? "PASS" : "FAIL", name);
        if (ok) pass++; else fail++;
    }

    public static void main(String[] args) throws Exception {
        Path f = Files.createTempFile("tests", ".csv");
        PassStore s = new PassStore(f);

        s.add("P1", "192311001", "Arun", 7, 4500);
        s.add("P2", "192311002", "Meena", 9, 4000);
        Files.writeString(f, "P3,DAMAGED\n", StandardOpenOption.APPEND);          // too few fields
        Files.writeString(f, "\n", StandardOpenOption.APPEND);                      // empty line
        Files.writeString(f, "P4,1,Ravi,7,100,EXTRA\n", StandardOpenOption.APPEND); // too many fields
        s.add("P5", "192311005", "Divya", 7, 3800);

        check("T1 normal find returns correct pass",
                Arrays.equals(s.find("P5"), new String[]{"P5", "192311005", "Divya", "7", "3800.0"}));
        check("T2 first record is found", s.find("P1") != null && s.find("P1")[2].equals("Arun"));
        check("T3 non-existent ID returns null", s.find("P999") == null);
        check("T4 damaged line (too few fields) is skipped -> null", s.find("P3") == null);
        check("T5 damaged line (too many fields) is skipped -> null", s.find("P4") == null);
        check("T6 records AFTER damaged lines are still found", s.find("P5") != null);
        check("T7 list route 7 returns exactly 2 valid passes", s.listByRoute(7).size() == 2);
        check("T8 list route with no passes returns empty list", s.listByRoute(99).isEmpty());

        Files.delete(f);
        check("T9 missing file: find returns null, no crash", s.find("P1") == null);
        check("T10 missing file: list returns empty", s.listByRoute(7).isEmpty());

        System.out.println("\nPassed " + pass + ", Failed " + fail);
    }
}
