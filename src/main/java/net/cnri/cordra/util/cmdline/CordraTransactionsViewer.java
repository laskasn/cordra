package net.cnri.cordra.util.cmdline;

import java.io.File;
import java.util.Map;

import net.cnri.cordra.collections.PersistentMap;
import net.cnri.cordra.indexer.CordraTransaction;

public class CordraTransactionsViewer {

    private static Map<Long, CordraTransaction> transactionsMap;
    private static Map<String, Long> txnStatus;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Missing dir in args.");
        }
        String dir = args[0];
        File txnsDir = new File(dir);
        boolean isReadOnly = true;
        transactionsMap = new PersistentMap<>(txnsDir, "transactionsMap", Long.class, CordraTransaction.class, isReadOnly);
        txnStatus = new PersistentMap<>(txnsDir, "txnStatus", String.class, Long.class, isReadOnly);

        System.out.println("transactionsMap: " + transactionsMap);
        System.out.println("txnStatus:       " + txnStatus);
    }
}
