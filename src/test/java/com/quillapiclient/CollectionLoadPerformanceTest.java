package com.quillapiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.quillapiclient.controller.CollectionTreeManager;
import com.quillapiclient.controller.RequestController;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.DatabaseSchema;
import com.quillapiclient.db.LiteConnection;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Memory/performance benchmark for loading a large (3000 request) collection.
 * Uses a throwaway database via the quill.db.path system property, so the
 * user's real database is never touched.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionLoadPerformanceTest {

    private static final int REQUEST_COUNT = 3000;

    private static Path tempDir;
    private static int importedCollectionId = -1;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        tempDir = Files.createTempDirectory("quill-bench");
        System.setProperty(
            "quill.db.path",
            tempDir.resolve("bench.db").toString()
        );
        DatabaseSchema.initializeSchema();
    }

    @AfterAll
    static void tearDownDatabase() throws Exception {
        LiteConnection.closeConnection();
        try (var paths = Files.walk(tempDir)) {
            paths
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    @Order(1)
    void importsLargeCollectionWithBoundedRetainedMemory() throws Exception {
        File collectionFile = generateCollectionFile(REQUEST_COUNT);
        long fileSizeKb = collectionFile.length() / 1024;

        long heapBefore = settledUsedHeap();
        long startNanos = System.nanoTime();

        importedCollectionId = CollectionDao.importCollectionFile(
            collectionFile,
            collectionFile.getName()
        );

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        long retainedKb = Math.max(0, settledUsedHeap() - heapBefore) / 1024;

        assertTrue(importedCollectionId > 0, "import should succeed");
        assertEquals(
            REQUEST_COUNT,
            countItems(importedCollectionId),
            "all requests should be persisted"
        );

        System.out.printf(
            "[bench] streaming import: %d requests, file %d KB, took %d ms, retained heap after import %d KB%n",
            REQUEST_COUNT,
            fileSizeKb,
            durationMs,
            retainedKb
        );

        // The importer streams one item at a time, so it must not retain the
        // full parsed collection. Allow generous slack for GC noise and
        // SQLite/JDBC internals.
        assertTrue(
            retainedKb < 50 * 1024,
            "retained heap after import should stay far below the eager-parse footprint, was " +
                retainedKb + " KB"
        );
    }

    @Test
    @Order(2)
    void treeMaterializesOnlyExpandedLevels() throws Exception {
        assertTrue(importedCollectionId > 0, "import test must run first");

        AtomicReference<CollectionTreeManager> managerRef =
            new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
            managerRef.set(new CollectionTreeManager(new RequestController()))
        );
        CollectionTreeManager manager = managerRef.get();

        long heapBefore = settledUsedHeap();
        manager.loadAllCollections(); // schedules the model swap on the EDT
        SwingUtilities.invokeAndWait(() -> {}); // flush the EDT

        JTree tree = manager.getTree();
        DefaultMutableTreeNode root =
            (DefaultMutableTreeNode) tree.getModel().getRoot();
        assertTrue(root.getChildCount() >= 1, "collection node should exist");

        DefaultMutableTreeNode collectionNode = findCollectionNode(root);
        assertNotNull(collectionNode, "imported collection should be in tree");
        assertEquals(
            1,
            collectionNode.getChildCount(),
            "collapsed collection should hold only the lazy placeholder"
        );

        long lazyHeapKb = Math.max(0, settledUsedHeap() - heapBefore) / 1024;
        System.out.printf(
            "[bench] lazy tree before expansion: 1 placeholder node, retained heap %d KB%n",
            lazyHeapKb
        );

        // Expanding the collection materializes exactly one level. The read
        // runs on a worker, so the level lands some time after expandPath
        // returns.
        long expandStart = System.nanoTime();
        SwingUtilities.invokeAndWait(() ->
            tree.expandPath(new TreePath(collectionNode.getPath()))
        );
        waitUntil(
            () -> collectionNode.getChildCount() == REQUEST_COUNT,
            30_000,
            "expansion should load the full flat collection level"
        );
        long expandMs = (System.nanoTime() - expandStart) / 1_000_000;

        long expandedHeapKb =
            Math.max(0, settledUsedHeap() - heapBefore) / 1024;
        System.out.printf(
            "[bench] expanded one level: %d nodes in %d ms, retained heap %d KB%n",
            collectionNode.getChildCount(),
            expandMs,
            expandedHeapKb
        );
    }

    /**
     * Polls {@code condition} on the EDT until it holds or the timeout expires.
     * Tree children are loaded on a worker, so the assertion cannot run inline
     * with the expand that triggered it.
     */
    private static void waitUntil(
        BooleanSupplier condition,
        long timeoutMs,
        String message
    ) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean satisfied = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() ->
                satisfied.set(condition.getAsBoolean())
            );
            if (satisfied.get()) {
                return;
            }
            Thread.sleep(20);
        }
        fail(message + " (timed out after " + timeoutMs + " ms)");
    }

    private static DefaultMutableTreeNode findCollectionNode(
        DefaultMutableTreeNode root
    ) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child =
                (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.getUserObject().toString().contains("bench")) {
                return child;
            }
        }
        return null;
    }

    /**
     * Writes a flat Postman collection with the given number of requests,
     * shaped like a real Postman v2.1 export (headers, url parts, body).
     */
    private static File generateCollectionFile(int requestCount)
        throws Exception {
        File file = tempDir
            .resolve("bench.postman_collection.json")
            .toFile();

        JsonFactory factory = new JsonFactory();
        try (
            JsonGenerator gen = factory.createGenerator(
                file,
                com.fasterxml.jackson.core.JsonEncoding.UTF8
            )
        ) {
            gen.writeStartObject();

            gen.writeObjectFieldStart("info");
            gen.writeStringField("_postman_id", "bench-collection-0001");
            gen.writeStringField("name", "bench collection");
            gen.writeStringField(
                "schema",
                "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
            );
            gen.writeEndObject();

            gen.writeArrayFieldStart("item");
            for (int i = 0; i < requestCount; i++) {
                gen.writeStartObject();
                gen.writeStringField("name", "Request " + i);

                gen.writeObjectFieldStart("request");
                gen.writeStringField(
                    "method",
                    switch (i % 4) {
                        case 0 -> "GET";
                        case 1 -> "POST";
                        case 2 -> "PUT";
                        default -> "DELETE";
                    }
                );

                gen.writeArrayFieldStart("header");
                gen.writeStartObject();
                gen.writeStringField("key", "Content-Type");
                gen.writeStringField("value", "application/json");
                gen.writeStringField("type", "text");
                gen.writeEndObject();
                gen.writeStartObject();
                gen.writeStringField("key", "Authorization");
                gen.writeStringField("value", "Bearer {{token}}");
                gen.writeStringField("type", "text");
                gen.writeEndObject();
                gen.writeEndArray();

                gen.writeObjectFieldStart("body");
                gen.writeStringField("mode", "raw");
                gen.writeStringField(
                    "raw",
                    "{\"index\": " + i + ", \"payload\": \"benchmark body content for request " + i + "\"}"
                );
                gen.writeEndObject();

                gen.writeObjectFieldStart("url");
                gen.writeStringField(
                    "raw",
                    "https://api.example.com/v1/resources/" + i + "?page=1"
                );
                gen.writeStringField("protocol", "https");
                gen.writeArrayFieldStart("host");
                gen.writeString("api");
                gen.writeString("example");
                gen.writeString("com");
                gen.writeEndArray();
                gen.writeArrayFieldStart("path");
                gen.writeString("v1");
                gen.writeString("resources");
                gen.writeString(String.valueOf(i));
                gen.writeEndArray();
                gen.writeArrayFieldStart("query");
                gen.writeStartObject();
                gen.writeStringField("key", "page");
                gen.writeStringField("value", "1");
                gen.writeEndObject();
                gen.writeEndArray();
                gen.writeEndObject(); // url

                gen.writeEndObject(); // request
                gen.writeEndObject(); // item
            }
            gen.writeEndArray();

            gen.writeEndObject();
        }

        return file;
    }

    private static int countItems(int collectionId) throws Exception {
        Connection conn = LiteConnection.getConnection();
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM items WHERE collection_id = ?"
            )
        ) {
            stmt.setInt(1, collectionId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Used heap after giving the GC a chance to settle. */
    private static long settledUsedHeap() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(50);
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
