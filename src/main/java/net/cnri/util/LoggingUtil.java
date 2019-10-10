package net.cnri.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

import org.slf4j.Logger;

import net.cnri.cordra.api.CordraException;

public class LoggingUtil {

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);
    
    public static <R> R run(Logger logger, Callable<R> c) throws CordraException {
        long start = System.currentTimeMillis();
        try {
            R result = c.call();
            return result;
        } catch (RuntimeException | Error | CordraException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");           
        }
    }

    public static <R> R runWithoutThrow(Logger logger, Callable<R> c) {
        long start = System.currentTimeMillis();
        try {
            R result = c.call();
            return result;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");           
        }
    }

    public static void run(Logger logger, ThrowingRunnable r) throws CordraException {
        long start = System.currentTimeMillis();
        try {
            r.run();
        } catch (RuntimeException | Error | CordraException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");            
        }
    }

    public static void runWithoutThrow(Logger logger, Runnable r) {
        long start = System.currentTimeMillis();
        r.run();
        long end = System.currentTimeMillis();
        long delta = end - start;
        String caller = getCallingFunction();
        String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
        logger.trace(caller + ": start " + startTime + ", " + delta + "ms");
    }

    @FunctionalInterface
    public static interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static String getCallingFunction() {
        return getCallingFunction(3);
    }

    public static String getCallingFunctionWhenCalledDirectly() {
        return getCallingFunction(2);
    }

    private static String getCallingFunction(int levelOfCaller) {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        if (trace.length > levelOfCaller) {
            String callingFunction = trace[levelOfCaller].getMethodName();
            String serviceInfo = "...";
            for (int i = 0; i < trace.length; i++) {
                StackTraceElement el = trace[i];
                if (el.getClassName().endsWith(".CordraService")) {
                    serviceInfo = "CordraService." + el.getMethodName() + "(" + el.getFileName() + ":" + el.getLineNumber() + ")";
                }
            }
            return callingFunction + " " + serviceInfo;
        } else {
            return "Unknown caller";
        }
    }
}
