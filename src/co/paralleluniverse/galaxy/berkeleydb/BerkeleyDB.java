/*
 * Galaxy
 * Copyright (C) 2012 Parallel Universe Software Co.
 * 
 * This file is part of Galaxy.
 *
 * Galaxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * Galaxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.
 */
package co.paralleluniverse.galaxy.berkeleydb;

import static co.paralleluniverse.common.logging.LoggingUtils.hex;
import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.galaxy.server.MainMemoryDB;
import co.paralleluniverse.galaxy.server.MainMemoryEntry;
import com.google.common.base.Throwables;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DiskOrderedCursor;
import com.sleepycat.je.DiskOrderedCursorConfig;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import java.beans.ConstructorProperties;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 *
 * @author pron
 */
public class BerkeleyDB extends Component implements MainMemoryDB {
    // Note: class must be public for Spring's auto generated javax.management.modelmbean.RequiredModelMBean to expose @ManagedAttribute

    private static final Logger LOG = LoggerFactory.getLogger(BerkeleyDB.class);
    private final Environment env;
    private Database ownerDirectory;
    private SecondaryDatabase ownerIndex;
    private Database mainStore;
    private final TupleBinding<MainMemoryEntry> entryBinding;
    private static final DatabaseEntry SERVER = new DatabaseEntry(Shorts.toByteArray((short) 0));
    private final String envHome;
    private boolean truncate = false;
    private Durability.SyncPolicy durability = Durability.SyncPolicy.WRITE_NO_SYNC;

    @ConstructorProperties({"name", "envHome"})
    public BerkeleyDB(String name, String envHome) {
        super(name);
        this.envHome = envHome;
        final EnvironmentConfig envConfig = new EnvironmentConfig().setAllowCreate(true).setTransactional(true);
        envConfig.setDurability(new Durability(durability, Durability.SyncPolicy.SYNC, Durability.ReplicaAckPolicy.SIMPLE_MAJORITY));
        this.env = new Environment(new File(envHome), envConfig);

        this.entryBinding = new MainMemoryTupleBinding();
    }

    public void setTruncate(boolean truncate) {
        assertDuringInitialization();
        this.truncate = truncate;
    }

    @ManagedAttribute
    public boolean isTruncate() {
        return truncate;
    }

    @ManagedAttribute(currencyTimeLimit = -1, description = "The BDB environment directory")
    public String getEnvHome() {
        return envHome;
    }

    @Override
    public void init() throws Exception {
        super.init();
        if (truncate)
            truncate();

        LOG.info("Opening database, home: {}", env.getHome());
        // Open the database. Create it if it does not already exist.
        this.ownerDirectory = env.openDatabase(null, "ownerDirecotry",
                new DatabaseConfig().setAllowCreate(true).setTransactional(true));

        this.ownerIndex = env.openSecondaryDatabase(null, "ownerIndex", ownerDirectory,
                ((SecondaryConfig) (new SecondaryConfig().setAllowCreate(true).setSortedDuplicates(true).setTransactional(true))).setAllowPopulate(true).setKeyCreator(new OwnerKeyCreator()));

        PreloadConfig ownerDirectoryPreloadConfig = new PreloadConfig();
        this.ownerDirectory.preload(ownerDirectoryPreloadConfig);

        this.mainStore = env.openDatabase(null, "mainStore",
                new DatabaseConfig().setAllowCreate(true).setTransactional(true));

        if (!truncate)
            resetOwners();
    }

    public void truncate() {
        LOG.info("Truncating database, home: {}", env.getHome());
        Transaction txn = env.beginTransaction(null, TransactionConfig.DEFAULT);
        try {
            env.truncateDatabase(txn, "ownerDirecotry", false);
            env.truncateDatabase(txn, "ownerIndex", false);
            txn.commit();
            env.truncateDatabase(null, "mainStore", false);
        } catch (Exception e) {
            LOG.error("Exception while truncating database. Aborting.", e);
            txn.abort();
            throw Throwables.propagate(e);
        }
    }

    @Override
    public short casOwner(long id, short oldNode, short newNode) {
        final DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(id));
        final DatabaseEntry value = new DatabaseEntry();

