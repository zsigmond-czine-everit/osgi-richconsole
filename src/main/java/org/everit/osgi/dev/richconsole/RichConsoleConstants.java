/**
 * This file is part of Everit - OSGi Rich Console.
 *
 * Everit - OSGi Rich Console is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - OSGi Rich Console is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - OSGi Rich Console.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.richconsole;

public final class RichConsoleConstants {

    public static final String ENV_EOSGI_STOP_AFTER_TESTS = "EOSGI_STOP_AFTER_TESTS";

    public static final String ENV_EOSGI_UPGRADE_SERVICE_PORT = "EOSGI_UPGRADE_SERVICE_PORT";

    public static final String SYSPROP_ENVIRONMENT_ID = "eosgi.environment.id";

    public static final String TCPCOMMAND_DEPLOY_BUNDLE = "deployBundle";

    public static final String TCPCOMMAND_GET_ENVIRONMENT_ID = "getEnvironmentId";

    public static final String TCPCOMMAND_UNINSTALL = "uninstallBundle";

    public static final String TCPRESPONSE_OK = "ok";

    private RichConsoleConstants() {
    }
}