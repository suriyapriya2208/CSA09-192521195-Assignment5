
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {

    static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null ? d : v;
    }

    public static Connection open() throws SQLException {

        String url = env("DB_URL",
                "jdbc:mysql://localhost:3307/transport_test"
                + "?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true");

        String user = env("DB_USER", "root");
        String password = env("DB_PASS", "Pass12345");

        return DriverManager.getConnection(url, user, password);
    }
}
