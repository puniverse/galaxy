/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.galaxy.core;

/**
 * A marker interface designating a server comm. Simply used for the sake of Spring auto-wiring, as we don't want it to 
 * autowire the peer comm in place of the server comm.
 * @author pron
 */
public interface ServerComm extends Comm {
    
}
