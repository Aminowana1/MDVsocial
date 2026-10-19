package com.mdvcraft.mdvsocial;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite upkeep shared by player-data.db and mail-data.db. Both run in WAL mode,
 * so recent writes live in the -wal file until a checkpoint copies them into the
 * .db: copying only the .db of a running server can miss weeks of changes.
 */
final class SqliteMaintenance {

    private SqliteMaintenance() {
    }

    /** Copies the -wal into the .db and empties it, so the .db alone is complete. */
    static void checkpoint(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    /**
     * Writes a consistent, self-contained copy of the database (no -wal needed) to
     * {@code target}, which must not exist yet. Safe while the server is running.
     */
    static void backup(Connection connection, File target) throws SQLException, IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("No se pudo crear " + parent);
        if (target.exists())
            throw new IOException(target.getName() + " ya existe");
        try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, target.getAbsolutePath());
            statement.execute();
        }
    }
}
