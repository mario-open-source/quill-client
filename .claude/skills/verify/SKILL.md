---
name: verify
description: Build, launch, and drive the Quill Client Swing app to verify changes at the GUI surface, with a throwaway database.
---

# Verifying Quill Client changes

## Build

```bash
mvn -q package -DskipTests    # fat jar at target/quillclient-1.0-SNAPSHOT.jar
```

## Isolate the database

Never run verification against the real `~/.quillclient/app.db`.

- Current builds honor `-Dquill.db.path=/tmp/whatever.db` (see LiteConnection).
- Older builds (before the override) resolve `~/.quillclient/app.db` from
  `user.home`, so use `-Duser.home=/tmp/fakehome` instead.

## Drive the GUI

There is no xdotool on this machine; drive the app in-process instead:
write a small driver class that calls `com.quillapiclient.Main.main`, waits
for the frame via `Frame.getFrames()`, finds the `JTree`, and drives it with
`tree.expandPath(...)` / `tree.setSelectionRow(...)` on the EDT — the same
code paths BasicTreeUI routes real clicks through. Capture rendered frames
with `frame.printAll(Graphics2D)` into a PNG (works regardless of
compositor). Compile the driver against the fat jar:

```bash
javac -cp target/quillclient-1.0-SNAPSHOT.jar Driver.java
java -cp target/quillclient-1.0-SNAPSHOT.jar:. -Dquill.db.path=/tmp/v.db Driver
```

## Useful checks

- Read RSS from `/proc/self/status` (`VmRSS`) inside the driver for memory
  observations; used heap = `Runtime` total - free after a couple of
  `System.gc()` calls.
- To seed a large collection, generate a Postman v2.1 JSON with Jackson's
  `JsonGenerator` (see `src/test/.../CollectionLoadPerformanceTest.java` for
  the exact shape the POJOs parse) and import it with
  `CollectionDao.importCollectionFile`.
- Flows worth driving: startup load of collections, expanding the tree,
  selecting a request (populates the request panel), collapse + re-expand
  (must not duplicate nodes).
