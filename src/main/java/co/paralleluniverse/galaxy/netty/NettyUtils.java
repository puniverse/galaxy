package co.paralleluniverse.galaxy.netty;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Some constants used for configuring netty thread pools.
 *
 * @author s.stupin
 */
public class NettyUtils {
    /**
     * Copy of {@link org.jboss.netty.channel.socket.nio.SelectorUtil#DEFAULT_IO_THREADS}
     */
    public static final int DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    public static int getWorkerCount(ThreadPoolExecutor workerExecutor) {
        return Math.min(workerExecutor.getMaximumPoolSize(), DEFAULT_IO_THREADS);
    }
}
