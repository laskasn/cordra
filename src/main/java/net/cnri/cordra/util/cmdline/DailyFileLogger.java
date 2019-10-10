package net.cnri.cordra.util.cmdline;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class DailyFileLogger {

    private static final long HOURS_25 = 1000 * 60 * 60 * 25;
    private static DateTimeFormatter yearMonthDayFormatter = DateTimeFormatter.ofPattern("uuuuMMdd").withZone(ZoneOffset.UTC);

    private Map<String, PrintWriterPlus> printWriters;
    private Path dir;

    public DailyFileLogger(Path dir) {
        this.dir = dir;
        this.printWriters = new HashMap<>();
    }

    public void appendLineToFileForTimestamp(String line, long timestamp) throws IOException {
        PrintWriterPlus printWriterPlus = getPrintWriterForTimestamp(timestamp);
        try {
            printWriterPlus.pw.println(line);
            printWriterPlus.pw.flush();
        } finally {
            printWriterPlus.numUsers.decrementAndGet();
        }
    }

    private synchronized PrintWriterPlus getPrintWriterForTimestamp(long timestamp) throws IOException {
        long now = System.currentTimeMillis();
        String filename = getFilenameForTimestamp(timestamp);
        PrintWriterPlus printWriterPlus = printWriters.get(filename);
        if (printWriterPlus == null) {
            PrintWriter pw = new PrintWriter(Files.newBufferedWriter(dir.resolve(filename), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
            printWriterPlus = new PrintWriterPlus(pw, now);
            printWriters.put(filename, printWriterPlus);
            closeOldPrintWriters();
        } else {
            printWriterPlus.lastUsedTimestamp = now;
        }
        printWriterPlus.numUsers.incrementAndGet();
        return printWriterPlus;
    }

    private void closeOldPrintWriters() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        for (String filename : printWriters.keySet()) {
            PrintWriterPlus printWriterPlus = printWriters.get(filename);
            long timeSinceLastUsed = now - printWriterPlus.lastUsedTimestamp;
            if (timeSinceLastUsed > HOURS_25 && printWriterPlus.numUsers.get() == 0) {
                printWriterPlus.pw.close();
                toRemove.add(filename);
            }
        }
        for (String filename : toRemove) {
            printWriters.remove(filename);
        }
    }

    public static String getFilenameForTimestamp(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        String dateString = yearMonthDayFormatter.format(instant);
        String filename = dateString + ".log";
        return filename;
    }

    public static class PrintWriterPlus {
        public PrintWriter pw;
        public long lastUsedTimestamp;
        public AtomicLong numUsers;

        public PrintWriterPlus(PrintWriter pw, long lastUsedTimestamp) {
            this.pw = pw;
            this.lastUsedTimestamp = lastUsedTimestamp;
            this.numUsers = new AtomicLong();
        }
    }

    public void close() {
        for (String filename : printWriters.keySet()) {
            PrintWriter pw = printWriters.get(filename).pw;
            pw.close();
        }
    }

    public static void main(String[] args) throws Exception {
        DailyFileLogger files = new DailyFileLogger(Paths.get("/users/bhadden/tempfiles/"));
        long now = System.currentTimeMillis();
        files.appendLineToFileForTimestamp("hello", now- HOURS_25);
    }
}
