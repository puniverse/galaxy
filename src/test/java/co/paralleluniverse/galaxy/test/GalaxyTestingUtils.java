/*
 * Galaxy
 * Copyright (C) 2012-2014 Parallel Universe Software Co.
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
package co.paralleluniverse.galaxy.test;

import co.paralleluniverse.galaxy.Server;

import java.net.URL;

public class GalaxyTestingUtils {
    static final boolean ci = (isEnvTrue("CI") || isEnvTrue("CONTINUOUS_INTEGRATION") || isEnvTrue("TRAVIS"));

    public static boolean isCI() {
        return ci;
    }

    public static URL pathToResource(final String name) {
        return ClassLoader.getSystemClassLoader().getResource(name);
    }

    public static Runnable startGlxServer(final String serverConfig, final String serverProps) {
        return new Runnable() {
            @Override
            public void run() {
                Server.start(pathToResource(serverConfig), pathToResource(serverProps));
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ex) {
                    System.out.println("Interrupted.....");
                }
            }
        };
    }

    private static boolean isEnvTrue(String envVar) {
        final String ev = System.getenv(envVar);
        if (ev == null)
            return false;
        try {
            return Boolean.parseBoolean(ev);
        } catch (Exception e) {
            return false;
        }
    }
}
