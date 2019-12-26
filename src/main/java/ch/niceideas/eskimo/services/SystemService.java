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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.SystemStatusParser;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SystemService {

    private static final Logger logger = Logger.getLogger(SystemService.class);

    public static final String NODES_STATUS_JSON_PATH = "/nodes-status.json";
    public static final String USR_LOCAL_BIN_JQ = "/usr/local/bin/jq";
    public static final String USR_LOCAL_BIN_MESOS_CLI_SH = "/usr/local/bin/mesos-cli.sh";
    public static final String SERVICE_PREFIX = "service_";
    public static final String TMP_PATH_PREFIX = "/tmp/";

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private ServicesInstallationSorter servicesInstallationSorter;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private ServicesConfigService servicesConfigService;

    @Value("${system.failedServicesTriggerCount}")
    private int failedServicesTriggerCount = 2;

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimout = 400;

    @Value("${system.statusFetchThreadCount}")
    private int parallelismStatusThreadCount = 10;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    private ReentrantLock statusFileLock = new ReentrantLock();
    private ReentrantLock nodesConfigFileLock = new ReentrantLock();
    private ReentrantLock prevStatusCheckLock = new ReentrantLock();
    private ReentrantLock systemActionLock = new ReentrantLock();

    private AtomicBoolean interruption = new AtomicBoolean(false);
    private AtomicBoolean interruptionNotified = new AtomicBoolean(false);
    private boolean lastOperationSuccess;

    private Map<String, Integer> serviceMissingCounter = new ConcurrentHashMap<>();

    /**
     * for tests
     */
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }
    void setProxyManagerService(ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    void setSystemOperationService(SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setMemoryComputer (MemoryComputer memoryComputer) {
        this.memoryComputer = memoryComputer;
    }
    void setServicesInstallationSorter (ServicesInstallationSorter servicesInstallationSorter) {
        this.servicesInstallationSorter = servicesInstallationSorter;
    }
    void setServicesConfigService (ServicesConfigService servicesConfigService) {
        this.servicesConfigService = servicesConfigService;
    }


    public boolean isProcessingPending() {
        return systemActionLock.isLocked();
    }

    void setProcessingPending() {
        systemActionLock.lock();
    }

    void releaseProcessingPending() {
        systemActionLock.unlock();
        interruption.set(false);
        interruptionNotified.set(false);
    }

    public void interruptProcessing() {
        if (isProcessingPending()) {
            interruption.set(true);
        }
    }

    boolean isInterrupted () {
        notifyInterruption();
        return interruption.get();
    }

    void notifyInterruption() {
        if (interruption.get() && !interruptionNotified.get()) {
            notificationService.addError("Processing has been interrupted");
            messagingService.addLine("Processing has been interrupted");
            interruptionNotified.set(true);
        }
    }

    public boolean getLastOperationSuccess() {
        return lastOperationSuccess;
    }

    void setLastOperationSuccess(boolean success) {
        lastOperationSuccess = success;
    }

    public void showJournal(String service, String ipAddress) throws SSHCommandException {
        applyServiceOperation(service, ipAddress, "Showing journal of", () -> sshCommandService.runSSHCommand(ipAddress, "sudo journalctl -u " + service));
    }

    public void startService(String service, String ipAddress) throws SSHCommandException {
        applyServiceOperation(service, ipAddress, "Starting", () -> sshCommandService.runSSHCommand(ipAddress, "sudo systemctl start " + service));
    }

    public void stopService(String service, String ipAddress) throws SSHCommandException {
        applyServiceOperation(service, ipAddress, "Stopping", () -> sshCommandService.runSSHCommand(ipAddress, "sudo systemctl stop " + service));
    }

    public void restartService(String service, String ipAddress) throws SSHCommandException {
        applyServiceOperation(service, ipAddress, "Restarting", () -> sshCommandService.runSSHCommand(ipAddress, "sudo systemctl restart " + service));
    }

    private void logOperationMessage(String operation) {
        messagingService.addLines(new String[]{
                "\n" + operation
        });
    }

    void applyServiceOperation(String service, String ipAddress, String opLabel, ServiceOperation<String> operation) throws SSHCommandException {

        boolean success = false;
        setProcessingPending();
        try {

            notificationService.addDoing(opLabel + " " + service + " on " + ipAddress);
            String message = opLabel + " " + service + " on " + ipAddress;
            logOperationMessage (message);
            messagingService.addLines("Done "
                    + message
                    + "\n-------------------------------------------------------------------------------\n"
                    + operation.call());
            notificationService.addInfo(opLabel + " " + service + " succeeded on " + ipAddress);

            success = true;
        } finally {
            setLastOperationSuccess (success);
            releaseProcessingPending();
        }
    }

    public SystemStatusWrapper getStatus() throws SystemException, NodesConfigurationException, FileException, SetupException, ConnectionManagerException {

        // 0. Build returned status
        SystemStatusWrapper systemStatus = SystemStatusWrapper.empty();

        // 1. Load Node Config
        NodesConfigWrapper rawNodesConfig = loadNodesConfig();

        // 1.1. Load Node status
        ServicesInstallStatusWrapper servicesInstallationStatus = loadServicesInstallationStatus();

        // 1.2 flag services needing restart
        if (rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            // 2. Build merged status
            final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();
            final ExecutorService threadPool = Executors.newFixedThreadPool(parallelismStatusThreadCount);

            for (Pair<String, String> nbrAndPair : nodesConfig.getNodeAdresses()) {

                int nodeNbr = Integer.parseInt(nbrAndPair.getKey());
                String ipAddress = nbrAndPair.getValue();
                String nodeName = ipAddress.replace(".", "-");

                statusMap.put(("node_nbr_" + nodeName), "" + nodeNbr);
                statusMap.put(("node_address_" + nodeName), ipAddress);

                threadPool.execute(() -> {
                    try {
                        fetchNodeStatus(nodesConfig, statusMap, nbrAndPair, servicesInstallationStatus);
                    } catch (SystemException e) {
                        logger.error(e, e);
                        throw new PooledOperationException(e);
                    }
                });
            }

            threadPool.shutdown();
            try {
                threadPool.awaitTermination(operationWaitTimout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error(e, e);
            }

            // fill in systemStatus
            for (String key : statusMap.keySet()) {
                String value = statusMap.get(key);
                systemStatus.setValueForPath(key, value);
            }
        }

        // 4. If a service disappeared, post notification
        try {
            checkServiceDisappearance(systemStatus);
        } catch (JSONException e) {
            logger.warn(e, e);
        }

        // 5. Handle status update if a service seem to have disappeared

        // 5.1 Test if any additional node should be check for being live
        Set<String> systemStatusIpAddresses = systemStatus.getIpAddresses();
        Set<String> additionalIpToTests = servicesInstallationStatus.getIpAddresses().stream()
                .filter(ip -> !systemStatusIpAddresses.contains(ip))
                .collect(Collectors.toSet());

        Set<String> liveIps = new HashSet<>(systemStatusIpAddresses);
        for (String ipAddress : additionalIpToTests) {

            // find out if SSH connection to host can succeed
            try {
                String ping = sendPing(ipAddress);

                if (ping.startsWith("OK")) {
                    liveIps.add(ipAddress);
                }
            } catch (SSHCommandException e) {
                logger.debug(e, e);
            }
        }

        handleStatusChanges (servicesInstallationStatus, systemStatus, liveIps);

        // 6. return result
        return systemStatus;
    }

    private String sendPing(String ipAddress) throws SSHCommandException {
        return sshCommandService.runSSHScript(ipAddress, "echo OK", false);
    }


    public void applyNodesConfig(OperationsCommand command)
            throws SystemException, ServiceDefinitionException, NodesConfigurationException {

        boolean success = false;
        setProcessingPending();
        try {

            NodesConfigWrapper rawNodesConfig = command.getRawConfig();
            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            Set<String> liveIps = new HashSet<>();
            Set<String> deadIps = new HashSet<>();

            List<Pair<String, String>> nodesSetup = new ArrayList<>();

            // Find out about dead IPs
            Set<String> ipAddressesToTest = new HashSet<>(command.getAllIpAddresses());
            ipAddressesToTest.addAll(nodesConfig.getIpAddresses());
            for (String ipAddress : ipAddressesToTest) {

                // handle potential interruption request
                if (isInterrupted()) {
                    return;
                }

                nodesSetup.add(new Pair<>("node_setup", ipAddress));

                // Ping IP to make sure it is available, report problem with IP if it is not ad move to next one

                // find out if SSH connection to host can succeed
                try {
                    String ping = sendPing(ipAddress);

                    if (!ping.startsWith("OK")) {

                        handleNodeDead(deadIps, ipAddress);
                    } else {
                        liveIps.add(ipAddress);
                    }
                } catch (SSHCommandException e) {
                    logger.debug(e, e);
                    handleNodeDead(deadIps, ipAddress);
                }
            }

            if (!deadIps.isEmpty()) {
                messagingService.addLines("\n");
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, deadIps);

            if (isInterrupted()) {
                return;
            }

            // Nodes setup
            performPooledOperation (nodesSetup, parallelismInstallThreadCount, baseInstallWaitTimout,
                    (operation, error) -> {
                        String ipAddress = operation.getValue();
                        if (nodesConfig.getIpAddresses().contains(ipAddress) && liveIps.contains(ipAddress)) {

                            if (!isInterrupted() && (error.get() == null && !isInstalledOnNode("base_system", ipAddress))) {
                                systemOperationService.applySystemOperation("Installation of Base System on " + ipAddress,
                                        builder -> installEskimoBaseSystem(builder, ipAddress), null);

                                flagInstalledOnNode("base_system", ipAddress);
                            }

                            // topology
                            if (!isInterrupted() && (error.get() == null)) {
                                systemOperationService.applySystemOperation("Installation of Topology and settings on " + ipAddress,
                                        builder -> installTopologyAndSettings(nodesConfig, memoryModel, ipAddress, deadIps), null);
                            }

                            if (!isInterrupted() && (error.get() == null && !isInstalledOnNode("mesos", ipAddress))) {
                                systemOperationService.applySystemOperation("Installation of Mesos on " + ipAddress,
                                        builder -> {
                                            uploadMesos(ipAddress);
                                            builder.append (installMesos(ipAddress));
                                        }, null);

                                flagInstalledOnNode("mesos", ipAddress);
                            }
                        }
                    });

            // first thing first, flag nodes that need to be restared as needing to be restarted
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (command.getRestarts(), nodesConfig, deadIps)) {
                for (Pair<String, String> operation : restarts) {
                    try {
                        updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            String nodeName = ipAddress.replace(".", "-");
                            servicesInstallationStatus.setValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName, "restart");
                        });
                    } catch (FileException | SetupException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                }
            }

            // Installation in batches (groups following dependencies)
            for (List<Pair<String, String>> installations : servicesInstallationSorter.orderOperations (command.getInstallations(), nodesConfig, deadIps)) {

                performPooledOperation (installations, parallelismInstallThreadCount, operationWaitTimout,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (liveIps.contains(ipAddress)) {
                                installService(service, ipAddress);
                            }
                        });
            }

            // uninstallations
            List<List<Pair<String, String>>> orderedUninstallations =  servicesInstallationSorter.orderOperations (command.getUninstallations(), nodesConfig, deadIps);
            Collections.reverse(orderedUninstallations);

            for (List<Pair<String, String>> uninstallations : orderedUninstallations) {
                performPooledOperation(uninstallations, parallelismInstallThreadCount, operationWaitTimout,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (!deadIps.contains(ipAddress)) {
                                uninstallService(service, ipAddress);
                            } else {
                                if (!liveIps.contains(ipAddress)) {
                                    // this means that the node has been de-configured
                                    // (since if it is neither dead nor alive then it just hasn't been tested since it's not
                                    // in the config anymore)
                                    // just consider it uninstalled
                                    uninstallServiceNoOp(service, ipAddress);
                                }
                            }
                        });
            }

            // restarts
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (command.getRestarts(), nodesConfig, deadIps)) {
                performPooledOperation(restarts, parallelismInstallThreadCount, operationWaitTimout,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (liveIps.contains(ipAddress)) {
                                restartServiceForSystem(service, ipAddress);
                            }
                        });
            }

            if (!isInterrupted() && (!Collections.disjoint(deadIps, nodesConfig.getIpAddresses()))) {
                throw new SystemException("At least one configured node was found dead");
            }

            success = true;
        } finally {
            setLastOperationSuccess (success);
            releaseProcessingPending();
        }
    }

    private void performPooledOperation(
            List<Pair<String, String>> operations, int parallelism, long operationWaitTimout, PooledOperation operation)
            throws SystemException {

        final ExecutorService threadPool = Executors.newFixedThreadPool(parallelism);
        AtomicReference<Exception> error = new AtomicReference<>();

        for (Pair<String, String> opToPerform : operations) {

            if (!isInterrupted()) {
                threadPool.execute(() -> {

                    if (!isInterrupted() && (error.get() == null)) {

                        try {
                            operation.call(opToPerform, error);
                        } catch (SystemException | JSONException | FileException | SetupException | ConnectionManagerException e) {
                            logger.error(e, e);
                            error.set(e);
                            throw new PooledOperationException(e);
                        }
                    }
                });
            }
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(operationWaitTimout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error (e, e);
        }

        if (error.get() != null) {
            throw new SystemException(error.get().getMessage(), error.get());
        }
    }

    void handleNodeDead(Set<String> deadIps, String ipAddress) {
        messagingService.addLines("\nNode seems dead " + ipAddress);
        notificationService.addError("Node " + ipAddress + " is dead.");
        deadIps.add(ipAddress);
    }

    private void flagInstalledOnNode(String installation, String ipAddress) throws SystemException {
        try {
            sshCommandService.runSSHCommand(ipAddress, "sudo bash -c \"echo OK > /etc/eskimo_flag_" + installation + "_installed\"");
        } catch (SSHCommandException e) {
            logger.error(e, e);
            throw new SystemException(e.getMessage(), e);
        }
    }

    private boolean isInstalledOnNode(String installation, String ipAddress) {

        try {
            messagingService.addLine("\nChecking " + installation + " on node " + ipAddress);
            String result = sshCommandService.runSSHCommand(ipAddress, "cat /etc/eskimo_flag_" + installation + "_installed");
            return result.contains("OK");
        } catch (SSHCommandException e) {
            logger.debug(e, e);
            return false;
        }
    }

    void restartServiceForSystem(String service, String ipAddress) throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("Restart of " + service + " on " + ipAddress,
                builder -> builder.append (sshCommandService.runSSHCommand(ipAddress, "sudo systemctl restart " + service)),
                status -> status.setValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName, "OK"));
    }

    void uninstallService(String service, String ipAddress) throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on " + ipAddress,
                builder -> proceedWithServiceUninstallation(builder, ipAddress, service),
                status -> status.removeRootKey(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName));
        proxyManagerService.removeServerForService(service, ipAddress);
    }

    void uninstallServiceNoOp(String service, String ipAddress) throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on " + ipAddress,
                builder -> {},
                status -> status.removeRootKey(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName));
        proxyManagerService.removeServerForService(service, ipAddress);
    }

    void installService(String service, String ipAddress)
            throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("installation of " + service + " on " + ipAddress,
                builder -> proceedWithServiceInstallation(builder, ipAddress, service),
                status -> status.setValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName, "OK"));
    }

    void updateAndSaveServicesInstallationStatus(StatusUpdater statusUpdater) throws FileException, SetupException {
        statusFileLock.lock();
        try {
            ServicesInstallStatusWrapper status = loadServicesInstallationStatus();
            statusUpdater.updateStatus(status);
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + NODES_STATUS_JSON_PATH), status.getFormattedValue());
        } finally {
            statusFileLock.unlock();
        }
    }

    public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, SetupException {
        statusFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + NODES_STATUS_JSON_PATH), status.getFormattedValue());
        } finally {
            statusFileLock.unlock();
        }
    }

    public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
        statusFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File statusFile = new File(configStoragePath + NODES_STATUS_JSON_PATH);
            if (!statusFile.exists()) {
                return ServicesInstallStatusWrapper.empty();
            }

            return new ServicesInstallStatusWrapper(statusFile);
        } finally {
            statusFileLock.unlock();
        }
    }

    public void saveNodesConfig(NodesConfigWrapper nodesConfig) throws FileException, SetupException {
        nodesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + "/nodes-config.json"), nodesConfig.getFormattedValue());
        } finally {
            nodesConfigFileLock.unlock();
        }
    }

    public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
        nodesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File nodesConfigFile = new File(configStoragePath + "/nodes-config.json");
            if (!nodesConfigFile.exists()) {
                return null;
            }

            return new NodesConfigWrapper(FileUtils.readFile(nodesConfigFile));
        } catch (JSONException | FileException e) {
            logger.error (e, e);
            throw new SystemException(e);
        } finally {
            nodesConfigFileLock.unlock();
        }
    }

    void fetchNodeStatus
            (NodesConfigWrapper nodesConfig, Map<String, String> statusMap, Pair<String, String> nbrAndPair,
             ServicesInstallStatusWrapper servicesInstallationStatus)
                throws SystemException {

        int nodeNbr = Integer.parseInt(nbrAndPair.getKey());
        String ipAddress = nbrAndPair.getValue();
        String nodeName = ipAddress.replace(".", "-");

        // 3.1 Node answers
        try {

            // find out if SSH connection to host can succeeed
            String ping = null;
            try {
                ping = sendPing(ipAddress);
            } catch (SSHCommandException e) {
                logger.warn(e.getMessage());
                logger.debug(e, e);
            }

            if (StringUtils.isBlank(ping) || !ping.startsWith("OK")) {

                statusMap.put(("node_alive_" + nodeName), "KO");

            } else {

                statusMap.put(("node_alive_" + nodeName), "OK");

                String allServicesStatus = sshCommandService.runSSHScript(ipAddress,
                        "sudo systemctl status --no-pager -al " + servicesDefinition.getAllServicesString() + " 2>/dev/null ", false);

                SystemStatusParser parser = new SystemStatusParser(allServicesStatus);

                for (String service : servicesDefinition.getAllServices()) {

                    // should service be installed on node ?
                    boolean shall = nodesConfig.shouldInstall (service, nodeNbr);

                    // check if service is installed ?
                    //check if service installed using SSH
                    String serviceStatus = parser.getServiceStatus(service);
                    boolean installed = !serviceStatus.equals("NA");

                    if (shall) {
                        if (!installed) {

                            statusMap.put(SERVICE_PREFIX + service + "_" + nodeName, "NA");

                        } else {

                            // check if services is running ?
                            // check if service running using SSH
                            boolean running = parser.getServiceStatus(service).equals("running");

                            if (!running) {
                                statusMap.put(SERVICE_PREFIX + service + "_" + nodeName, "KO");

                            } else {

                                if (servicesInstallationStatus.isServiceOK(service,nodeName)) {
                                    statusMap.put(SERVICE_PREFIX + service + "_" + nodeName, "OK");
                                } else {
                                    statusMap.put(SERVICE_PREFIX + service + "_" + nodeName, "restart");
                                }

                                // configure proxy if required
                                proxyManagerService.updateServerForService(service, ipAddress);
                            }
                        }
                    } else {
                        if (installed) {
                            statusMap.put(SERVICE_PREFIX + service + "_" + nodeName, "TD"); // To Be Deleted
                        }
                    }
                }
            }
        } catch (SSHCommandException | JSONException | ConnectionManagerException e) {
            logger.error(e, e);
            throw new SystemException(e.getMessage(), e);
        }
    }

    public void handleStatusChanges(
            ServicesInstallStatusWrapper servicesInstallationStatus, SystemStatusWrapper systemStatus, Set<String> liveIps)
            throws FileException, SetupException {

        // If there is some processing pending, then nothing is reliable, just move on
        if (!isProcessingPending()) {

            try {

                boolean changes = false;

                for (String serviceStatusFullString : servicesInstallationStatus.getRootKeys()) {

                    String searchedPattern = OperationsCommand.INSTALLED_ON_IP_FLAG;
                    int index = serviceStatusFullString.indexOf(searchedPattern);
                    if (index > -1) {

                        String savedService = serviceStatusFullString.substring(0, index);

                        String nodeName = serviceStatusFullString.substring(index + searchedPattern.length());

                        Boolean nodeAlive = systemStatus.isNodeAlive (nodeName);
                        // this means that node is not configured anymore ! (no status has been obtained)
                        if (nodeAlive == null) {

                            // => we want to consider removing services in any case if not is not only not configured anymore but down
                            // so if node is down in addition to being not configured anymore, we remove all services from saved install stazus
                            String nodeIp = nodeName.replace("-", ".");
                            if (!liveIps.contains(nodeIp)) {
                                if (countErrorAndRemoveServices(servicesInstallationStatus, serviceStatusFullString, savedService, nodeName)) {
                                    changes = true;
                                }
                            }

                            // on the other hand if node is not configured but up, we want to attempt uninstallation, thus
                            // not removing services if they are up
                            else {
                                if (handleRemoveServiceIfDown(servicesInstallationStatus, systemStatus, serviceStatusFullString, savedService, nodeName)) {
                                    changes = true;
                                }
                            }

                        } else if (nodeAlive) { // this means that the node is configured and up

                            if (handleRemoveServiceIfDown(servicesInstallationStatus, systemStatus, serviceStatusFullString, savedService, nodeName)) {
                                changes = true;
                            }
                        } // else if node is configured but down, don't do anything
                    }
                }

                if (changes) {
                    saveServicesInstallationStatus(servicesInstallationStatus);
                }
            } catch (JSONException e) {
                logger.error(e, e);
                // this is no mission critical method, let's silent errors there
            }
        }
    }

    boolean handleRemoveServiceIfDown(
            ServicesInstallStatusWrapper savedSystemStatusWrapper, SystemStatusWrapper systemStatusWrapper,
            String serviceStatusFullString, String savedService, String nodeName) {

        boolean changes = false;

        // make sure service for node name is found in new status
        String serviceStatus = (String) systemStatusWrapper.getValueForPath(SERVICE_PREFIX + savedService + "_" + nodeName);

        // if OK reset error count
        if (StringUtils.isNotBlank(serviceStatus) && !serviceStatus.equals("NA")) {
            serviceMissingCounter.remove(savedService);

        } else {
            if (countErrorAndRemoveServices(savedSystemStatusWrapper, serviceStatusFullString, savedService, nodeName)) {
                changes = true;
            }
        }
        return changes;
    }

    boolean countErrorAndRemoveServices(
            ServicesInstallStatusWrapper servicesInstallationStatus, String serviceStatusFullString,
            String savedService, String nodeName) {
        boolean changes = false;
        // otherwise count error
        Integer counter = serviceMissingCounter.get(serviceStatusFullString);
        if (counter == null) {
            counter = 0;
            serviceMissingCounter.put(serviceStatusFullString, counter);

        } else {

            counter = counter + 1;

            // if error count > 2 (i.e. 3), consider service uninstalled, remove it from saved status
            if (counter > failedServicesTriggerCount) {

                servicesInstallationStatus.removeRootKey(serviceStatusFullString);
                serviceMissingCounter.remove(serviceStatusFullString);
                notificationService.addError("Service " + savedService + " on " + nodeName + " vanished!");

                // unconfigure proxy if required
                proxyManagerService.removeServerForService(savedService, nodeName.replace("-", "."));

                changes = true;

            } else {
                serviceMissingCounter.put(serviceStatusFullString, counter);
            }
        }
        return changes;
    }

    void checkServiceDisappearance(SystemStatusWrapper systemStatus) throws FileException, SetupException {

        prevStatusCheckLock.lock();
        try {

            String configStoragePath = setupService.getConfigStoragePath();

            // load last statusWrapper
            File prevStatusFile = new File(configStoragePath + "/nodes-status-check-previous.json");
            if (prevStatusFile.exists()) {

                String prevStatusAsString = FileUtils.readFile(prevStatusFile);

                if (StringUtils.isNotBlank(prevStatusAsString)) {
                    SystemStatusWrapper previousStatus = new SystemStatusWrapper(prevStatusAsString);

                    for (String service : systemStatus.getRootKeys()) {

                        if (!systemStatus.isServiceOK(service)
                            && previousStatus.isServiceOK(service)) {

                            logger.warn("For service " + service + " - previous status was OK and status is " + systemStatus.getValueForPath(service));
                            notificationService.addError("Service " + service + " got into problem");
                        }
                    }
                }
            }

            // store statusWrapper for next run
            FileUtils.writeFile(new File(configStoragePath + "/nodes-status-check-previous.json"), systemStatus.getFormattedValue());
        } finally {
            prevStatusCheckLock.unlock();
        }
    }


    private String installTopologyAndSettings(NodesConfigWrapper nodesConfig, MemoryModel memoryModel, String ipAddress, Set<String> deadIps)
            throws SystemException, SSHCommandException, IOException {

        File tempTopologyFile = createTempFile("eskimo_topology", ipAddress, ".sh");
        try {
            FileUtils.delete(tempTopologyFile);
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
        try {
            FileUtils.writeFile(tempTopologyFile, servicesDefinition
                    .getTopology(nodesConfig, deadIps)
                    .getTopologyScriptForNode(nodesConfig, memoryModel, nodesConfig.getNodeNumber (ipAddress)));
        } catch (ServiceDefinitionException | NodesConfigurationException | FileException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
        sshCommandService.copySCPFile(ipAddress, tempTopologyFile.getAbsolutePath());
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "mv", tempTopologyFile.getName(), "/etc/eskimo_topology.sh"});
        sshChmod755(ipAddress, "/etc/eskimo_topology.sh");

        try {
            ServicesConfigWrapper servicesConfig = servicesConfigService.loadServicesConfigNoLock();

            File tempServicesSettingsFile = createTempFile("eskimo_services-config", ipAddress, ".json");
            try {
                FileUtils.delete(tempServicesSettingsFile);
            } catch (FileUtils.FileDeleteFailedException e) {
                logger.error (e, e);
                throw new SystemException(e);
            }

            FileUtils.writeFile(tempServicesSettingsFile, servicesConfig.getFormattedValue());

            sshCommandService.copySCPFile(ipAddress, tempServicesSettingsFile.getAbsolutePath());
            sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "mv", tempServicesSettingsFile.getName(), "/etc/eskimo_services-config.json"});
            sshChmod755(ipAddress, "/etc/eskimo_services-config.json");


        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }

        return null;
    }

    private void sshChmod755 (String ipAddress, String file) throws SSHCommandException {
        sshChmod (ipAddress, file, "755");
    }

    private void sshChmod (String ipAddress, String file, String mode) throws SSHCommandException {
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "chmod", mode, file});
    }

    private void uploadMesos(String ipAddress) throws SSHCommandException, SystemException {

        messagingService.addLines(" - Uploading mesos distribution");
        String mesosFlavour = "mesos-" + getNodeFlavour(ipAddress);

        File packageDistributionDir = new File (packageDistributionPath);

        String mesosFileName = setupService.findLastPackageFile("_", mesosFlavour);
        File mesosDistrib = new File (packageDistributionDir, mesosFileName);

        sshCommandService.copySCPFile(ipAddress, mesosDistrib.getAbsolutePath());
    }

    private String getNodeFlavour(String ipAddress) throws SSHCommandException, SystemException {
        // Find out if debian or RHEL or SUSE
        String flavour = null;
        String rawIsDebian = sshCommandService.runSSHScript(ipAddress, "if [[ -f /etc/debian_version ]]; then echo debian; fi");
        if (rawIsDebian.contains("debian")) {
            flavour = "debian";
        }

        if (flavour == null) {
            String rawIsRedHat = sshCommandService.runSSHScript(ipAddress, "if [[ -f /etc/redhat-release ]]; then echo redhat; fi");
            if (rawIsRedHat.contains("redhat")) {
                flavour = "redhat";
            }
        }

        if (flavour == null) {
            String rawIsSuse = sshCommandService.runSSHScript(ipAddress, "if [[ -f /etc/SUSE-brand ]]; then echo suse; fi");
            if (rawIsSuse.contains("suse")) {
                flavour = "suse";
            }
        }

        if (flavour == null) {
            throw new SystemException ("Unknown OS flavour. None of the known OS type marker files has been found.");
        }
        return flavour;
    }

    private String installMesos(String ipAddress) throws SSHCommandException {
        return sshCommandService.runSSHScriptPath(ipAddress, servicesSetupPath + "/base-eskimo/install-mesos.sh");
    }

    private void copyCommand (String source, String target, String ipAddress) throws SSHCommandException {
        sshCommandService.copySCPFile(ipAddress, servicesSetupPath + "/base-eskimo/" + source);
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "mv", source, target});
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "chown", "root.root", target});
        sshChmod755(ipAddress, target);
    }

    private void installEskimoBaseSystem(StringBuilder sb, String ipAddress) throws SSHCommandException {
        sb.append (sshCommandService.runSSHScriptPath(ipAddress, servicesSetupPath + "/base-eskimo/install-eskimo-base-system.sh"));

        sb.append(" - Copying jq program\n");
        copyCommand ("jq-1.6-linux64", USR_LOCAL_BIN_JQ, ipAddress);

        sb.append(" - Copying mesos-cli script\n");
        copyCommand ("mesos-cli.sh", USR_LOCAL_BIN_MESOS_CLI_SH, ipAddress);

        connectionManagerService.forceRecreateConnection(ipAddress); // user privileges may have changed
    }

    private String proceedWithServiceUninstallation(StringBuilder sb, String ipAddress, String service)
            throws SSHCommandException, SystemException {

        // 1. Calling uninstall.sh script if it exists
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        try {
            File uninstallScriptFile = new File(containerFolder, "uninstall.sh");
            if (uninstallScriptFile.exists()) {
                sb.append(" - Calling uninstall script\n");

                sb.append(sshCommandService.runSSHScriptPath(ipAddress, uninstallScriptFile.getAbsolutePath()));
            }
        } catch (SSHCommandException e) {
            logger.warn (e, e);
            sb.append (e.getMessage());
        }

        // 2. Stop service
        sb.append(" - Stopping Service\n");
        sshCommandService.runSSHCommand(ipAddress, "sudo systemctl stop " + service);

        // 3. Uninstall systemd service file
        sb.append(" - Removing systemd Service File\n");
        // Find systemd unit config files directory
        String foundStandardFlag = sshCommandService.runSSHScript(ipAddress, "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi");
        if (foundStandardFlag.contains("found_standard")) {
            sshCommandService.runSSHCommand(ipAddress, "sudo rm -f  /lib/systemd/system/" + service + ".service");
        } else {
            sshCommandService.runSSHCommand(ipAddress, "sudo rm -f  /usr/lib/systemd/system/" + service + ".service");
        }

        // 4. Delete docker container
        sb.append(" - Removing docker container \n");
        sshCommandService.runSSHCommand(ipAddress, "sudo docker rm -f " + service + " || true ");

        // 5. Delete docker image
        sb.append(" - Removing docker image \n");
        sshCommandService.runSSHCommand(ipAddress, "sudo docker image rm -f eskimo:" + servicesDefinition.getService(service).getImageName());

        // 6. Reloading systemd daemon
        sb.append(" - Reloading systemd daemon \n");
        sshCommandService.runSSHCommand(ipAddress, "sudo systemctl daemon-reload");
        sshCommandService.runSSHCommand(ipAddress, "sudo systemctl reset-failed");

        return sb.toString();
    }

    private void proceedWithServiceInstallation(StringBuilder sb, String ipAddress, String service)
            throws IOException, SystemException, SSHCommandException {

        sb.append(" - Creating archive and copying it over\n");

        // 1. Find container folder, archive and copy there

        // 1.1 Make sure folder exist
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        // 1.2 Create archive


        // Get the temporary directory and print it.
        String tempDir = System.getProperty("java.io.tmpdir");
        if (StringUtils.isBlank(tempDir)) {
            throw new SystemException("Unable to get system temporary directory.");
        }
        File tmpArchiveFile = createTempFile(service, ipAddress, ".tgz");
        tmpArchiveFile.delete(); // I just want te file name
        File archive = new File(tempDir + "/" + tmpArchiveFile.getName());
        FileUtils.createTarFile(servicesSetupPath + "/" + service, archive);
        if (!archive.exists()) {
            throw new SystemException("Could not create archive for service " + service + " : " + TMP_PATH_PREFIX +  tmpArchiveFile.getName());
        }

        // 2. copy it over to target node and extract it

        // 2.1
        sshCommandService.copySCPFile(ipAddress, archive.getAbsolutePath());

        exec(ipAddress, sb, "rm -Rf " + TMP_PATH_PREFIX + service);
        exec(ipAddress, sb, "rm -f " + TMP_PATH_PREFIX + service + ".tgz");
        exec(ipAddress, sb, "mv " +  tmpArchiveFile.getName() + " " + TMP_PATH_PREFIX + service + ".tgz");
        exec(ipAddress, sb, "tar xfz " + TMP_PATH_PREFIX + service + ".tgz --directory=" + TMP_PATH_PREFIX);
        exec(ipAddress, sb, "chmod 755 " + TMP_PATH_PREFIX + service + "/setup.sh");

        // 2.2 delete local archive
        try {
            FileUtils.delete(archive);
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error(e, e);
            throw new SystemException("Could not delete archive /tmp/" + service + ".tgz");
        }

        // 3. Copy container image there if any

        String imageName = servicesDefinition.getService(service).getImageName();
        if (StringUtils.isNotBlank(imageName)) {
            String imageFileName = setupService.findLastPackageFile(SetupService.DOCKER_TEMPLATE_PREFIX, imageName);

            File containerFile = new File(packageDistributionPath + "/" + imageFileName);
            if (containerFile.exists()) {

                sb.append(" - Copying over docker image " + imageFileName + "\n");
                sshCommandService.copySCPFile(ipAddress, packageDistributionPath + "/" + imageFileName);

                exec(ipAddress, sb, new String[]{"mv", imageFileName, TMP_PATH_PREFIX + service + "/"});

                exec(ipAddress, sb, new String[]{"ln", "-s", TMP_PATH_PREFIX + service + "/" + imageFileName, TMP_PATH_PREFIX + service + "/" + SetupService.DOCKER_TEMPLATE_PREFIX + imageName + ".tar.gz"});

            } else {
                sb.append(" - (no container found for ").append(service).append(" - will just invoke setup)");
            }
        }

        // 4. call setup script
        try {
            exec(ipAddress, sb, new String[]{"bash", TMP_PATH_PREFIX + service + "/setup.sh", ipAddress});
        } catch (SSHCommandException e) {
            logger.debug (e, e);
            sb.append(e.getMessage());
            throw new SystemException ("Setup.sh script execution for " + service + " on node " + ipAddress + " failed.");
        }

        // 5. cleanup
        exec(ipAddress, sb, "rm -Rf " + TMP_PATH_PREFIX + service);
        exec(ipAddress, sb, "rm -f " + TMP_PATH_PREFIX + service + ".tgz");

        if (StringUtils.isNotBlank(imageName)) {
            try {
                sb.append(" - Deleting docker template image");
                exec(ipAddress, new StringBuilder(), "docker image rm eskimo:" + imageName + "_template");
            } catch (SSHCommandException e) {
                logger.error(e, e);
                sb.append(e.getMessage());
                // ignroed any further
            }
        }

        try {
            FileUtils.delete (new File (TMP_PATH_PREFIX + tmpArchiveFile.getName() + ".tgz"));
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
    }

    protected File createTempFile(String service, String ipAddress, String extension) throws IOException {
        return File.createTempFile(service, extension);
    }

    private void exec(String ipAddress, StringBuilder sb, String[] setupScript) throws SSHCommandException {
        sb.append(sshCommandService.runSSHCommand(ipAddress, setupScript));
    }

    private void exec(String ipAddress, StringBuilder sb, String command) throws SSHCommandException {
        sb.append(sshCommandService.runSSHCommand(ipAddress, command));
    }


    interface PooledOperation {
        void call(Pair<String, String> operation, AtomicReference<Exception> error)
                throws SystemException, FileException, SetupException, ConnectionManagerException;
    }

    interface ServiceOperation<V> {
        V call() throws SSHCommandException;
    }

    interface StatusUpdater {
        void updateStatus (ServicesInstallStatusWrapper servicesInstallationStatus);
    }

    public static class PooledOperationException extends RuntimeException {

        static final long serialVersionUID = -3317632123352229248L;

        PooledOperationException(Throwable cause) {
            super(cause);
        }
    }
}
