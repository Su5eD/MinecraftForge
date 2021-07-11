/*
 * The FML Forge Mod Loader suite.
 * Copyright (C) 2012 cpw
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package cpw.mods.fml.relauncher;

import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;

public class FMLRelaunchLog {

    private static class ConsoleLogWrapper extends Handler {
        @Override
        public void publish(LogRecord record) {
            boolean currInt = Thread.interrupted();
            try {
                ConsoleLogThread.recordQueue.put(record);
            } catch (InterruptedException e) {
                e.printStackTrace(errCache);
            }
            if (currInt) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {
        }

    }

    private static class ConsoleLogThread implements Runnable {
        static ConsoleHandler wrappedHandler = new ConsoleHandler();
        static LinkedBlockingQueue<LogRecord> recordQueue = new LinkedBlockingQueue<>();

        @Override
        public void run() {
            do {
                LogRecord lr;
                try {
                    lr = recordQueue.take();
                    wrappedHandler.publish(lr);
                } catch (InterruptedException e) {
                    e.printStackTrace(errCache);
                    Thread.interrupted();
                    // Stupid
                }
            }
            while (true);
        }
    }

    private static class LoggingOutStream extends ByteArrayOutputStream {
        private Logger log;
        private StringBuilder currentMessage;

        public LoggingOutStream(Logger log) {
            this.log = log;
            this.currentMessage = new StringBuilder();
        }

        @Override
        public void flush() throws IOException {
            String record;
            synchronized (FMLRelaunchLog.class) {
                super.flush();
                record = this.toString();
                super.reset();

                currentMessage.append(record.replace(FMLLogFormatter.LINE_SEPARATOR, "\n"));
                if (currentMessage.lastIndexOf("\n") >= 0) {
                    // Are we longer than just the line separator?
                    if (currentMessage.length() > 1) {
                        // Trim the line separator
                        currentMessage.setLength(currentMessage.length() - 1);
                        log.log(Level.INFO, currentMessage.toString());
                    }
                    currentMessage.setLength(0);
                }
            }
        }
    }

    /**
     * Our special logger for logging issues to. We copy various assets from the
     * Minecraft logger to achieve a similar appearance.
     */
    public static FMLRelaunchLog log = new FMLRelaunchLog();

    static File minecraftHome;
    private static boolean configured;

    private static Thread consoleLogThread;

    private static PrintStream errCache;
    private Logger myLog;

    private static FileHandler fileHandler;

    private static FMLLogFormatter formatter;

    private FMLRelaunchLog() {
    }

    /**
     * Configure the FML logger
     */
    private static void configureLogging() {
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globalLogger.setLevel(Level.OFF);

        log.myLog = Logger.getLogger("ForgeModLoader");

        Logger stdOut = Logger.getLogger("STDOUT");
        stdOut.setParent(log.myLog);
        Logger stdErr = Logger.getLogger("STDERR");
        stdErr.setParent(log.myLog);
        log.myLog.setLevel(Level.ALL);
        log.myLog.setUseParentHandlers(false);
        consoleLogThread = new Thread(new ConsoleLogThread());
        consoleLogThread.start();
        formatter = new FMLLogFormatter();
        try {
            File logPath = new File(minecraftHome, FMLRelauncher.logFileNamePattern);
            fileHandler = new FileHandler(logPath.getPath(), 0, 3) {
                public synchronized void close() throws SecurityException {
                    // We don't want this handler to reset
                }
            };
        } catch (Exception e) {
        }

        resetLoggingHandlers();

        // Set system out to a log stream
        errCache = System.err;

        System.setOut(new PrintStream(new LoggingOutStream(stdOut), true));
        System.setErr(new PrintStream(new LoggingOutStream(stdErr), true));

        configured = true;
    }

    private static void resetLoggingHandlers() {
        ConsoleLogThread.wrappedHandler.setLevel(Level.parse(System.getProperty("fml.log.level", "INFO")));
        // Console handler captures the normal stderr before it gets replaced
        log.myLog.addHandler(new ConsoleLogWrapper());
        ConsoleLogThread.wrappedHandler.setFormatter(formatter);
        fileHandler.setLevel(Level.ALL);
        fileHandler.setFormatter(formatter);
        log.myLog.addHandler(fileHandler);
    }

    public static void loadLogConfiguration(File logConfigFile) {
        if (logConfigFile != null && logConfigFile.exists() && logConfigFile.canRead()) {
            try {
                LogManager.getLogManager().readConfiguration(new FileInputStream(logConfigFile));
                resetLoggingHandlers();
            } catch (Exception e) {
                log(Level.SEVERE, e, "Error reading logging configuration file %s", logConfigFile.getName());
            }
        }
    }

    public static void log(String logChannel, Level level, String format, Object... data) {
        makeLog(logChannel);
        Logger.getLogger(logChannel).log(level, String.format(format, data));
    }

    public static void log(Level level, String format, Object... data) {
        if (!configured) {
            configureLogging();
        }
        log.myLog.log(level, String.format(format, data));
    }

    public static void log(String logChannel, Level level, Throwable ex, String format, Object... data) {
        makeLog(logChannel);
        Logger.getLogger(logChannel).log(level, String.format(format, data), ex);
    }

    public static void log(Level level, Throwable ex, String format, Object... data) {
        if (!configured) {
            configureLogging();
        }
        log.myLog.log(level, String.format(format, data), ex);
    }

    public static void severe(String format, Object... data) {
        log(Level.SEVERE, format, data);
    }

    public static void warning(String format, Object... data) {
        log(Level.WARNING, format, data);
    }

    public static void info(String format, Object... data) {
        log(Level.INFO, format, data);
    }

    public static void fine(String format, Object... data) {
        log(Level.FINE, format, data);
    }

    public static void finer(String format, Object... data) {
        log(Level.FINER, format, data);
    }

    public static void finest(String format, Object... data) {
        log(Level.FINEST, format, data);
    }

    public Logger getLogger() {
        return myLog;
    }

    public static void makeLog(String logChannel) {
        Logger l = Logger.getLogger(logChannel);
        l.setParent(log.myLog);
    }
}
