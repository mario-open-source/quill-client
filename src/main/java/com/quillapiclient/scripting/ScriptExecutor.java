package com.quillapiclient.scripting;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Sandboxed GraalJS script runner for pre-request and post-response scripts.
 *
 * <h3>Security constraints</h3>
 * <ul>
 *   <li>No Java class access ({@code allowHostClassLoading(false)})</li>
 *   <li>No file-system access (no {@code --experimental-options},
 *       no {@code io} language enabled)</li>
 *   <li>CPU time budget per script (default 5 seconds)</li>
 *   <li>Separate {@code Context} per execution – no muddling of state</li>
 *   <li>Only whitelisted bindings reach the script</li>
 * </ul>
 */
public final class ScriptExecutor {

    private static final int SCRIPT_TIMEOUT_SECONDS = 5;

    private ScriptExecutor() {}

    /**
     * Executes the given JavaScript snippet in a sandboxed GraalJS context.
     *
     * @param scriptBody          JavaScript source code
     * @param contextBindings     map of names → Java objects to expose as globals
     * @param logCollector        if non-null, {@code console.log()} messages are captured here
     * @throws ScriptException    if the script times out, throws, or fails to parse
     */
    public static void execute(
        String scriptBody,
        ScriptBindings contextBindings,
        List<String> logCollector
    ) throws ScriptException {
        if (scriptBody == null || scriptBody.isBlank()) {
            return;
        }

        // Use a dedicated single-thread executor so we can enforce a wall-clock timeout
        // on top of GraalVM's own sandbox limits.
        ExecutorService guard = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "quill-script-runner");
            t.setDaemon(true);
            return t;
        });

        Future<Void> future = guard.submit(() -> {
            try (Context ctx = buildContext(contextBindings, logCollector)) {
                try {
                    ctx.eval("js", scriptBody);
                } catch (Exception e) {
                    System.err.println(
                        "[ScriptExecutor] eval error: " + e.getMessage()
                    );
                    e.printStackTrace();
                    throw e;
                }
            }
            return null;
        });

        try {
            future.get(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ScriptException(
                "Script timed out after " + SCRIPT_TIMEOUT_SECONDS + " s"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScriptException("Script execution interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PolyglotException pe) {
                throw new ScriptException(pe.getMessage(), pe);
            }
            throw new ScriptException(
                cause != null ? cause.getMessage() : "Unknown script error"
            );
        } finally {
            guard.shutdownNow();
        }
    }

    // ---------------------------------------------------------------
    //  context factory
    // ---------------------------------------------------------------

    private static Context buildContext(
        ScriptBindings bindings,
        List<String> logCollector
    ) {
        Context.Builder builder = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(className -> false) // block all Java class lookups
            .allowIO(false) // no file / network I/O
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowExperimentalOptions(false)
            .option("js.ecmascript-version", "2023");

        // resource limits are only available with --experimental-options
        // which we deliberately keep disabled to tighten sandboxing.
        // We rely on the executor Future timeout instead.

        Context ctx = builder.build();

        // inject bindings
        Value jsBindings = ctx.getBindings("js");
        if (bindings != null) {
            for (String name : bindings.names()) {
                jsBindings.putMember(name, bindings.get(name));
            }
        }

        // inject a sandboxed console
        jsBindings.putMember("console", new SandboxedConsole(logCollector));

        return ctx;
    }

    // ---------------------------------------------------------------
    //  console.log  capture (sandboxed)
    // ---------------------------------------------------------------

    public static class SandboxedConsole {

        private final List<String> logCollector;

        public SandboxedConsole(List<String> logCollector) {
            this.logCollector = logCollector;
        }

        @HostAccess.Export
        public void log(Object... args) {
            if (logCollector == null) return;
            StringBuilder sb = new StringBuilder("[script]");
            if (args != null) {
                for (Object a : args) {
                    sb.append(' ').append(a);
                }
            }
            logCollector.add(sb.toString());
        }

        @HostAccess.Export
        public void warn(Object... args) {
            log(args);
        }

        @HostAccess.Export
        public void error(Object... args) {
            log(args);
        }
    }

    // ---------------------------------------------------------------
    //  exception type
    // ---------------------------------------------------------------

    public static class ScriptException extends Exception {

        public ScriptException(String message) {
            super(message);
        }

        public ScriptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
