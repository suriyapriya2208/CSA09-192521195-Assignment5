import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * B4.1 - migrate the old bus-pass text file into MySQL using batches + ONE transaction.
 * Records that fail validation are NOT loaded; they are listed in failed_records.txt.
 *
 * Run:  java -cp ".;../lib/*" Migration [passes.csv]
 * If the input file does not exist, a sample file with 20,000 passes (some deliberately
 * damaged) is generated first so the program can be demonstrated.
 */
public class Migration {

    static final String INSERT = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)";
    static final int BATCH = 1000;

    /** Returns null if valid, otherwise the reason for rejection. */
    static String validate(String line, Set<String> seenIds) {
        if (line.isBlank()) return "empty line";
        String[] f = line.split(",", -1);
        if (f.length != 5) return "wrong number of fields (" + f.length + ")";
        if (f[0].isBlank() || f[0].length() > 12) return "bad pass ID";
        if (!f[1].matches("\\d{1,15}")) return "register number is not numeric";
        if (f[2].isBlank()) return "empty name";
        try {
            if (Integer.parseInt(f[3].trim()) <= 0) return "route must be positive";
        } catch (NumberFormatException e) {
            return "route is not a number";
        }
        try {
            if (Double.parseDouble(f[4].trim()) < 0) return "negative amount";
        } catch (NumberFormatException e) {
            return "amount is not numeric";
        }
        if (!seenIds.add(f[0])) return "duplicate pass ID in file";
        return null;
    }

    static void generateSample(Path p, int n) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            for (int i = 1; i <= n; i++) {
                String line;
                if (i % 5000 == 0)      line = "P" + i + ",19231" + i + ",Student" + i + ",5";       // missing field
                else if (i % 7777 == 0) line = "P" + i + ",19231" + i + ",Student" + i + ",5,abc";   // bad amount
                else if (i % 9000 == 0) line = "";                                                    // empty
                else line = "P" + String.format("%07d", i) + ",19231" + String.format("%07d", i)
                        + ",Student" + i + "," + (1 + i % 20) + "," + (3000.0 + i % 2000);
                w.write(line);
                w.newLine();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args.length > 0 ? args[0] : "passes.csv");
        if (!Files.exists(in)) {
            System.out.println("Input not found - generating sample file " + in);
            generateSample(in, 20000);
        }

        // ---- 1. read + validate ----
        List<String[]> valid = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int lineNo = 0;
        try (BufferedReader r = Files.newBufferedReader(in)) {
            String line;
            while ((line = r.readLine()) != null) {
                lineNo++;
                String err = validate(line, seen);
                if (err == null) valid.add(line.split(",", -1));
                else failed.add("line " + lineNo + " | " + err + " | " + line);
            }
        }
        Files.write(Path.of("failed_records.txt"), failed);
        System.out.printf("Read %d lines: %d valid, %d failed (see failed_records.txt)%n", lineNo, valid.size(), failed.size());

        // ---- 2. load valid rows: batches of 1,000 inside ONE transaction ----
        try (Connection c = Db.open()) {
            try (Statement st = c.createStatement()) {
                st.execute("TRUNCATE TABLE bus_pass");   // test database only
            }
            c.setAutoCommit(false);
            long t0 = System.nanoTime();
            int loaded = 0;
            try (PreparedStatement ps = c.prepareStatement(INSERT)) {
                for (String[] p : valid) {
                    ps.setString(1, p[0]);
                    ps.setString(2, p[1]);
                    ps.setString(3, p[2]);
                    ps.setInt(4, Integer.parseInt(p[3].trim()));
                    ps.setDouble(5, Double.parseDouble(p[4].trim()));
                    ps.addBatch();
                    if (++loaded % BATCH == 0) ps.executeBatch();
                }
                ps.executeBatch();
                c.commit();
                System.out.printf("Committed %d rows in %.0f ms%n", loaded, (System.nanoTime() - t0) / 1e6);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                System.out.println("MIGRATION FAILED, all inserts rolled back: " + e);
                throw e;
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
                rs.next();
                System.out.println("Rows now in bus_pass table: " + rs.getInt(1));
            }
            System.out.printf("REPORT: read=%d  loaded=%d  rejected=%d%n", lineNo, valid.size(), failed.size());
        }
    }
}
