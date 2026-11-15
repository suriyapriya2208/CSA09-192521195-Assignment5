import java.sql.*;
import java.util.regex.Pattern;

/** B2 - unsafe login vs PreparedStatement vs escape/regex "defence". Use a TEST database only! */
public class LoginDemo {

    static boolean unsafe(Connection c, String id, String pwd) throws SQLException {
        String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + pwd + "'";
        System.out.println("      SQL sent -> " + sql);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            System.out.println("      SQL error: " + e.getMessage());
            return false;
        }
    }

    static boolean safe(Connection c, String id, String pwd) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE id=? AND pwd=?")) {
            ps.setString(1, id);
            ps.setString(2, pwd);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Same unsafe query but silent (used by the weaker alternative defences). */
    static boolean concatQuiet(Connection c, String id, String pwd) throws SQLException {
        String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + pwd + "'";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /** Alternative concept 1: escape quotes only. Weak - it forgets the backslash. */
    static boolean escaped(Connection c, String id, String pwd) throws SQLException {
        return concatQuiet(c, id.replace("'", "''"), pwd.replace("'", "''"));
    }

    /** Alternative concept 2: regex-validate the id, escape the password, still concatenate. */
    static final Pattern ID_OK = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    static boolean regexChecked(Connection c, String id, String pwd) throws SQLException {
        if (!ID_OK.matcher(id).matches()) return false;
        return concatQuiet(c, id, pwd.replace("'", "''"));
    }

    public static void main(String[] args) throws Exception {
        try (Connection c = Db.open()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS users "
                        + "(id VARCHAR(30) PRIMARY KEY, pwd VARCHAR(60) NOT NULL)");
                st.execute("DELETE FROM users");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users VALUES (?,?)")) {
                ps.setString(1, "arun");
                ps.setString(2, "secret123");
                ps.executeUpdate();
            }

            String[][] cases = {
                {"valid user, right password",          "arun",          "secret123"},
                {"valid user, WRONG password",          "arun",          "wrong"},
                {"comment attack (no password needed)", "arun' -- ",     "anything"},
                {"tautology in password",               "arun",          "x' OR '1'='1"},
                {"tautology in both fields",            "' OR 1=1 -- ",  "x"},
                {"backslash attack vs quote-escaping",  "arun\\",        " OR 1=1 -- "},
            };

            System.out.println("=== UNSAFE (string concatenation) vs SAFE (PreparedStatement) ===");
            for (String[] t : cases) {
                System.out.println("\n[" + t[0] + "]  id=" + t[1] + "  pwd=" + t[2]);
                boolean u = unsafe(c, t[1], t[2]);
                boolean s = safe(c, t[1], t[2]);
                System.out.println("   unsafe accepted? " + u + "    PreparedStatement accepted? " + s);
            }

            System.out.println("\n=== ALTERNATIVE DEFENCES (weaker) ===");
            for (String[] t : cases) {
                System.out.printf("%-40s escaped: %-5s regex+escape: %-5s%n", t[0],
                        escaped(c, t[1], t[2]), regexChecked(c, t[1], t[2]));
            }

            System.out.println("\n=== Time per login check (avg of 2000 runs, incl. DB round trip) ===");
            int n = 2000;
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) safe(c, "arun", "secret123");
            System.out.printf("PreparedStatement : %.1f us%n", (System.nanoTime() - t0) / 1e3 / n);
            t0 = System.nanoTime();
            for (int i = 0; i < n; i++) regexChecked(c, "arun", "secret123");
            System.out.printf("regex + escape    : %.1f us%n", (System.nanoTime() - t0) / 1e3 / n);
            t0 = System.nanoTime();
            for (int i = 0; i < n; i++) ID_OK.matcher("arun").matches();
            System.out.printf("regex check alone : %.3f us%n", (System.nanoTime() - t0) / 1e3 / n);
        }
    }
}
