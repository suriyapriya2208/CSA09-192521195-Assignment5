import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * B1.3 - buffered vs unbuffered line counting (1, 10, 50 MB)
 * B1.4 - time to find a pass as file grows (1,000 .. 100,000 records), plus HashMap alternative.
 * Results are printed and saved to results/*.csv
 */
public class Task01Benchmark {

    // ---------- line counters ----------
    static long countUnbuffered(Path p) throws IOException {
        long lines = 0;
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            int b;
            while ((b = in.read()) != -1) if (b == '\n') lines++;   // 1 OS call per byte
        }
        return lines;
    }

    static long countBufferedStream(Path p) throws IOException {
        long lines = 0;
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(p.toFile()), 8192)) {
            int b;
            while ((b = in.read()) != -1) if (b == '\n') lines++;   // 1 OS call per 8 KB
        }
        return lines;
    }

    static long countByteArray(Path p) throws IOException {
        long lines = 0;
        byte[] buf = new byte[8192];
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            int n;
            while ((n = in.read(buf)) != -1)
                for (int i = 0; i < n; i++) if (buf[i] == '\n') lines++;
        }
        return lines;
    }

    static long countReadAllLines(Path p) throws IOException {
        return Files.readAllLines(p).size();                         // whole file in memory
    }

    // ---------- file generation ----------
    static void makeFileOfSize(Path p, long targetBytes) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            long written = 0; int i = 0;
            while (written < targetBytes) {
                String line = "2026-01-" + (1 + i % 28) + " student" + i + " entered hostel gate " + (i % 5) + " ok";
                w.write(line); w.newLine();
                written += line.length() + 1; i++;
            }
        }
    }

    static void makePassFile(Path p, int n) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            for (int i = 1; i <= n; i++) {
                w.write(String.join(",", "P" + i, "19231" + String.format("%07d", i),
                        "Student" + i, String.valueOf(1 + i % 20), String.valueOf(3000.0 + i % 2000)));
                w.newLine();
            }
        }
    }

    interface Counter { long run(Path p) throws IOException; }

    static double time(Counter c, Path p) throws IOException {
        long t0 = System.nanoTime();
        c.run(p);
        return (System.nanoTime() - t0) / 1e6;   // ms
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("results"));

        // ===== B1.3 =====
        int[] sizesMB = {1, 10, 50};
        StringBuilder csv = new StringBuilder("sizeMB,unbuffered_ms,bufferedStream_ms,byteArray_ms,readAllLines_ms\n");
        System.out.println("=== B1.3 Line counting (ms) ===");
        System.out.printf("%-8s %-14s %-16s %-12s %-14s%n", "MB", "unbuffered", "BufferedStream", "byte[8192]", "readAllLines");
        for (int mb : sizesMB) {
            Path f = Files.createTempFile("attendance", ".log");
            makeFileOfSize(f, mb * 1024L * 1024L);
            countByteArray(f); // warm-up
            double u = time(Task01Benchmark::countUnbuffered, f);
            double b = time(Task01Benchmark::countBufferedStream, f);
            double a = time(Task01Benchmark::countByteArray, f);
            double r;
            try { r = time(Task01Benchmark::countReadAllLines, f); }
            catch (OutOfMemoryError e) { r = -1; }
            System.out.printf("%-8d %-14.1f %-16.1f %-12.1f %-14.1f%n", mb, u, b, a, r);
            csv.append(String.format("%d,%.1f,%.1f,%.1f,%.1f%n", mb, u, b, a, r));
            System.out.printf("   (read() calls: unbuffered = %,d, buffered OS reads ~ %,d)%n",
                    Files.size(f) + 1, Files.size(f) / 8192 + 1);
            Files.delete(f);
        }
        Files.writeString(Path.of("results/b1_3_buffering.csv"), csv.toString());

        // ===== B1.4 =====
        int[] ns = {1000, 5000, 10000, 25000, 50000, 100000};
        csv = new StringBuilder("n,find_last_ms,find_missing_ms,hashmap_load_ms,hashmap_lookup_ms\n");
        System.out.println("\n=== B1.4 Search growth (average of 20 runs, ms) ===");
        System.out.printf("%-9s %-14s %-16s %-16s %-16s%n", "records", "find(last)", "find(missing)", "HashMap load", "HashMap lookup");
        for (int n : ns) {
            Path f = Files.createTempFile("passes", ".csv");
            makePassFile(f, n);
            PassStore s = new PassStore(f);
            s.find("P" + n); // warm-up
            int runs = 20;
            long t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) s.find("P" + n);
            double last = (System.nanoTime() - t0) / 1e6 / runs;
            t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) s.find("NOPE");
            double missing = (System.nanoTime() - t0) / 1e6 / runs;

            t0 = System.nanoTime();
            Map<String, String[]> map = new HashMap<>();
            for (String line : Files.readAllLines(f)) {
                String[] x = line.split(",", -1);
                if (x.length == 5) map.put(x[0], x);
            }
            double load = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            for (int i = 0; i < 100000; i++) map.get("P" + n);
            double lookup = (System.nanoTime() - t0) / 1e6 / 100000;

            System.out.printf("%-9d %-14.3f %-16.3f %-16.3f %-16.6f%n", n, last, missing, load, lookup);
            csv.append(String.format("%d,%.3f,%.3f,%.3f,%.6f%n", n, last, missing, load, lookup));
            Files.delete(f);
        }
        Files.writeString(Path.of("results/b1_4_growth.csv"), csv.toString());
        System.out.println("\nSaved: results/b1_3_buffering.csv and results/b1_4_growth.csv");
    }
}
