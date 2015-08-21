package co.paralleluniverse.galaxy.netty;

import org.jboss.netty.util.ThreadNameDeterminer;

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
    /**
     * Copy of {@link org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory#DEFAULT_BOSS_COUNT}
     */
    public static final int DEFAULT_BOSS_COUNT = 1;

    public static final ThreadNameDeterminer KEEP_UNCHANGED_DETERMINER = new ThreadNameDeterminer() {
        @Override
        public String determineThreadName(String currentThreadName, String proposedThreadName) throws Exception {
            return currentThreadName;
        }
    };

    public static int getWorkerCount(ThreadPoolExecutor workerExecutor) {
        return Math.min(workerExecutor.getMaximumPoolSize(), DEFAULT_IO_THREADS);
    }
}
