/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.proxy.ServicesProxyServlet;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ServicesProxyServletTest {

    private ProxyManagerService pms;
    private ServicesDefinition sd;

    private ServicesProxyServlet servlet;

    @Before
    public void setUp() throws Exception {
        pms = new ProxyManagerService();
        sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        servlet = new ServicesProxyServlet(pms, sd);
    }

    @Test
    public void testNominalReplacements() throws Exception {

        Service kafkaManagerService = sd.getService("kafka-manager");

        String toReplace  = "\n <a href='/toto.txt'>\na/a>";
        String result = servlet.performReplacements(kafkaManagerService, "", "test/test", toReplace );
        assertEquals("\n" +
                " <a href='/test/test/toto.txt'>\n" +
                "a/a>", result);
    }

    @Test
    public void testMesosSpecificReplacements() throws Exception {

        Service mesosMasterService = sd.getService("mesos-master");

        String toReplace  = "return '//' + leader_info.hostname + ':' + leader_info.port;";
        String result = servlet.performReplacements(mesosMasterService, "", "test/test", toReplace );
        assertEquals("return '/test/test';", result);

        toReplace = "    // time we are retrieving state), fallback to the current master.\n" +
                "    return '';";
        result = servlet.performReplacements(mesosMasterService, "controllers.js", "test/test", toReplace );
        assertEquals("    // time we are retrieving state), fallback to the current master.\n" +
                "    return '/test/test';", result);
    }

    @Test
    public void testZeppelinReplacements() throws Exception {

        Service zeppelinService = sd.getService("zeppelin");

        String toReplace = "function(e, t, n) {\n" +
                "    \"use strict\";\n" +
                "    function r() {\n" +
                "        this.getPort = function() {\n" +
                "            var e = Number(location.port);\n" +
                "            return e || (e = 80,\n" +
                "            \"https:\" === location.protocol && (e = 443)),\n" +
                "            9e3 === e && (e = 8080),\n" +
                "            e\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getWebsocketUrl = function() {\n" +
                "            var t = \"https:\" === location.protocol ? \"wss:\" : \"ws:\";\n" +
                "            return t + \"//\" + location.hostname + \":\" + this.getPort() + e(location.pathname) + \"/ws\"\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getBase = function() {\n" +
                "            return location.protocol + \"//\" + location.hostname + \":\" + this.getPort() + location.pathname\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getRestApiBase = function() {\n" +
                "            return e(this.getBase()) + \"/api\"\n" +
                "        }\n" +
                "        ;\n" +
                "        var e = function(e) {\n" +
                "            return e.replace(/\\/$/, \"\")\n" +
                "        }\n" +
                "    }\n" +
                "    angular.module(\"zeppelinWebApp\").service(\"baseUrlSrv\", r)\n" +
                "}";

        String result = servlet.performReplacements(zeppelinService, "controllers.js", "test/test", toReplace );

        assertEquals("function(e, t, n) {\n" +
                "    \"use strict\";\n" +
                "    function r() {\n" +
                "        this.getPort = function() {\n" +
                "            var e = Number(location.port);\n" +
                "            return e || (e = 80,\n" +
                "            \"https:\" === location.protocol && (e = 443)),\n" +
                "            9e3 === e && (e = 8080),\n" +
                "            e\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getWebsocketUrl = function() {\n" +
                "            var t = \"https:\" === location.protocol ? \"wss:\" : \"ws:\";\n" +
                "            return t + \"//\" + location.hostname + \":\" + this.getPort() + \"/ws\" + e(location.pathname) + \"/ws\"\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getBase = function() {\n" +
                "            return location.protocol + \"//\" + location.hostname + \":\" + this.getPort() + location.pathname\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getRestApiBase = function() {\n" +
                "            return e(this.getBase()) + \"/api\"\n" +
                "        }\n" +
                "        ;\n" +
                "        var e = function(e) {\n" +
                "            return e.replace(/\\/$/, \"\")\n" +
                "        }\n" +
                "    }\n" +
                "    angular.module(\"zeppelinWebApp\").service(\"baseUrlSrv\", r)\n" +
                "}", result);
    }
}
