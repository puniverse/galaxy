/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.common.spring;

import org.springframework.beans.factory.FactoryBean;

/**
 *
 * @author pron
 */
public class NullBean implements FactoryBean<Void> {

    @Override
    public Void getObject() throws Exception {
        return null;
    }

    @Override
    public Class<? extends Void> getObjectType() {
        return null;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}