        final Transaction txn = env.beginTransaction(null, null);
        try {
            OperationStatus status;

            value.setData(Shorts.toByteArray(newNode));
            if (oldNode < 0) {
                status = ownerDirectory.putNoOverwrite(txn, key, value);
                if (status == OperationStatus.SUCCESS) {
                    LOG.debug("CAS owner succeeded.");
                    txn.commit();
                    return newNode;
                }
            }

            status = ownerDirectory.get(txn, key, value, LockMode.RMW);
            if (status == OperationStatus.SUCCESS) {
                final short curOldNode = Shorts.fromByteArray(value.getData());
                if (LOG.isDebugEnabled())
                    LOG.debug("CAS owner of {}: current old node: {} wanted old node: {}", new Object[]{hex(id), curOldNode, oldNode});
                if (oldNode != curOldNode) {
                    assert curOldNode >= 0;
                    LOG.debug("CAS owner failed.");
                    txn.commit();
                    return curOldNode;
                }

                LOG.debug("CAS owner succeeded.");
                value.setData(Shorts.toByteArray(newNode));
                ownerDirectory.put(txn, key, value);
                txn.commit();
                return newNode;
            } else if (status == OperationStatus.NOTFOUND) {
                LOG.debug("CAS owner failed.");
                txn.commit();
                return (short) -1;
            }

            LOG.debug("Bad status: {}", status);
            throw new AssertionError();
        } catch (Exception e) {
            LOG.error("Exception during DB operation. Aborting transaction.", e);
            txn.abort();
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void removeOwner(short node) {
        final boolean trace = LOG.isTraceEnabled();
        final Transaction txn = null;
//        final Transaction txn = env.beginTransaction(null, TransactionConfig.DEFAULT);
//        try {
        TLongArrayList lines = new TLongArrayList();

        final DatabaseEntry sKey = new DatabaseEntry(Shorts.toByteArray(node));
        final DatabaseEntry pKey = new DatabaseEntry();
        final DatabaseEntry data = new DatabaseEntry();

        final SecondaryCursor cursor = ownerIndex.openCursor(txn, null);
        try {
            OperationStatus retVal = cursor.getSearchKey(sKey, pKey, data, LockMode.DEFAULT);
            while (retVal == OperationStatus.SUCCESS) {
                final long id = Longs.fromByteArray(pKey.getData());
                if (trace)
                    LOG.trace("Owner of {}: {} -> 0", id, node);

                lines.add(id); // cursor.getPrimaryDatabase().put(null, pKey, SERVER); - causes deadlock
                retVal = cursor.getNextDup(sKey, pKey, data, LockMode.DEFAULT);
            }
        } finally {
            cursor.close();
        }

        byte[] longArray = new byte[8];
        for (TLongIterator it = lines.iterator(); it.hasNext();) {
            toByteArray(it.next(), longArray);
            pKey.setData(longArray);
            ownerDirectory.put(null, pKey, SERVER);
        }
//            txn.commit();
//        } catch (Exception e) {
//            LOG.error("Exception while removing. Aborting.", e);
//            txn.abort();
//            throw Throwables.propagate(e);
//        }
    }

    public void resetOwners() {
        final boolean trace = LOG.isTraceEnabled();

        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry data = new DatabaseEntry();

        final DiskOrderedCursor cursor = ownerDirectory.openCursor(new DiskOrderedCursorConfig().setKeysOnly(true));

        try {
            OperationStatus retVal = cursor.getNext(key, data, null);
            while (retVal == OperationStatus.SUCCESS) {
                if (trace)
                    LOG.trace("Owner of {} -> 0", Longs.fromByteArray(key.getData()));

                ownerDirectory.put(null, key, SERVER);
                retVal = cursor.getNext(key, data, null);
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public Object beginTransaction() {
        return env.beginTransaction(null, TransactionConfig.DEFAULT);
    }

    @Override
    public void commit(Object txn) {
        LOG.debug("commit");
        ((Transaction) txn).commit();
    }

    @Override
    public void abort(Object txn) {
        LOG.debug("abort");
        ((Transaction) txn).abort();
    }

    @Override
    public void write(long id, short owner, long version, byte[] data, Object txn) {
        if (LOG.isDebugEnabled())
            LOG.debug("WRITE " + hex(id) + " ver: " + version + " data: " + (data != null ? "(" + data.length + " bytes)" : "null"));

        final DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(id));
        final DatabaseEntry dbEntry = new DatabaseEntry();
        entryBinding.objectToEntry(new MainMemoryEntry(version, data), dbEntry);

        mainStore.put((Transaction) txn, key, dbEntry);
        // try to write owner, but only if nonexistent (i.e will happen at first put only)
        ownerDirectory.putNoOverwrite((Transaction) txn, key, new DatabaseEntry(Shorts.toByteArray(owner)));
    }

    @Override
    public MainMemoryEntry read(long id) {
        final DatabaseEntry dbEntry = new DatabaseEntry();
        OperationStatus status = mainStore.get(null, new DatabaseEntry(Longs.toByteArray(id)), dbEntry, LockMode.READ_COMMITTED);
        if (status == OperationStatus.SUCCESS) {
            final MainMemoryEntry entry = entryBinding.entryToObject(dbEntry);
            return entry;
        } else
            return null;
    }

    @Override
    public void delete(long id, Object txn) {
        mainStore.delete((Transaction) txn, new DatabaseEntry(Longs.toByteArray(id)));
        ownerDirectory.delete((Transaction) txn, new DatabaseEntry(Longs.toByteArray(id)));
    }

    @Override
    public long getMaxId() {
        final long ownerDirecotryMaxId = getMaxId(ownerDirectory);
        final long mainStoreMaxId = getMaxId(mainStore);
        
        LOG.info("OwnerDirectory max id: {}", ownerDirecotryMaxId);
        LOG.info("MainStore max id: {}", mainStoreMaxId);
        
        return Math.max(ownerDirecotryMaxId, mainStoreMaxId);
    }

    private long getMaxId(Database db) {
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();
        final Cursor cursor = db.openCursor(null, CursorConfig.DEFAULT);
        try {
            final OperationStatus status = cursor.getLast(key, value, null);
            if (status == OperationStatus.SUCCESS)
                return Longs.fromByteArray(key.getData());
            else
                return 0;
        } finally {
            cursor.close();
        }

    }

    @Override
    public void close() {
        ownerIndex.close();
        ownerDirectory.close();
        mainStore.close();
        env.close();
    }

    private static class MainMemoryTupleBinding extends TupleBinding<MainMemoryEntry> {

        @Override
        public void objectToEntry(MainMemoryEntry entry, TupleOutput out) {
            out.writeLong(entry.version);
            //out.writeUnsignedShort(entry.data.length);
            out.writeFast(entry.data);
        }

        @Override
        public MainMemoryEntry entryToObject(TupleInput in) {
            final long version = in.readLong();
            //final int dataLength = in.readUnsignedShort();
            final int dataLength = in.getBufferLength() - in.getBufferOffset();
            final byte[] data = new byte[dataLength];
            in.readFast(data);
            return new MainMemoryEntry(version, data);
        }

    }

    private static class OwnerKeyCreator implements SecondaryKeyCreator {

        @Override
        public boolean createSecondaryKey(SecondaryDatabase secondary, DatabaseEntry key, DatabaseEntry data, DatabaseEntry result) {
            result.setData(data.getData());
            return true;
        }

    }

    @Override
    public void dump(java.io.PrintStream ps) {
        String home = "";
        try {
            home = env.getHome().getCanonicalPath();
        } catch (IOException e) {
        }

        ps.println();
        ps.println("BERKELEYDB " + home);
        ps.println("=====================================");
        ps.println();

        printOwners(ps);
        ps.println();

        printMainStore(ps);
        ps.println();

        printOwnerIndex(ps);
        ps.println();
    }

    public void printOwners(java.io.PrintStream ps) {
        ps.println("OWNERS");
        ps.println("======");
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();
        final Cursor cursor = ownerDirectory.openCursor(null, CursorConfig.DEFAULT);
        try {
            while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                long id = Longs.fromByteArray(key.getData());
                short owner = Shorts.fromByteArray(value.getData());
                ps.println("Id : " + hex(id) + " owner: " + owner + "");
            }
        } finally {
            cursor.close();
        }
    }

    public void printMainStore(java.io.PrintStream ps) {
        ps.println("MAIN STORE");
        ps.println("==========");
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();
        final Cursor cursor = mainStore.openCursor(null, CursorConfig.DEFAULT);
        try {
            while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                long id = Longs.fromByteArray(key.getData());
                final MainMemoryEntry entry = entryBinding.entryToObject(value);
                ps.println("Id : " + hex(id) + " version: " + entry.version + " data: (" + entry.data.length + " bytes).");
            }
        } finally {
            cursor.close();
        }
    }

    public void printOwnerIndex(java.io.PrintStream ps) {
        ps.println("OWNER INDEX");
        ps.println("===========");
        final DatabaseEntry sKey = new DatabaseEntry();
        final DatabaseEntry pKey = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();
        final SecondaryCursor cursor = ownerIndex.openCursor(null, CursorConfig.DEFAULT);
        try {
            while (cursor.getNext(sKey, pKey, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                long id = Longs.fromByteArray(pKey.getData());
                short owner = Shorts.fromByteArray(sKey.getData());
                ps.println("Owner: " + owner + " id : " + hex(id));
            }
        } finally {
            cursor.close();
        }
    }

    public static byte[] toByteArray(long value, byte[] array) {
        array[0] = (byte) (value >> 56);
        array[1] = (byte) (value >> 48);
        array[2] = (byte) (value >> 40);
        array[3] = (byte) (value >> 32);
        array[4] = (byte) (value >> 24);
        array[5] = (byte) (value >> 16);
        array[6] = (byte) (value >> 8);
        array[7] = (byte) value;
        return array;
    }

}
