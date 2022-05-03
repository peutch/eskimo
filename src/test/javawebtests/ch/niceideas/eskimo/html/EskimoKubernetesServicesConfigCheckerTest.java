/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.html;

import ch.niceideas.eskimo.controlers.ServicesController;
import ch.niceideas.eskimo.services.ServicesDefinition;
import com.gargoylesoftware.htmlunit.ScriptException;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoKubernetesServicesConfigCheckerTest extends AbstractWebTest {

    private static final Logger logger = Logger.getLogger(EskimoKubernetesServicesConfigCheckerTest.class);

    @BeforeEach
    public void setUp() throws Exception {

        loadScript(page, "eskimoNodesConfigurationChecker.js");
        loadScript(page, "eskimoKubernetesServicesConfigChecker.js");

        ServicesController sc = new ServicesController();

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();

        sc.setServicesDefinition(sd);

        String servicesDependencies = sc.getServicesDependencies();
        String kubernetesServiceConfig = sc.getKubernetesServices();

        js("var SERVICES_DEPENDENCIES_WRAPPER = " + servicesDependencies + ";");

        js("var KUBERNETES_SERVICES_CONFIG = " + kubernetesServiceConfig + ";");

        js("function callCheckKubernetesSetup(nodesConfig, kubernetesConfig) {\n" +
                "   return doCheckKubernetesSetup(nodesConfig, kubernetesConfig, SERVICES_DEPENDENCIES_WRAPPER.servicesDependencies, KUBERNETES_SERVICES_CONFIG.kubernetesServicesConfigurations);\n" +
                "}");
    }


    @Test
    public void testCheckKubernetesSetupOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("kube-master", "1");
            put("kube-slave", "1");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});

        JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("elasticsearch_install", "on");
            put("cerebro_install", "on");
            put("kibana_install", "on");
            put("grafana_install", "on");
        }});

        Exception error = null;
        try {
            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        } catch (Exception e) {
            error = e;
        }
        assertNull(error);
    }


    @Test
    public void testOneCerebroButNoES() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("kubernetes", "1");
                put("ntp1", "on");
                put("prometheus1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("cerebro_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service cerebro expects a installaton of  elasticsearch. But it's not going to be installed"));
    }

    @Test
    public void testSparkButNoKube() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("spark-executor_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service spark-executor expects 1 kube-master instance(s). But only 0 has been found !"));
    }

    @Test
    public void testZeppelinButNoZookeeper() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
                put("prometheus1", "on");
                put("kube-master1", "on");
                put("kube-slave1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("elasticsearch_install", "on");
                put("zeppelin_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service zeppelin expects 1 zookeeper instance(s). But only 0 has been found !"));
    }

    @Test
    public void testZeppelinButNoElasticsearch() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
                put("kube-master", "1");
                put("prometheus1", "on");
                put("zookeeper1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("zeppelin_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service zeppelin expects a installaton of  elasticsearch. But it's not going to be installed"));
    }
}
