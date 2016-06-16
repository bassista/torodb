
package com.torodb.mongodb.core;

import com.eightkdata.mongowp.server.api.Connection;
import com.google.common.base.Preconditions;
import com.torodb.torod.TorodConnection;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 */
@NotThreadSafe
public class MongodConnection implements Connection, AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(MongodConnection.class);

    private final MongodServer server;
    private final TorodConnection torodConnection;
    private final LastErrorManager lastErrorManager;
    private MongodTransaction currentTransaction;
    private boolean closed = false;

    public MongodConnection(MongodServer server) {
        this.server = server;
        this.torodConnection = server.getTorodServer().openConnection();
        this.lastErrorManager = new LastErrorManager();
    }

    public MongodServer getServer() {
        return server;
    }

    public TorodConnection getTorodConnection() {
        return torodConnection;
    }

    public LastErrorManager getLastErrorManager() {
        return lastErrorManager;
    }

    public MongodTransaction openTransaction(boolean readOnly) {
        Preconditions.checkState(!closed, "This connection is closed");
        Preconditions.checkState(currentTransaction == null, "Another transaction is currently under execution. Transaction is " + currentTransaction);
        currentTransaction = new MongodTransaction(this, readOnly);
        return currentTransaction;
    }

    @Nullable
    public MongodTransaction getCurrentTransaction() {
        return currentTransaction;
    }

    public int getConnectionId() {
        return torodConnection.getConnectionId();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (currentTransaction != null) {
                currentTransaction.close();
            }
            assert currentTransaction == null;
            server.onConnectionClose(this);

            torodConnection.close();
        }
    }

    void onTransactionClosed(MongodTransaction transaction) {
        if (currentTransaction == null) {
            LOGGER.debug("Recived an on transaction close notification, but there is no current transaction");
            return ;
        }
        if (currentTransaction != transaction) {
            LOGGER.debug("Recived an on transaction close notification, but the recived transaction is not the same as the current one");
            return ;
        }
        currentTransaction = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (!closed) {
            LOGGER.warn(this.getClass() + " finalized without being closed");
            close();
        }
    }

}
