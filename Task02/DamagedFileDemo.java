import java.io.*;
import java.nio.file.*;

/** B2 - construct a damaged bus-pass file and show how a validating reader reacts. */
public class DamagedFileDemo {

    /** Returns an error text, or null if the line is a valid pass record. */
    static String validate(String line) {
        if (line.isBlank()) return "empty line";
        String[] f = line.split(",", -1);
        if (f.length != 5) return "wrong number of fields (" + f.length + " instead of 5)";
        if (f[0].isBlank()) return "empty pass ID";
        try {
            Integer.parseInt(f[3].trim());
        } catch (NumberFormatException e) {
            return "route is not a number: " + f[3];
        }
        try {
            double a = Double.parseDouble(f[4].trim());
            if (a < 0) return "negative amount";
        } catch (NumberFormatException e) {
            return "amount is not numeric: " + f[4];
        }
        return null;
    }

    static void process(Path p) throws IOException {
        int good = 0, bad = 0, n = 0;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                n++;
                String err = validate(line);
                if (err == null) {
                    good++;
                    System.out.printf("  line %d OK       : %s%n", n, line);
                } else {
                    bad++;
                    System.out.printf("  line %d REJECTED : %-30s <- %s%n", n, line, err);
                }
            }
        } catch (NoSuchFileException e) {
            System.out.println("  File not found: " + p + "  (treated as 'no passes yet', no crash)");
            return;
        }
        System.out.printf("  Summary: %d valid, %d rejected, program continued normally%n", good, bad);
    }

    public static void main(String[] args) throws IOException {
        Path f = Files.createTempFile("damaged", ".csv");
        Files.writeString(f, String.join("\n",
                "P1,192311001,Arun,7,4500.0",     // good
                "P2,192311002,Meena,9",           // wrong number of fields
                "P3,192311003,Ravi,7,abc",        // non-numeric amount
                "",                               // empty line
                "P5,192311005,Divya,xx,3800.0",   // non-numeric route
                "P6,192311006,Kavi,12,3900.0",    // good
                "P7,192311007,Sam,3"));           // last line cut short (power-failure style)
        System.out.println("=== Damaged file ===");
        process(f);
        System.out.println("\n=== Missing file ===");
        process(Path.of("does_not_exist.csv"));
        Files.delete(f);
    }
}
