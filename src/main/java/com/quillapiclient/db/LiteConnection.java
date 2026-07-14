package com.quillapiclient.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite connection access for the application.
 *
 * <h2>Connection policy (non-negotiable)</h2>
 *
 * <ul>
 *   <li><b>EDT / interactive UI work</b> — call {@link #getConnection()} (or DAOs
 *       that do). Uses the shared singleton. SQLite JDBC connections are not
 *       safe for concurrent use by multiple threads, so this connection must
 *       only be used from one thread at a time; in practice that is the EDT.
 *   <li><b>Background work</b> (SwingWorker, executor pool, long imports) —
 *       wrap the entire unit of work in {@link #withNewConnection}. That opens
 *       a dedicated connection, binds it for the current thread so
 *       {@link #getConnection()} (and every DAO that uses it) participates in
 *       the same connection, then closes it when the block finishes.
 * </ul>
 *
 * <p>WAL mode (enabled in {@link DatabaseSchema}) lets a dedicated background
 * connection read/write concurrently with the shared EDT connection without
 * blocking, as long as two writers don't collide — {@code busy_timeout} makes
 * a collision retry instead of failing immediately.
 *
 * <p>Never call {@link #getConnection()} from a background thread outside
 * {@link #withNewConnection}: that reintroduces the interleaving bug where a
 * concurrent EDT read/write is swept into the background transaction.
 */
public class LiteConnection {

    private static final String APP_NAME = "quillclient";
    private static final String DB_DIR =
        System.getProperty("user.home") +
        File.separator +
        "." +
        APP_NAME;
    private static final String DB_FILE = "app.db";
    // Overridable so tests/benchmarks can point at a throwaway database
    private static final String DB_PATH = System.getProperty(
        "quill.db.path",
        DB_DIR + File.separator + DB_FILE
    );
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    private static final int BUSY_TIMEOUT_MS = 5000;

    /**
     * Connection bound by {@link #withNewConnection} for the current thread.
     * When set, {@link #getConnection()} returns it so existing DAOs stay
     * correct without each method taking a {@link Connection} parameter.
     */
    private static final ThreadLocal<Connection> BOUND = new ThreadLocal<>();

    private static Connection sharedConnection;
    private static boolean driverLoaded = false;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println(
                "SQLite JDBC driver not found. Make sure sqlite-jdbc is in the classpath."
            );
            e.printStackTrace();
        }
    }

    /**
     * Work that receives an open connection and may throw checked exceptions.
     */
    @FunctionalInterface
    public interface SqlCallable<T> {
        T call(Connection conn) throws Exception;
    }

    /**
     * Work that receives an open connection, returns nothing, and may throw.
     */
    @FunctionalInterface
    public interface SqlRunnable {
        void run(Connection conn) throws Exception;
    }

    /**
     * Connection for the current call site.
     *
     * <p>If this thread is inside {@link #withNewConnection}, returns that
     * dedicated connection. Otherwise returns the shared singleton (EDT use).
     *
     * @return a live Connection — never close the shared singleton yourself;
     *         use {@link #closeConnection()} at app shutdown
     */
    public static Connection getConnection() {
        Connection bound = BOUND.get();
        if (bound != null) {
            return bound;
        }
        return sharedSingleton();
    }

    /**
     * Runs {@code work} on a dedicated connection independent of the shared
     * singleton, then closes it. Nested calls on the same thread reuse the
     * outer connection (no nested open/close).
     *
     * <p>While {@code work} runs, {@link #getConnection()} returns this
     * connection so DAOs written against the shared API are safe on background
     * threads.
     *
     * @param work unit of work; may throw — checked exceptions are wrapped in
     *             {@link RuntimeException}
     * @return the value returned by {@code work}
     */
    public static <T> T withNewConnection(SqlCallable<T> work) {
        Connection existing = BOUND.get();
        if (existing != null) {
            return invoke(work, existing);
        }

        Connection conn = null;
        try {
            conn = openNewConnection();
            BOUND.set(conn);
            return invoke(work, conn);
        } catch (SQLException e) {
            throw new RuntimeException(
                "Failed to open database connection at: " + DB_PATH,
                e
            );
        } finally {
            BOUND.remove();
            closeQuietly(conn);
        }
    }

    /**
     * Like {@link #withNewConnection(SqlCallable)} for work that returns void.
     *
     * <p>Deliberately not an overload of {@code withNewConnection}: an
     * expression lambda whose body calls a value-returning method matches both
     * {@link SqlCallable} and {@link SqlRunnable}, so overloading the name
     * makes such call sites ambiguous and fails the compile. Separate names
     * keep every lambda unambiguous.
     */
    public static void runWithNewConnection(SqlRunnable work) {
        withNewConnection(conn -> {
            work.run(conn);
            return null;
        });
    }

    /**
     * Opens a standalone connection without binding it to the thread.
     * Prefer {@link #withNewConnection} unless you are managing lifecycle and
     * passing the connection explicitly (e.g. multi-step importers).
     * Callers are responsible for closing it ({@link #closeQuietly}).
     *
     * @return a new Connection to the same database file
     * @throws SQLException if the connection cannot be opened
     */
    public static Connection openNewConnection() throws SQLException {
        if (!driverLoaded) {
            throw new RuntimeException("SQLite JDBC driver not loaded");
        }
        ensureDbDirectoryExists();
        Connection conn = DriverManager.getConnection(DB_URL);
        configureConnection(conn);
        return conn;
    }

    /**
     * Closes a connection, logging failures. Safe to call with null.
     * Does not touch the shared singleton — use {@link #closeConnection()} for that.
     */
    public static void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            System.err.println(
                "Error closing database connection: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    /** Creates the database's parent directory if it doesn't already exist. */
    private static void ensureDbDirectoryExists() {
        File dbDir = new File(DB_PATH).getParentFile();
        if (dbDir != null && !dbDir.exists()) {
            boolean created = dbDir.mkdirs();
            if (!created) {
                throw new RuntimeException(
                    "Failed to create database directory: " + dbDir
                );
            }
        }
    }

    /** Applies the PRAGMAs every connection to this database needs. */
    private static void configureConnection(Connection conn)
        throws SQLException {
        conn.createStatement().execute("PRAGMA foreign_keys = ON");
        conn
            .createStatement()
            .execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
    }

    private static Connection sharedSingleton() {
        if (sharedConnection == null) {
            if (!driverLoaded) {
                throw new RuntimeException("SQLite JDBC driver not loaded");
            }
            try {
                ensureDbDirectoryExists();
                sharedConnection = DriverManager.getConnection(DB_URL);
                configureConnection(sharedConnection);
                System.out.println("SQLite connected to: " + DB_PATH);
            } catch (SQLException e) {
                throw new RuntimeException(
                    "Failed to connect to SQLite database at: " + DB_PATH,
                    e
                );
            }
        }
        return sharedConnection;
    }

    private static <T> T invoke(SqlCallable<T> work, Connection conn) {
        try {
            return work.call(conn);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Closes the shared singleton connection.
     * Should be called when the application shuts down.
     */
    public static void closeConnection() {
        if (sharedConnection != null) {
            try {
                sharedConnection.close();
                sharedConnection = null;
                System.out.println("SQLite connection closed");
            } catch (SQLException e) {
                System.err.println(
                    "Error closing SQLite connection: " + e.getMessage()
                );
                e.printStackTrace();
            }
        }
    }

    /**
     * Checks if the shared connection is valid.
     *
     * @return true if connection is valid, false otherwise
     */
    public static boolean isConnectionValid() {
        if (sharedConnection == null) {
            return false;
        }
        try {
            return sharedConnection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Gets the database file path.
     *
     * @return The full path to the database file
     */
    public static String getDbPath() {
        return DB_PATH;
    }
}
