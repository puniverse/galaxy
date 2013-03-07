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
package co.paralleluniverse.galaxy;

import co.paralleluniverse.common.spring.SpringContainerHelper;
import co.paralleluniverse.galaxy.core.AbstractCluster;
import co.paralleluniverse.galaxy.core.MainMemory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

/**
 * This class runs the Galaxy server.
 * This class can be run standalone, or, if you want to embed the server in a running JVM, by calling the {@link #start(java.lang.String, java.lang.String) start} method.
 */
public class Server {
    /**
     * Starts the galaxy server.
     * @param configFile The path to the xml (spring) configuration file {@code null} null for default.
     */
    public static void start(String configFile) {
        start(configFile, null);
    }

    /**
     * Starts the galaxy server.
     * @param configFile The path to the xml (spring) configuration file {@code null} null for default.
     * @param propertiesFile The name of the properties file containing the grid's properties.
     * You may, of course use Spring's {@code <context:property-placeholder location="classpath:com/foo/bar.properties"/>} but this parameter is helpful when you want to use the same xml configuration
     * with different properties for different instances.
     */
    public static void start(String configFile, String propertiesFile) {
        final ApplicationContext context = SpringContainerHelper.createContext("co.paralleluniverse.galaxy",
                configFile != null ? new FileSystemResource(configFile) : new ClassPathResource("galaxy.xml"),
                propertiesFile != null ? new FileSystemResource(propertiesFile) : null,
                null);
        MainMemory memory = context.getBean("memory", MainMemory.class);
        ((AbstractCluster)memory.getCluster()).goOnline();
    }

    /**
     * Runs the Galaxy server.
     * If command line arguments are given, the first {@code args[0]} is the path xml (spring) configuration file
     * and the second (if it exists) is the path to a properties file (referenced in the config-file).
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        final String configFile = args.length > 0 ? args[0] : null;
        final String propertiesFile = args.length > 1 ? args[1] : null;
        start(configFile, propertiesFile);
        Thread.sleep(Long.MAX_VALUE);
    }
}
