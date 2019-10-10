package net.cnri.cordra.util.cmdline;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class ReindexBatchTool {

    private Map<Long, Batch> batchMap = new HashMap<>();
    private ContiguousPositiveLongs successfulBatchIds = new ContiguousPositiveLongs();
    private String filename;
    private int batchSize;
    private long startPosition;
    private String username;
    private String password;
    private String baseUri;
    private ExecutorService exec = null;
    private CordraClient cordra;
    private int numThreads = 10;
    private int queueSize;
    private Options cordraOptions;
    private long totalCount;
    private volatile boolean halt = false;

    public ReindexBatchTool(OptionSet options) throws CordraException {
        filename = (String) options.valueOf("f");//"/users/bhadden/ids.txt";
        String batchSizeString = (String) options.valueOf("c");
        batchSize = Integer.parseInt(batchSizeString);
        String startPositionString = (String) options.valueOf("s");
        startPosition = Long.parseLong(startPositionString);
        if (startPosition < 1) {
            startPosition = 1;
        }
        username = (String) options.valueOf("u");
        password = (String) options.valueOf("p");
        baseUri = (String) options.valueOf("b");//"http://localhost:8081/cordra/";
        String numThreadsString = (String) options.valueOf("n");
        numThreads = Integer.parseInt(numThreadsString);
        if (numThreads < 1) {
            numThreads = 1;
        }
        queueSize = numThreads * 3;

        cordraOptions = new Options();
        String lockObjectsString = (String) options.valueOf("l");
        if ("false".equals(lockObjectsString)) {
            cordraOptions.reindexBatchLockObjects = false;
        } else {
            cordraOptions.reindexBatchLockObjects = true;
        }

        cordraOptions.username = username;
        cordraOptions.password = password;
        cordra = new TokenUsingHttpCordraClient(baseUri, username, password);
    }

    public void run() {
        long start = System.currentTimeMillis();
        BlockingQueue<Runnable> queue = new LinkedBlockingDeque<>(queueSize);
        try (Stream<String> linesStream = getLines()) {
            Iterator<String> linesIter = linesStream.iterator();
            exec = new ThreadPoolExecutor(numThreads, numThreads, 0, TimeUnit.MILLISECONDS, queue, new ThreadPoolExecutor.CallerRunsPolicy());
            long startPositionZeroBased = startPosition -1;
            if (!"-".equals(filename)) totalCount = countLinesInFile(filename);
            long position = 0;
            long batchId = 0;
            skipToStart(linesIter, startPositionZeroBased);
            position = startPositionZeroBased;
            while (!halt) {
                List<String> lines = readNextBatchOfLines(linesIter, batchSize);
                Batch batch = new Batch(lines, position, batchId);
                batchMap.put(batchId, batch);
                batchId++;
                if (batch.size() > 0) {
                    exec.submit(() -> {
                        boolean success = sendBatch(batch, cordra, cordraOptions);
                        if (success) {
                            successfulBatchIds.insert(batch.batchId);
                            updateProgress();
                        } else {
                            halt = true;
                        }
                    });
                    position = batch.position + batch.size();
                } else {
                    break;
                }
                if (batch.size() < batchSize) {
                    //last batch was sent
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println(e);
        } finally {
            shutdownExecServAndWait(exec);
        }
        if (halt) {
            printError();
        } else {
            long end = System.currentTimeMillis();
            long runningTime = end - start;
            printComplete(runningTime);
        }
    }

    private Stream<String> getLines() throws IOException {
        if ("-".equals(filename)) {
            return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).lines();
        }
        return Files.lines(Paths.get(filename));
    }


    public static void main(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        ReindexBatchTool tool = new ReindexBatchTool(options);
        tool.run();
    }

    public synchronized void updateProgress() {
        long lastCompletedBatchId = this.successfulBatchIds.highest();
        Batch lastCompletedBatch = this.batchMap.get(lastCompletedBatchId);
        long positionOfLastSuccessfulItem = lastCompletedBatch.lastItemPosition();
        if (totalCount > 0) {
            long percent = (positionOfLastSuccessfulItem * 100) / totalCount;
            System.out.print("\r" + "Progress: " + percent + "% " + positionOfLastSuccessfulItem + "/" +totalCount);
        } else {
            System.out.print("\r" + "Progress: " + positionOfLastSuccessfulItem);
        }
        pruneBatchMap(lastCompletedBatchId);
    }

    private synchronized void pruneBatchMap(long lastCompletedBatchId) {
        long i = lastCompletedBatchId -1;
        while (true) {
            if (batchMap.containsKey(i)) {
                batchMap.remove(i);
            } else {
                break;
            }
            i--;
        }
    }


    private synchronized void printError() {
        System.out.println();
        System.out.println("An error occured. Reindex halted.");
        long lastCompletedBatchId = this.successfulBatchIds.highest();
        if (lastCompletedBatchId == -1) {
            System.out.println("Zero items were successful.");
        } else {
            Batch lastCompletedBatch = this.batchMap.get(lastCompletedBatchId);
            long positionOfLastSuccessfulItem = lastCompletedBatch.lastItemPosition();
            System.out.println("The position of the last successful item was " + positionOfLastSuccessfulItem);
            System.out.println("To resume from this position use the option: -s " + (positionOfLastSuccessfulItem +1));
        }
    }

    private void printComplete(long runningTime) {
        System.out.println();
        double runningTimeSeconds = runningTime/1000d;
        if (totalCount > 0) {
            long rate = (long) (totalCount / runningTimeSeconds);
            System.out.println("All requested objects reindexed. Total run time: " + runningTimeSeconds + " seconds. Rate " + rate + " objects/s");
        } else {
            System.out.println("All requested objects reindexed. Total run time: " + runningTimeSeconds + " seconds.");
        }
    }


    public static boolean sendBatch(Batch batch, CordraClient cordra, Options options) {
        try {
            cordra.reindexBatch(batch.ids, options);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> readNextBatchOfLines(Iterator<String> linesIter, int batchSize) {
        List<String> lines = new ArrayList<>();
        while (linesIter.hasNext()) {
            String line = linesIter.next();
            lines.add(line);
            if (lines.size() == batchSize) {
                break;
            }
        }
        return lines;
    }

    public static void skipToStart(Iterator<String> linesIter, long startPosition) {
        long count = 0;
        while (count < startPosition) {
            if (linesIter.hasNext()) {
                linesIter.next();
                count++;
            } else {
                break;
            }
        }
    }

    private static long countLinesInFile(String filename) throws IOException {
        try (Stream<String> linesStream = Files.lines(Paths.get(filename))) {
            return linesStream.count();
        }
    }

    private static OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Prints help").forHelp();
        parser.acceptsAll(Arrays.asList("u", "username"), "Cordra username").withRequiredArg().defaultsTo("admin");
        parser.acceptsAll(Arrays.asList("p", "password"), "Cordra password").withRequiredArg();
        parser.acceptsAll(Arrays.asList("f", "file"), "Path to input file, - for stdin").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("b", "base-uri"), "Cordra base URI").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("c", "batch-size"), "Number of ids to be sent with each request. Assuming object locking on reindexing is enabled increasing the batch size will have a negative impact on performance. The default is likely to be optimal.").withRequiredArg().defaultsTo("16");
        parser.acceptsAll(Arrays.asList("s", "start"), "Start position where the first line is 1").withRequiredArg().defaultsTo("1");
        parser.acceptsAll(Arrays.asList("n", "num-threads"), "Number of request threads").withRequiredArg().defaultsTo("48");
        parser.acceptsAll(Arrays.asList("l", "lock-objects"), "Flag indicating if object ids should be locked during reindexing. Warning this should only be set to false if Cordra is not in use. If set to false increasing the batch-size can benefit performance performance.").withRequiredArg().defaultsTo("true");

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("Reindex Batch Tool\n" +
                    "\n" +
                    "This tool reads a file that contains new line separated cordra ids. It divides this stream of ids into batches and for\n" +
                    "each batch makes a request to the cordra /reindexBatch/ api. The tool is multi-threaded, submitting multiple\n" +
                    "reindexBatch requests simultaneously. If the tool is stopped before completing the file, it can be restarted from any\n" +
                    "line in the file using the -s option.\n" +
                    "\n" +
                    "A minimal example call might use the following options:\n" +
                    "\n" +
                    " -b http://localhost:8080/ -u admin -p password -f ./ids.txt");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        return options;
    }

    private static void shutdownExecServAndWait(ExecutorService exec) {
        if (exec != null) {
            exec.shutdown();
            try {
                exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                exec.shutdownNow();
            }
        }
    }

    public static class Batch {
        public final List<String> ids;
        public final long position;
        public final long batchId;

        public Batch(List<String> ids, long position, long batchId) {
            this.ids = ids;
            this.position = position;
            this.batchId = batchId;
        }

        public int size() { return ids.size(); }

        public long lastItemPosition() {
            return position + size();
        }
    }
}
