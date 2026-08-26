package com.structureguard.database;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.database.model.StructureInfo;

import java.sql.*;
import java.util.*;

public class StructureDatabase {
    private final StructureGuardPlugin plugin;
    private Connection connection;
    private final Object dbLock = new Object();

    public StructureDatabase(StructureGuardPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        try {
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/structures.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS structures");
                stmt.executeUpdate("DROP TABLE IF EXISTS scanned_chunks");
                stmt.executeUpdate(
                    "CREATE TABLE structures (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "world TEXT NOT NULL," +
                    "structure_type TEXT NOT NULL," +
                    "min_x INTEGER NOT NULL," +
                    "min_z INTEGER NOT NULL," +
                    "max_x INTEGER NOT NULL," +
                    "max_z INTEGER NOT NULL," +
                    "min_y INTEGER NOT NULL," +
                    "max_y INTEGER NOT NULL," +
                    "chunk_x INTEGER NOT NULL," +
                    "chunk_z INTEGER NOT NULL," +
                    "has_region INTEGER DEFAULT 0," +
                    "region_id TEXT," +
                    "UNIQUE(world, structure_type, chunk_x, chunk_z))"
                );
                stmt.executeUpdate(
                    "CREATE TABLE scanned_chunks (" +
                    "world TEXT NOT NULL," +
                    "chunk_x INTEGER NOT NULL," +
                    "chunk_z INTEGER NOT NULL," +
                    "scanned_at INTEGER DEFAULT (strftime('%s','now'))," +
                    "PRIMARY KEY(world, chunk_x, chunk_z))"
                );
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_type ON structures(structure_type)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_world ON structures(world)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_region ON structures(region_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chunk ON structures(chunk_x, chunk_z)");
            }
            plugin.getLogger().info("Structure database initialized (1.21 BB schema)");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    public boolean addStructure(String world, String structureType, int minX, int minZ, int maxX, int maxZ, int minY, int maxY, int chunkX, int chunkZ) {
        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO structures (world, structure_type, min_x, min_z, max_x, max_z, min_y, max_y, chunk_x, chunk_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setString(1, world);
                stmt.setString(2, structureType);
                stmt.setInt(3, minX);
                stmt.setInt(4, minZ);
                stmt.setInt(5, maxX);
                stmt.setInt(6, maxZ);
                stmt.setInt(7, minY);
                stmt.setInt(8, maxY);
                stmt.setInt(9, chunkX);
                stmt.setInt(10, chunkZ);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to add structure: " + e.getMessage());
                return false;
            }
        }
    }

    public boolean updateBounds(String world, String structureType, int chunkX, int chunkZ, int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE structures SET min_x=?, min_z=?, max_x=?, max_z=?, min_y=?, max_y=? WHERE world=? AND structure_type=? AND chunk_x=? AND chunk_z=?"
            )) {
                stmt.setInt(1, minX);
                stmt.setInt(2, minZ);
                stmt.setInt(3, maxX);
                stmt.setInt(4, maxZ);
                stmt.setInt(5, minY);
                stmt.setInt(6, maxY);
                stmt.setString(7, world);
                stmt.setString(8, structureType);
                stmt.setInt(9, chunkX);
                stmt.setInt(10, chunkZ);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update bounds: " + e.getMessage());
                return false;
            }
        }
    }

    public StructureInfo getStructureByChunk(String world, String structureType, int chunkX, int chunkZ) {
        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM structures WHERE world=? AND structure_type=? AND chunk_x=? AND chunk_z=? LIMIT 1"
            )) {
                stmt.setString(1, world);
                stmt.setString(2, structureType);
                stmt.setInt(3, chunkX);
                stmt.setInt(4, chunkZ);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return mapInfo(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get structure by chunk: " + e.getMessage());
            }
            return null;
        }
    }

    public int addStructuresBatch(String world, List<StructureInfo> structures) {
        if (structures == null || structures.isEmpty()) return 0;
        synchronized (dbLock) {
            int inserted = 0;
            try {
                boolean wasAutoCommit = connection.getAutoCommit();
                if (wasAutoCommit) connection.setAutoCommit(false);
                try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO structures (world, structure_type, min_x, min_z, max_x, max_z, min_y, max_y, chunk_x, chunk_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    for (StructureInfo s : structures) {
                        stmt.setString(1, world);
                        stmt.setString(2, s.type);
                        stmt.setInt(3, s.minX);
                        stmt.setInt(4, s.minZ);
                        stmt.setInt(5, s.maxX);
                        stmt.setInt(6, s.maxZ);
                        stmt.setInt(7, s.minY);
                        stmt.setInt(8, s.maxY);
                        stmt.setInt(9, s.chunkX);
                        stmt.setInt(10, s.chunkZ);
                        stmt.addBatch();
                    }
                    int[] results = stmt.executeBatch();
                    for (int r : results) if (r > 0) inserted++;
                    connection.commit();
                } finally {
                    if (wasAutoCommit) connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed batch insert: " + e.getMessage());
                try { if (!connection.getAutoCommit()) { connection.rollback(); connection.setAutoCommit(true); } } catch (SQLException e2) {}
            }
            return inserted;
        }
    }

    public boolean isChunkScanned(String world, int chunkX, int chunkZ) {
        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM scanned_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
            )) {
                stmt.setString(1, world);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);
                try (ResultSet rs = stmt.executeQuery()) { return rs.next(); }
            } catch (SQLException e) { return false; }
        }
    }

    public Set<Long> getScannedChunks(String world) {
        Set<Long> scanned = new HashSet<>();
        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT chunk_x, chunk_z FROM scanned_chunks WHERE world = ?"
            )) {
                stmt.setString(1, world);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int x = rs.getInt("chunk_x");
                        int z = rs.getInt("chunk_z");
                        long packed = ((long) x & 0xFFFFFFFFL) | (((long) z) << 32);
                        scanned.add(packed);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get scanned chunks: " + e.getMessage());
            }
        }
        return scanned;
    }

    public void markChunksScanned(String world, List<int[]> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        synchronized (dbLock) {
            try {
                boolean wasAutoCommit = connection.getAutoCommit();
                if (wasAutoCommit) connection.setAutoCommit(false);
                try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO scanned_chunks (world, chunk_x, chunk_z) VALUES (?, ?, ?)"
                )) {
                    for (int[] chunk : chunks) {
                        stmt.setString(1, world);
                        stmt.setInt(2, chunk[0]);
                        stmt.setInt(3, chunk[1]);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    connection.commit();
                } finally { if (wasAutoCommit) connection.setAutoCommit(true); }
            } catch (SQLException e) {
                plugin.getConfigManager().debug("Failed to mark chunks scanned: " + e.getMessage());
                try { if (!connection.getAutoCommit()) { connection.rollback(); connection.setAutoCommit(true); } } catch (SQLException e2) {}
            }
        }
    }

    public int clearScannedChunks(String world) {
        try {
            PreparedStatement stmt = connection.prepareStatement("DELETE FROM scanned_chunks WHERE world = ?");
            stmt.setString(1, world);
            return stmt.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("Failed to clear scanned chunks: " + e.getMessage()); return 0; }
    }

    public int clearWorld(String world) {
        try {
            PreparedStatement stmt = connection.prepareStatement("DELETE FROM structures WHERE world = ?");
            stmt.setString(1, world);
            return stmt.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("Failed to clear world structures: " + e.getMessage()); return 0; }
    }

    public int getScannedChunkCount(String world) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM scanned_chunks WHERE world = ?");
            stmt.setString(1, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {}
        return 0;
    }

    public void setRegionId(String world, String structureType, int chunkX, int chunkZ, String regionId) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "UPDATE structures SET has_region = 1, region_id = ? WHERE world = ? AND structure_type = ? AND chunk_x = ? AND chunk_z = ?"
            );
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.setString(3, structureType);
            stmt.setInt(4, chunkX);
            stmt.setInt(5, chunkZ);
            stmt.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("Failed to update region: " + e.getMessage()); }
    }

    public int clearRegions(String pattern) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "UPDATE structures SET has_region = 0, region_id = NULL WHERE structure_type LIKE ?"
            );
            stmt.setString(1, "%" + pattern + "%");
            return stmt.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("Failed to clear regions: " + e.getMessage()); return 0; }
    }

    private StructureInfo mapInfo(ResultSet rs) throws SQLException {
        return new StructureInfo(
            rs.getString("world"),
            rs.getString("structure_type"),
            rs.getInt("min_x"), rs.getInt("min_z"),
            rs.getInt("max_x"), rs.getInt("max_z"),
            rs.getInt("min_y"), rs.getInt("max_y"),
            rs.getInt("chunk_x"), rs.getInt("chunk_z"),
            rs.getInt("has_region") == 1,
            rs.getString("region_id")
        );
    }

    public List<StructureInfo> getStructures(String pattern) {
        List<StructureInfo> results = new ArrayList<>();
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM structures WHERE structure_type LIKE ? ORDER BY structure_type, chunk_x, chunk_z"
            );
            stmt.setString(1, "%" + pattern + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapInfo(rs));
        } catch (SQLException e) { plugin.getLogger().warning("Failed to query structures: " + e.getMessage()); }
        return results;
    }

    public List<StructureInfo> getUnprotectedStructures(String pattern) {
        List<StructureInfo> results = new ArrayList<>();
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM structures WHERE structure_type LIKE ? AND has_region = 0 ORDER BY structure_type, chunk_x, chunk_z"
            );
            stmt.setString(1, "%" + pattern + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapInfo(rs));
        } catch (SQLException e) { plugin.getLogger().warning("Failed to query unprotected structures: " + e.getMessage()); }
        return results;
    }

    public List<StructureInfo> getProtectedStructures(String pattern) {
        List<StructureInfo> results = new ArrayList<>();
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM structures WHERE structure_type LIKE ? AND has_region = 1 ORDER BY structure_type, chunk_x, chunk_z"
            );
            stmt.setString(1, "%" + pattern + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapInfo(rs));
        } catch (SQLException e) { plugin.getLogger().warning("Failed to query protected structures: " + e.getMessage()); }
        return results;
    }

    public StructureInfo getNearestStructure(String world, int x, int z, int maxRadius) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT *, (((min_x+max_x)/2 - ?) * ((min_x+max_x)/2 - ?) + ((min_z+max_z)/2 - ?) * ((min_z+max_z)/2 - ?)) as dist_sq FROM structures WHERE world = ? ORDER BY dist_sq LIMIT 1"
            );
            stmt.setInt(1, x); stmt.setInt(2, x); stmt.setInt(3, z); stmt.setInt(4, z); stmt.setString(5, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double dist = Math.sqrt(rs.getDouble("dist_sq"));
                if (dist <= maxRadius) return mapInfo(rs);
            }
        } catch (SQLException e) { plugin.getLogger().warning("Failed to find nearest structure: " + e.getMessage()); }
        return null;
    }

    public Set<String> getStructureTypes() {
        Set<String> types = new TreeSet<>();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT structure_type FROM structures ORDER BY structure_type");
            while (rs.next()) types.add(rs.getString("structure_type"));
        } catch (SQLException e) { plugin.getLogger().warning("Failed to get structure types: " + e.getMessage()); }
        return types;
    }

    public List<StructureInfo> getStructuresOfType(String world, String structureType) {
        List<StructureInfo> results = new ArrayList<>();
        try {
            PreparedStatement stmt;
            if (structureType.contains("*") || structureType.contains("%")) {
                String sqlPattern = structureType.replace("*", "%");
                stmt = connection.prepareStatement("SELECT * FROM structures WHERE world = ? AND structure_type LIKE ?");
                stmt.setString(1, world); stmt.setString(2, sqlPattern);
            } else {
                stmt = connection.prepareStatement("SELECT * FROM structures WHERE world = ? AND structure_type = ?");
                stmt.setString(1, world); stmt.setString(2, structureType);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapInfo(rs));
        } catch (SQLException e) { plugin.getLogger().warning("Failed to get structures of type: " + e.getMessage()); }
        return results;
    }

    public int getCount(String pattern) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM structures WHERE structure_type LIKE ?");
            stmt.setString(1, "%" + pattern + "%");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { plugin.getLogger().warning("Failed to count structures: " + e.getMessage()); }
        return 0;
    }

    public int getTotalCount() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM structures");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { plugin.getLogger().warning("Failed to count total structures: " + e.getMessage()); }
        return 0;
    }

    public int getProtectedCount() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM structures WHERE has_region = 1");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { plugin.getLogger().warning("Failed to count protected structures: " + e.getMessage()); }
        return 0;
    }

    public boolean hasStructure(String world, String structureType, int chunkX, int chunkZ) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM structures WHERE world = ? AND structure_type = ? AND chunk_x = ? AND chunk_z = ?"
            );
            stmt.setString(1, world); stmt.setString(2, structureType); stmt.setInt(3, chunkX); stmt.setInt(4, chunkZ);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    public boolean isStructureProtected(String world, String structureType, int chunkX, int chunkZ) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT has_region FROM structures WHERE world = ? AND structure_type = ? AND chunk_x = ? AND chunk_z = ?"
            );
            stmt.setString(1, world); stmt.setString(2, structureType); stmt.setInt(3, chunkX); stmt.setInt(4, chunkZ);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("has_region") == 1;
        } catch (SQLException e) {}
        return false;
    }

    public void reset() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DELETE FROM structures");
            stmt.executeUpdate("DELETE FROM scanned_chunks");
            plugin.getLogger().info("Database reset");
        } catch (SQLException e) { plugin.getLogger().warning("Failed to reset database: " + e.getMessage()); }
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException e) {}
    }
}
