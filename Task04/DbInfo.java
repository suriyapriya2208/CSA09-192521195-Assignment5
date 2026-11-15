import java.sql.*;

/** B4.2 - report database product, driver version, tables and columns using DatabaseMetaData. */
public class DbInfo {
    public static void main(String[] args) throws Exception {
        try (Connection c = Db.open()) {
            DatabaseMetaData md = c.getMetaData();
            System.out.println("Database product : " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            System.out.println("Driver           : " + md.getDriverName() + " " + md.getDriverVersion());
            System.out.println("JDBC version     : " + md.getJDBCMajorVersion() + "." + md.getJDBCMinorVersion());
            System.out.println("URL / user       : " + md.getURL() + " / " + md.getUserName());
            System.out.println("Supports transactions : " + md.supportsTransactions());
            System.out.println("Supports batch updates: " + md.supportsBatchUpdates());
            System.out.println("Max connections       : " + md.getMaxConnections() + " (0 = no fixed limit)");

            System.out.println("\nTables in catalog '" + c.getCatalog() + "':");
            try (ResultSet t = md.getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (t.next()) {
                    String table = t.getString("TABLE_NAME");
                    System.out.println("  * " + table);
                    try (ResultSet cols = md.getColumns(c.getCatalog(), null, table, "%")) {
                        while (cols.next())
                            System.out.printf("      %-10s %-10s size=%d nullable=%s%n", cols.getString("COLUMN_NAME"),
                                    cols.getString("TYPE_NAME"), cols.getInt("COLUMN_SIZE"), cols.getString("IS_NULLABLE"));
                    }
                    try (ResultSet pk = md.getPrimaryKeys(c.getCatalog(), null, table)) {
                        while (pk.next()) System.out.println("      PRIMARY KEY -> " + pk.getString("COLUMN_NAME"));
                    }
                }
            }
        }
    }
}
