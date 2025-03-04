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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SystemServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(SystemServiceTest.class);

    private String testRunUUID = UUID.randomUUID().toString();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(createTempStoragePath());
    }

    @Override
    protected SystemService createSystemService() {
        SystemService ss = new SystemService(false) {
            @Override
            protected File createTempFile(String serviceOrFlag, String node, String extension) throws IOException {
                File retFile = new File ("/tmp/" + serviceOrFlag + "-" + testRunUUID + "-" + node + extension);
                retFile.createNewFile();
                return retFile;
            }
        };
        ss.setConfigurationService(configurationService);
        return ss;
    }

    @Override
    protected SetupService createSetupService() {
        return new SetupService() {
            @Override
            public String findLastPackageFile(String prefix, String packageName) {
                return prefix+"_"+packageName+"_dummy_1.dummy";
            }
        };
    }

    public static String createTempStoragePath() throws Exception {
        File dtempFileName = File.createTempFile("test_systemservice_", "config_storage");
        FileUtils.delete (dtempFileName); // delete file to create directory below

        File configStoragePathFile = new File (dtempFileName.getAbsolutePath() + "/");
        configStoragePathFile.mkdirs();
        return configStoragePathFile.getAbsolutePath();
    }

    @Test
    public void testShowJournal() throws Exception {
        systemService.showJournal(servicesDefinition.getService("ntp"), "192.168.10.11");
        assertEquals ("sudo journalctl -u ntp", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testStartService() throws Exception {
        systemService.startService(servicesDefinition.getService("ntp"), "192.168.10.11");
        assertEquals ("sudo systemctl start ntp", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testStopService() throws Exception {
        systemService.stopService(servicesDefinition.getService("ntp"), "192.168.10.11");
        assertEquals ("sudo systemctl stop ntp", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testRestartService() throws Exception {
        systemService.restartService(servicesDefinition.getService("ntp"), "192.168.10.11");
        assertEquals ("sudo systemctl restart ntp", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testCallCommand() throws Exception {

        SystemException exception = assertThrows(SystemException.class,
                () -> systemService.callCommand("dummy", "ntp", "192.168.10.11"));
        assertNotNull(exception);
        assertEquals("Command dummy is unknown for service ntp", exception.getMessage());

        systemService.callCommand("show_log", "ntp", "192.168.10.11");
        assertEquals ("cat /var/log/ntp/ntp.log", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testUpdateStatusWithKubernetesException() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        configurationService.saveNodesConfig(nodesConfig);

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        configurationService.saveKubernetesServicesConfig(kubeServicesConfig);

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String node, String filePath) {
                // just do nothing
            }
        });

        systemService.updateStatus();

        SystemStatusWrapper systemStatus = systemService.getStatus();

        //System.err.println(systemStatus.getFormattedValue());

        SystemStatusWrapper expectedStatusWrapper = new SystemStatusWrapper(expectedFullStatus);
        for (String kubeService : servicesDefinition.listKubernetesServices()) {
            String prevValue = expectedStatusWrapper.getValueForPathAsString(SystemStatusWrapper.SERVICE_PREFIX + kubeService + "_192-168-10-11");
            if (StringUtils.isNotBlank(prevValue) && prevValue.equals("OK")) {
                expectedStatusWrapper.setValueForPath(SystemStatusWrapper.SERVICE_PREFIX + kubeService + "_192-168-10-11", "KO");
            }
        }
        expectedStatusWrapper.removeRootKey(SystemStatusWrapper.SERVICE_PREFIX + "grafana_192-168-10-11");

        //System.out.println(expectedStatusWrapper.getFormattedValue());

        assertTrue(expectedStatusWrapper.getJSONObject().similar(systemStatus.getJSONObject()),
                "Expected : \n" + expectedStatusWrapper.getFormattedValue() + "\n\n but got \n: " + systemStatus.getFormattedValue());
    }

    @Test
    public void testFetchNodeStatus() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        Map<String, String> statusMap = new HashMap<>();

        int nodeNbr = 1;
        String ipAddress = "192.168.10.11";

        Pair<String, String> nodeNumnberAndIpAddress = new Pair<>(""+nodeNbr, ipAddress);

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String node, String filePath){
                // just do nothing
            }
        });


        systemService.fetchNodeStatus (nodesConfig, statusMap, nodeNumnberAndIpAddress, servicesInstallStatus);

        assertEquals(6, statusMap.size());

        assertNull(statusMap.get("service_kafka-manager_192-168-10-11")); // this is moved to Kubernetes
        assertEquals("OK", statusMap.get("node_alive_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_etcd_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_gluster_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_ntp_192-168-10-11"));
    }

    @Test
    public void testUpdateStatus() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        configurationService.saveNodesConfig(nodesConfig);

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        configurationService.saveKubernetesServicesConfig(kubeServicesConfig);

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String node, String filePath) {
                // just do nothing
            }
        });

        systemService.updateStatus();

        SystemStatusWrapper systemStatus = systemService.getStatus();


        JSONObject actual = systemStatus.getJSONObject();
        assertTrue(new JSONObject(expectedFullStatus).similar(actual), actual.toString(2));
    }

    @Test
    public void testFetchNodeStatusWithRestarts() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        Map<String, String> statusMap = new HashMap<>();

        int nodeNbr = 1;
        String ipAddress = "192.168.10.11";

        Pair<String, String> nbrAndPair = new Pair<>(""+nodeNbr, ipAddress);

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public String runSSHScript(String node, String script, boolean throwsException){
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String node, String filePath) {
                // just do nothing
            }
        });

        servicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-11", "restart");
        servicesInstallStatus.setValueForPath("gluster_installed_on_IP_192-168-10-11", "restart");

        systemService.fetchNodeStatus (nodesConfig, statusMap, nbrAndPair, servicesInstallStatus);

        assertEquals(6, statusMap.size());

        assertNull(statusMap.get("service_kafka-manager_192-168-10-11")); // kafka manager is moved to kubernetes
        assertEquals("OK", statusMap.get("node_alive_192-168-10-11"));
        assertEquals("restart", statusMap.get("service_etcd_192-168-10-11"));
        assertEquals("restart", statusMap.get("service_gluster_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_ntp_192-168-10-11"));
    }

    @Test
    public void testCheckServiceDisappearance() throws Exception {

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        prevSystemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");

        systemService.setLastStatusForTest(prevSystemStatus);

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");
        systemStatus.setValueForPath("service_cerebro_192-168-10-11", "NA");
        systemStatus.setValueForPath("service_kibana_192-168-10-11", "KO");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(2, notifications.getKey().intValue());

        assertEquals("{\"type\":\"Error\",\"message\":\"Service cerebro on 192.168.10.11 got into problem\"}", notifications.getValue().get(0).toString());

        assertEquals("{\"type\":\"Error\",\"message\":\"Service kibana on 192.168.10.11 got into problem\"}", notifications.getValue().get(1).toString());
    }

    @Test
    public void testCheckServiceDisappearanceWithRestarts() throws Exception {

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        prevSystemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");

        systemService.setLastStatusForTest(prevSystemStatus);

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");
        systemStatus.setValueForPath("service_cerebro_192-168-10-11", "NA");
        systemStatus.setValueForPath("service_kibana_192-168-10-11", "KO");

        // flag a few services as restart => should not be reported as in issue
        systemStatus.setValueForPath("service_kafka_192-168-10-11", "restart");
        systemStatus.setValueForPath("service_kafka_192-168-10-13", "restart");
        systemStatus.setValueForPath("service_kafka-manager_192-168-10-11", "restart");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(2, notifications.getKey().intValue());

        assertEquals("{\"type\":\"Error\",\"message\":\"Service cerebro on 192.168.10.11 got into problem\"}", notifications.getValue().get(0).toString());

        assertEquals("{\"type\":\"Error\",\"message\":\"Service kibana on 192.168.10.11 got into problem\"}", notifications.getValue().get(1).toString());
    }

    @Test
    public void testCheckServiceDisappearanceNodeDown() throws Exception {

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();

        systemService.setLastStatusForTest(prevSystemStatus);

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("nbr")  && !key.contains("alive"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);
        systemStatus.setValueForPath(SystemStatusWrapper.NODE_ALIVE_FLAG + "192-168-10-13", "KO");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(1, notifications.getKey().intValue());

        assertEquals("{\"type\":\"Error\",\"message\":\"Service Node Alive on 192.168.10.13 got into problem\"}", notifications.getValue().get(0).toString());
    }

    @Test
    public void testHandleStatusChanges() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_etcd_192-168-10-11");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        // etcd is missing
        System.err.println (resultPrevStatus.getFormattedValue());
        assertTrue (new JSONObject(expectedPrevStatusServicesRemoved).similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangesNodeDown() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();

        // remove all status for node 2 (except node down and node nbr)
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("nbr")  && !key.contains("alive"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);
        systemStatus.setValueForPath("node_alive_192-168-10-13", "KO");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        // nothing is removed (don't act on node down)
        System.err.println (resultPrevStatus.getFormattedValue());
        assertTrue (new JSONObject(expectedPrevStatusAllServicesStay).similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangesKubernetesService() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_cerebro_192-168-10-11");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        JSONObject expectedPrevStatusJson = new JSONObject(expectedPrevStatusServicesRemoved);
        expectedPrevStatusJson.put("etcd_installed_on_IP_192-168-10-11", "OK"); // need to re-add this since the expectedPrevStatusServicesRemoved is for another test
        expectedPrevStatusJson.remove("cerebro_installed_on_IP_KUBERNETES_NODE");

        // cerebro is missing
        //System.err.println (expectedPrevStatusJson.toString(2));
        //System.err.println (resultPrevStatus.getJSONObject().toString(2));
        assertTrue (expectedPrevStatusJson.similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangesKubernetesServiceWhenKubernetesDown() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_cerebro_192-168-10-11");
        systemStatus.getJSONObject().put("service_kube-master_192-168-10-11", "KO");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // Since the Kubernetes service status change has not been saved (since marazthon is down), it's still empty !!
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        assertEquals("{}", resultPrevStatus.getJSONObject().toString(2));
    }

    @Test
    public void testHandleStatusChangeMoreServicesRemoved() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and services are removed from it
        // if they are down
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        //logger.debug (systemStatus.getIpAddresses());

        // remove all status for node 2 : 192-168-10-13 EXCEPT KAFKA AND ELASTICSEARCH
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("kafka") && !key.contains("elasticsearch") && !key.contains("nbr")  && !key.contains("alive"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        //logger.debug (servicesInstallStatus.getIpAddresses());


        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        // kafka and elasticsearch have been kept
        System.err.println (resultPrevStatus.getFormattedValue());
        assertTrue(new JSONObject("{\n" +
                "    \"kafka-manager_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"logstash_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kube-slave_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kibana_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"etcd_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"ntp_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"cerebro_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"gluster_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kube-master_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"spark-runtime_installed_on_IP_KUBERNETES_NODE\": \"OK\"\n" +
                "}").similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangeWhenNodeRemovedFromConfig() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and services are removed from it
        // if they are down
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        //logger.debug (systemStatus.getIpAddresses());

        // remove all status for node 2 (really means it has been removed from config)
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);


        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        //logger.debug (servicesInstallStatus.getIpAddresses());

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        // kafka and elasticsearch have been kept
        System.err.println (resultPrevStatus.getFormattedValue());
        JSONObject expectedPrevStatusJson = new JSONObject(expectedPrevStatusAllServicesStay);
        assertTrue(expectedPrevStatusJson.similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangeWhenNodeRemovedFromConfigAndNodeIsDown() throws Exception {

        Set<String> configuredAndLiveIps = new HashSet<String>(){{
            add("192.168.10.11");
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and
        // if it is down, all services are removed without checking them
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        logger.debug (systemStatus.getNodes());

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        logger.debug (servicesInstallStatus.getNodes());

        // remove all status for node 2
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationService.loadServicesInstallationStatus();

        // everything has been removed
        System.err.println(resultPrevStatus.getFormattedValue());
        assertTrue(new JSONObject("{\n" +
                "    \"kafka-manager_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"logstash_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kube-slave_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kibana_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"etcd_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"ntp_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"cerebro_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"gluster_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kube-master_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"spark-runtime_installed_on_IP_KUBERNETES_NODE\": \"OK\"\n" +
                "}").similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testApplyServiceOperation() throws Exception {

        AtomicBoolean called = new AtomicBoolean(false);

        systemService.applyServiceOperation("ntp", "192.168.10.11", "test op", () -> {
            called.set(true);
            return "OK";
        });

        assertTrue (called.get());

        assertEquals("[" +
                        "{\"type\":\"Doing\",\"message\":\"test op ntp on 192.168.10.11\"}, " +
                        "{\"type\":\"Info\",\"message\":\"test op ntp succeeded on 192.168.10.11\"}" +
                        "]",
                ""+notificationService.getSubList(0));

        SimpleOperationCommand.SimpleOperationId operationId = new SimpleOperationCommand.SimpleOperationId("test op", "ntp", "192.168.10.11");

        List<String> messages = operationsMonitoringService.getNewMessages(operationId, 0);

        assertEquals ("[\n" +
                "test op ntp on 192.168.10.11, Done test op ntp on 192.168.10.11, " +
                "-------------------------------------------------------------------------------, " +
                "OK" +
                "]", ""+messages);
    }

}
