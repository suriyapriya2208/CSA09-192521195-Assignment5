import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * B3.1 correctness (10 rows, SELECT + ResultSetMetaData)
 * B3.2 timing at 1,000 / 10,000 / 100,000 rows: Statement, PreparedStatement, Batch
 * B3.3 auto-commit vs single transaction, with a forced failure half-way
 *
 * Run:  java -cp ".;../lib/*" BatchBenchmark            (all sizes)
 *       java -cp ".;../lib/*" BatchBenchmark 1000 10000 (chosen sizes only)
 * Note: Statement with 100,000 rows in auto-commit can take several minutes.
 */
public class BatchBenchmark {

    static final String INSERT = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)";

    // ---------- data generation ----------
    static List<String[]> generate(int n, int badAmountAt) {
        List<String[]> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            String amount = (i == badAmountAt) ? "INVALID" : String.valueOf(3000.0 + i % 2000);
            list.add(new String[]{"P" + String.format("%07d", i), "19231" + String.format("%07d", i),
                    "Student" + i, String.valueOf(1 + i % 20), amount});
        }
        return list;
    }

    static void truncate(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("TRUNCATE TABLE bus_pass");
        }
    }

    static int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ---------- the three insert strategies ----------
    /** (a) Plain Statement, SQL text built per row, auto-commit ON. */
    static int viaStatement(Connection c, List<String[]> passes) throws SQLException {
        c.setAutoCommit(true);
        int n = 0;
        try (Statement st = c.createStatement()) {
            for (String[] p : passes) {
                st.executeUpdate("INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES ('"
                        + p[0] + "','" + p[1] + "','" + p[2] + "'," + Integer.parseInt(p[3]) + "," + Double.parseDouble(p[4]) + ")");
                n++;
            }
        }
        return n;
    }

    /** (b) PreparedStatement, one executeUpdate per row, auto-commit ON. */
    static int viaPrepared(Connection c, List<String[]> passes) throws SQLException {
        c.setAutoCommit(true);
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(INSERT)) {
            for (String[] p : passes) {
                bind(ps, p);
                ps.executeUpdate();
                n++;
            }
        }
        return n;
    }

    /** (c1) Batch with auto-commit ON: each executeBatch() is committed immediately. */
    static int viaBatchAutoCommit(Connection c, List<String[]> passes) throws SQLException {
        c.setAutoCommit(true);
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(INSERT)) {
            for (String[] p : passes) {
                bind(ps, p);
                ps.addBatch();
                n++;
                if (n % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        return n;
    }

    static void bind(PreparedStatement ps, String[] p) throws SQLException {
        ps.setString(1, p[0]);
        ps.setString(2, p[1]);
        ps.setString(3, p[2]);
        ps.setInt(4, Integer.parseInt(p[3]));
        ps.setDouble(5, Double.parseDouble(p[4]));
    }

    // ---------- B3.1 ----------
    static void showContents(Connection c, String label) throws SQLException {
        System.out.println("--- " + label + " ---");
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bus_pass ORDER BY pass_id LIMIT 10")) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            for (int i = 1; i <= cols; i++)
                System.out.printf("  col %d: %-10s type=%-8s (java.sql.Types=%d)%n", i, md.getColumnName(i), md.getColumnTypeName(i), md.getColumnType(i));
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) sb.append(rs.getObject(i)).append(" | ");
                System.out.println(sb);
            }
        }
        System.out.println("  row count = " + count(c));
    }

    public static void main(String[] args) throws Exception {
        int[] sizes = {1000, 10000};
        if (args.length > 0) {
            sizes = Arrays.stream(args).mapToInt(Integer::parseInt).toArray();
        }
        Files.createDirectories(Path.of("results"));

        try (Connection c = Db.open()) {
            // ===== B3.1 =====
            System.out.println("===== B3.1 Correctness with 10 records =====");
            List<String[]> ten = generate(10, -1);
            truncate(c); viaStatement(c, ten);       showContents(c, "Statement");
            truncate(c); viaPrepared(c, ten);        showContents(c, "PreparedStatement");
            truncate(c); viaBatchAutoCommit(c, ten); showContents(c, "PreparedStatement + batch");

            // ===== B3.2 =====
            System.out.println("\n===== B3.2 / B3.3 Timing (milliseconds) =====");
            System.out.printf("%-9s %-12s %-14s %-18s %-18s%n", "rows", "Statement", "Prepared", "Batch(autocommit)", "Batch(transaction)");
            StringBuilder csv = new StringBuilder("rows,statement_ms,prepared_ms,batch_autocommit_ms,batch_transaction_ms\n");
            for (int n : sizes) {
                List<String[]> data = generate(n, -1);
                double[] t = new double[4];

                truncate(c); long t0 = System.nanoTime(); viaStatement(c, data);        t[0] = (System.nanoTime() - t0) / 1e6;
                truncate(c); t0 = System.nanoTime();      viaPrepared(c, data);         t[1] = (System.nanoTime() - t0) / 1e6;
                truncate(c); t0 = System.nanoTime();      viaBatchAutoCommit(c, data);  t[2] = (System.nanoTime() - t0) / 1e6;
                truncate(c); t0 = System.nanoTime();      PassLoader.loadAll(c, data);  t[3] = (System.nanoTime() - t0) / 1e6;

                System.out.printf("%-9d %-12.0f %-14.0f %-18.0f %-18.0f%n", n, t[0], t[1], t[2], t[3]);
                csv.append(String.format("%d,%.0f,%.0f,%.0f,%.0f%n", n, t[0], t[1], t[2], t[3]));
            }
            Files.writeString(Path.of("results/b3_timings.csv"), csv.toString());

            // ===== B3.3 failure half-way =====
            System.out.println("\n===== B3.3 Forced failure half-way (invalid amount at row 5,500 of 10,000) =====");
            List<String[]> bad = generate(10000, 5500);

            truncate(c);
            try {
                viaBatchAutoCommit(c, bad);
            } catch (RuntimeException | SQLException e) {
                System.out.println("Auto-commit batch failed: " + e);
            }
            System.out.println("  rows left in table (auto-commit) = " + count(c) + "   <- PARTIAL data stays!");

            truncate(c);
            try {
                PassLoader.loadAll(c, bad);
            } catch (RuntimeException | SQLException e) {
                System.out.println("Transaction batch failed: " + e);
            }
            System.out.println("  rows left in table (transaction) = " + count(c) + "   <- everything rolled back");

            truncate(c);
            System.out.println("\nSaved: results/b3_timings.csv");
        }
    }
}
