package com.quillapiclient.controller;

/**
 * User object for every meaningful node in the collection tree.
 * Kind distinguishes collection roots, folders, and requests so callers
 * switch on a real type instead of free-form strings.
 */
public class TreeNodeData {

    public enum Kind {
        COLLECTION,
        FOLDER,
        REQUEST;

        /** Maps a DB {@code items.item_type} value onto a tree kind. */
        public static Kind fromDbItemType(String itemType) {
            if ("request".equals(itemType)) {
                return REQUEST;
            }
            // folders, and any unknown row type treated as expandable containers
            return FOLDER;
        }

        public String displayLabel() {
            return switch (this) {
                case COLLECTION -> "collection";
                case FOLDER -> "folder";
                case REQUEST -> "request";
            };
        }

        public boolean isContainer() {
            return this == COLLECTION || this == FOLDER;
        }

        /** Context-menu rename is offered for folders and requests only. */
        public boolean isContextRenamable() {
            return this == FOLDER || this == REQUEST;
        }
    }

    /** COLLECTION → collection id; FOLDER/REQUEST → item id. */
    public final Kind kind;
    public final int id;
    public final String name;
    /** Non-null only for {@link Kind#REQUEST}. */
    public final String method;

    private TreeNodeData(Kind kind, int id, String name, String method) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.method = method;
    }

    public static TreeNodeData collection(int collectionId, String name) {
        return new TreeNodeData(Kind.COLLECTION, collectionId, name, null);
    }

    public static TreeNodeData folder(int itemId, String name) {
        return new TreeNodeData(Kind.FOLDER, itemId, name, null);
    }

    public static TreeNodeData request(int itemId, String name, String method) {
        return new TreeNodeData(Kind.REQUEST, itemId, name, method);
    }

    public TreeNodeData withName(String newName) {
        return new TreeNodeData(kind, id, newName, method);
    }

    public TreeNodeData withMethod(String newMethod) {
        return new TreeNodeData(kind, id, name, newMethod);
    }

    /**
     * Plain name only. Method tags are painted by
     * {@link com.quillapiclient.components.MethodTreeCellRenderer} from
     * {@link #method}, not encoded into this string.
     */
    @Override
    public String toString() {
        return name;
    }
}
