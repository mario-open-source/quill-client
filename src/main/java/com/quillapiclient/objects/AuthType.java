package com.quillapiclient.objects;

/**
 * Type-safe auth method constants, replacing string comparisons throughout the codebase.
 *
 * <p>Each constant carries a {@link #getDisplayName() display name} that matches the
 * combo-box labels in {@code AuthPanel}, and a {@link #getDbKey() persistence key}
 * that is stable for database storage.</p>
 */
public enum AuthType {

    NONE("No auth", "noauth"),
    BASIC("Basic auth", "basic"),
    BEARER("Bearer token", "bearer"),
    JWT_BEARER("Jwt bearer", "jwtbearer");

    private final String displayName;
    private final String dbKey;

    AuthType(String displayName, String dbKey) {
        this.displayName = displayName;
        this.dbKey = dbKey;
    }

    /** The label shown in the auth-type combo box. */
    public String getDisplayName() {
        return displayName;
    }

    /** Stable key used when persisting to the database. */
    public String getDbKey() {
        return dbKey;
    }

    /**
     * Looks up an {@code AuthType} from its display name.
     *
     * @return the matching enum constant, or {@link #NONE} if not recognised
     */
    public static AuthType fromDisplayName(String displayName) {
        if (displayName == null) return NONE;
        for (AuthType t : values()) {
            if (t.displayName.equals(displayName)) return t;
        }
        return NONE;
    }

    /**
     * Looks up an {@code AuthType} from a database key.
     *
     * @return the matching enum constant, or {@link #NONE} if not recognised
     */
    public static AuthType fromDbKey(String dbKey) {
        if (dbKey == null) return NONE;
        String lowered = dbKey.toLowerCase();
        // Also accept the legacy display names stored in older DB rows
        for (AuthType t : values()) {
            if (t.dbKey.equalsIgnoreCase(dbKey)) return t;
            if (t.displayName.equalsIgnoreCase(dbKey)) return t;
        }
        return NONE;
    }

    /** Returns all display names in the combo-box order. */
    public static String[] displayNames() {
        AuthType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }
}
