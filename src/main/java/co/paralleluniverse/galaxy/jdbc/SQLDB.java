/*
 * Galaxy
 * Copyright (c) 2012-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.galaxy.jdbc;

import static co.paralleluniverse.common.logging.LoggingUtils.hex;
import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.galaxy.server.MainMemoryDB;
import co.paralleluniverse.galaxy.server.MainMemoryEntry;
import com.google.common.base.Throwables;
import java.beans.ConstructorProperties;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class SQLDB extends Component implements MainMemoryDB {
    private static final Logger LOG = LoggerFactory.getLogger(SQLDB.class);
    private final DataSource dataSource;
    private String username;
    private String password;
    private String schema = "pugalaxy";
    private String tableName = "memory";
    private String table;
    private String allocationTableName = "allocation";
    private String allocTable;
    private String bigintType;
    private String smallintType;
    private String varbinaryType;
    private int maxItemSize = 1024;
    private boolean useUpdateableCursors = false;
    private Connection conn;
    private PreparedStatement casOwner;
    private PreparedStatement getOwner;
    private PreparedStatement deleteOwner;
    private PreparedStatement insertLine;
    private PreparedStatement setLine;
    private PreparedStatement getLine;
    private PreparedStatement deleteLine;
    private PreparedStatement selectAll;
    private PreparedStatement getMaxId;
    private PreparedStatement addAllocation;
    private PreparedStatement getAllocation;
    private static final Object TRANSACTION = new Object();

    @ConstructorProperties({"name", "dataSource"})
    public SQLDB(String name, DataSource dataSource) {
        super(name);
        this.dataSource = dataSource;
    }

    public void setPassword(String password) {
        assertDuringInitialization();
        this.password = password;
    }

    public void setUsername(String username) {
        assertDuringInitialization();
        this.username = username;
    }

    public void setSchema(String schema) {
        assertDuringInitialization();
        this.schema = schema;
    }

    public void setTableName(String tableName) {
        assertDuringInitialization();
        this.tableName = tableName;
    }

    public void setAllocationTableName(String tableName) {
        assertDuringInitialization();
        this.allocationTableName = tableName;
    }

    public void setMaxItemSize(int maxItemSize) {
        assertDuringInitialization();
        this.maxItemSize = maxItemSize;
    }

    public void setUseUpdateableCursors(boolean useUpdateableCursors) {
        assertDuringInitialization();
        this.useUpdateableCursors = useUpdateableCursors;
    }

    public void setBigintType(String bigintType) {
        assertDuringInitialization();
        this.bigintType = bigintType;
    }

    public void setVarbinaryType(String varbinaryType) {
        assertDuringInitialization();
        this.varbinaryType = varbinaryType;
    }

    public void setSmallintType(String smallintType) {
        assertDuringInitialization();
        this.smallintType = smallintType;
    }

    @Override
    protected void init() throws Exception {
        super.init();

        LOG.info("Connecting to database {}", dataSource);
        if (username != null)
            conn = dataSource.getConnection(username, password);
        else
            conn = dataSource.getConnection();
        LOG.info("Connection successful");

        initDbTypes();

        this.table = schema + "." + tableName;
        this.allocTable = schema + "." + allocationTableName;
        initTable();
        initPreparedStatements();

        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    private void initTable() throws SQLException {
        try {
            try (Statement stmt = conn.createStatement()) {
                String createTable = "CREATE TABLE " + table + " "
                        + "(id " + bigintType + " PRIMARY KEY, "
                        + "owner " + smallintType + " NOT NULL, "
                        + "version " + bigintType + " NOT NULL, "
                        + "data " + createVarbinary(maxItemSize)
                        + ")";
                LOG.debug("Creating table: {}", createTable);
                stmt.executeUpdate(createTable);
                stmt.executeUpdate("CREATE INDEX owner_index ON " + table + "(owner)");

                createTable = "CREATE TABLE " + allocTable + " "
                        + "(id " + bigintType + " PRIMARY KEY, "
                        + "end " + bigintType + " NOT NULL, "
                        + "owner " + smallintType + " NOT NULL "
                        + ")";
                LOG.debug("Creating table: {}", createTable);
                stmt.executeUpdate(createTable);
            }
        } catch (SQLException e) {
            LOG.debug("SQLException caught: {} - {}", e.getClass().getName(), e.getMessage());
        }
    }

    private void initPreparedStatements() throws SQLException {
        getMaxId();
        dump(null);
        insert(0, (short) 0, 0, null, null);
        read(0);
        write(0, (short) 0, 0, null, null);
        delete(0, null);
        removeOwner((short) 0);
        if (useUpdateableCursors)
            casOwnerUpdateableCursor(0, (short) 0, (short) 0);
        else {
            casOwnerUpdate(0, (short) 0, (short) 0);
            getOwner(0);
        }
        allocate((short) 0, 0, 0);
        findAllocation(0);
    }

    private void initDbTypes() throws SQLException {
        if (bigintType == null || smallintType == null || varbinaryType == null) {
            final Map<Integer, String> types = new HashMap<Integer, String>();
            final DatabaseMetaData dmd = conn.getMetaData();
            try (ResultSet rs = dmd.getTypeInfo()) {
                while (rs.next()) {
                    final int jdbcType = rs.getInt("DATA_TYPE");
                    final String typeName = rs.getString("TYPE_NAME");
                    types.put(jdbcType, typeName);
                }
            }

            if (bigintType == null)
                bigintType = types.get(Types.BIGINT);
            if (smallintType == null)
                smallintType = types.get(Types.SMALLINT);
            if (varbinaryType == null)
                varbinaryType = types.get(Types.VARBINARY);

            LOG.debug("BIGINT type is: {}", bigintType);
            LOG.debug("SMALLINT type is: {}", smallintType);
            LOG.debug("VARBINARY type is: {}", varbinaryType);
        }
    }

    private String createVarbinary(int size) {
        if (varbinaryType.contains("()"))
            return varbinaryType.replace("()", "(" + size + ")");
        else
            return varbinaryType + "(" + size + ")";
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Object beginTransaction() {
        return TRANSACTION;
    }

    @Override
    public void commit(Object txn) {
        try {
            LOG.debug("COMMIT");
            assert txn == TRANSACTION;
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void abort(Object txn) {
        try {
            LOG.debug("ROLLBACK");
            assert txn == TRANSACTION;
            conn.rollback();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void write(long id, short owner, long version, byte[] data, Object txn) {
        if (setLine == null) {
            setLine = prepareStatement("UPDATE " + table + " SET version = ?, data = ? WHERE id = ? AND owner = ?");
            return;
        }

        if (LOG.isDebugEnabled())
            LOG.debug("WRITE " + id + " ver: " + version + " data: (" + data.length + " bytes)");

        if (data.length > maxItemSize) {
            LOG.error("Data length is {}, which is bigger than maxItemSize ({})", data.length, maxItemSize);
            throw new RuntimeException("Data too big.");
        }
        try {
            setLine.setLong(3, id);
            setLine.setShort(4, owner);
            setLine.setLong(1, version);
            setLine.setBytes(2, data);
            if (setLine.executeUpdate() < 1) {
                LOG.debug("Setting line {} failed. Inserting.", id);
                insert(id, (short) owner, version, data, txn);
            } else if (txn == null) // insert() commits
                conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public MainMemoryEntry read(long id) {
        if (getLine == null) {
            getLine = prepareStatement("SELECT version, data FROM " + table + "  WHERE id = ?");
            return null;
        }

        try {
            getLine.setLong(1, id);
            try (ResultSet rs = getLine.executeQuery()) {
                rs.next();
                final long version = rs.getLong(1);
                final byte[] data = rs.getBytes(2);
                conn.commit();
                return new MainMemoryEntry(version, data);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public short casOwner(long id, short oldNode, short newNode) {
        if (LOG.isDebugEnabled())
            LOG.debug("CAS owner of {}: {} -> {}", new Object[]{id, oldNode, newNode});

        try {
            if (oldNode < 0) {
                try {
                    insert(id, newNode, -1, null, null);
                    LOG.debug("CAS owner succeeded (insert).");
                    return newNode;
                } catch (SQLException e) {
                }
                LOG.debug("CAS owner failed (insert).");
                return getOwner(id);
            } else {
                if (useUpdateableCursors)
                    return casOwnerUpdateableCursor(id, oldNode, newNode);
                else
                    return casOwnerUpdate(id, oldNode, newNode);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    private short casOwnerUpdateableCursor(long id, short oldNode, short newNode) throws SQLException {
        if (casOwner == null) {
            casOwner = prepareStatement("SELECT owner FROM " + table + " WHERE id = ? FOR UPDATE", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
            return 0;
        }

        final short res;
        casOwner.setLong(1, id);
        try (ResultSet rs = casOwner.executeQuery()) {
            if (rs.next()) {
                final short currentOwner = rs.getShort(1);
                if (currentOwner != oldNode) {
                    LOG.debug("CAS owner failed (UC).");
                    res = currentOwner;
                } else {
                    rs.updateShort(1, newNode);
                    LOG.debug("CAS owner succeeded (UC).");
                    res = newNode;
                }
                conn.commit();
                return res;
            } else {
                LOG.debug("CAS owner failed (UC).");
                return -1;
            }
        }
    }

    private short casOwnerUpdate(long id, short oldNode, short newNode) throws SQLException {
        if (casOwner == null) {
            casOwner = prepareStatement("UPDATE " + table + " SET owner = ? WHERE id = ? AND owner = ?");
            return 0;
        }

        final short res;
        casOwner.setLong(2, id);
        casOwner.setShort(3, oldNode);
        casOwner.setShort(1, newNode);
        int rows = casOwner.executeUpdate();
        if (rows > 0) {
            LOG.debug("CAS owner succeeded.");
            res = newNode;
        } else {
            LOG.debug("CAS owner failed.");
            res = getOwner(id);
        }
        conn.commit();
        return res;
    }

    private short getOwner(long id) throws SQLException {
        if (getOwner == null) {
            getOwner = conn.prepareStatement("SELECT owner FROM " + table + " WHERE id = ?");
            return 0;
        }

        getOwner.setLong(1, id);
        try (ResultSet rs = getOwner.executeQuery()) {
            final short res = rs.next() ? rs.getShort(1) : (short) -1;
            conn.commit();
            return res;
        }
    }

    private void insert(long id, short owner, long version, byte[] data, Object txn) throws SQLException {
        if (insertLine == null) {
            insertLine = prepareStatement("INSERT INTO " + table + " (id, owner, version, data) VALUES (?, ?, ?, ?)");
            return;
        }

        insertLine.setLong(1, id);
        insertLine.setShort(2, owner);
        insertLine.setLong(3, version);
        insertLine.setBytes(4, data);
        insertLine.executeUpdate();

        if (txn == null)
            conn.commit();
    }

    @Override
    public void delete(long id, Object txn) {
        if (deleteLine == null) {
            deleteLine = prepareStatement("DELETE FROM " + table + " WHERE id = ?");
            return;
        }
        try {
            deleteLine.setLong(1, id);
            deleteLine.executeUpdate();
            if (txn == null)
                conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void removeOwner(short node) {
        if (deleteOwner == null) {
            deleteOwner = prepareStatement("UPDATE " + table + " SET owner = 0 WHERE owner = ?");
            return;
        }

        try {
            deleteOwner.setShort(1, node);
            deleteOwner.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void allocate(short owner, long start, int num) {
        if (owner <= 0) {
            addAllocation = prepareStatement("INSERT INTO " + allocTable + " (id, end, owner) VALUES (?, ?, ?)");
            return;
        }

        try {
            addAllocation.setLong(1, start);
            addAllocation.setLong(2, start + num);
            addAllocation.setShort(3, owner);
            addAllocation.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public short findAllocation(long ref) {
        if (ref <= 0) {
            getAllocation = prepareStatement("SELECT owner FROM " + allocTable + " WHERE id <= ? AND end > ?");
            return 0;
        }

        try {
            getAllocation.setLong(1, ref);
            getAllocation.setLong(2, ref);
            try (ResultSet rs = getAllocation.executeQuery()) {
                final short res = rs.next() ? rs.getShort(1) : -1;
                conn.commit();
                return res;
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public long getMaxId() {
        if (getMaxId == null) {
            getMaxId = prepareStatement("SELECT MAX(id) FROM " + allocTable);
            return 0;
        }

        try (ResultSet rs = getMaxId.executeQuery()) {
            final long res = rs.next() ? rs.getLong(1) : 0;
            conn.commit();
            return res;
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void dump(PrintStream ps) {
        if (selectAll == null) {
            selectAll = prepareStatement("SELECT * FROM " + table);
            return;
        }
        try {
            ps.println("MEMORY");
            ps.println("===========");
            try (ResultSet rs = selectAll.executeQuery()) {
                while (rs.next()) {
                    final long id = rs.getLong("id");
                    final short owner = rs.getShort("owner");
                    final long version = rs.getLong("version");
                    final byte[] data = rs.getBytes("data");
                    ps.println("Id : " + hex(id) + " owner: " + owner + " version: " + version + " data: (" + data.length + " bytes).");
                }
                conn.commit();
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    private PreparedStatement prepareStatement(String sql) {
        try {
            assertDuringInitialization();
            return conn.prepareStatement(sql);
        } catch (SQLException e) {
            LOG.error("Error while preparing statement: " + sql, e);
            throw new Error(e);
        }
    }

    private PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
        try {
            assertDuringInitialization();
            return conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
        } catch (SQLException e) {
            LOG.error("Error while preparing statement: " + sql, e);
            throw new Error(e);
        }
    }

}
