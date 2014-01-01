/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.galaxy;

/**
 *
 * @author pron
 */
public interface Cache {
    /**
     * Sets a listener listening for local cache events on the given item.
     *
     * @param id       The item's ID.
     * @param listener The listener.
     */
    void setListener(long id, CacheListener listener);

    /**
     * Sets a listener listening for local cache events on the given item if absent.
     *
     * @param id       The item's ID.
     * @param listener The listener.
     * @return The given listener if it was set or the existing one otherwise.
     */
    CacheListener setListenerIfAbsent(long id, CacheListener listener);

    /**
     * @param id The item's ID.
     * @return The cacheListener of this line
     */
    CacheListener getListener(long id);
}
