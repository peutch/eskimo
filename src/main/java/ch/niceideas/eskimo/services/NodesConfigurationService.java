package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class NodesConfigurationService {

    private static final Logger logger = Logger.getLogger(NodesConfigurationService.class);

    public static final String USR_LOCAL_BIN_JQ = "/usr/local/bin/jq";
    public static final String USR_LOCAL_SBIN_GLUSTER_MOUNT_SH = "/usr/local/sbin/gluster_mount.sh";
    public static final String USR_LOCAL_BIN_ESKIMO_KUBECTL = "/usr/local/bin/eskimo-kubectl";
    public static final String ESKIMO_TOPOLOGY_SH = "/etc/eskimo_topology.sh";

    @Autowired
    private ServicesInstallationSorter servicesInstallationSorter;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private SystemService systemService;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private KubernetesService kubernetesService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimoutSeconds = 800; // ~ 13 minutes (for an individual step)

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    /* For tests */
    void setServicesInstallationSorter (ServicesInstallationSorter servicesInstallationSorter) {
        this.servicesInstallationSorter = servicesInstallationSorter;
    }
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setMemoryComputer (MemoryComputer memoryComputer) {
        this.memoryComputer = memoryComputer;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setSystemOperationService(SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
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
    void setKubernetesService (KubernetesService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }
    void setSystemService (SystemService systemService) {
        this.systemService = systemService;
    }
    void setConnectionManagerService (ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }
    void setOperationsMonitoringService (OperationsMonitoringService operationsMonitoringService) {
        this.operationsMonitoringService = operationsMonitoringService;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyNodesConfig(ServiceOperationsCommand command)
            throws NodesConfigurationException {

        boolean success = false;
        try {

            logger.info ("Starting System Deployment Operations.");

            operationsMonitoringService.operationsStarted(command);

            NodesConfigWrapper rawNodesConfig = command.getRawConfig();
            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            Set<String> liveIps = new HashSet<>();
            Set<String> deadIps = new HashSet<>();

            List<Pair<String, String>> nodeSetupPairs = systemService.buildDeadIps(command.getAllNodes(), nodesConfig, liveIps, deadIps);
            if (nodeSetupPairs == null) {
                return;
            }

            List<ServiceOperationsCommand.ServiceOperationId> nodesSetup =
                    nodeSetupPairs.stream()
                            .map(nodeSetupPair -> new ServiceOperationsCommand.ServiceOperationId(ServiceOperationsCommand.CHECK_INSTALL_OP_TYPE, ServiceOperationsCommand.BASE_SYSTEM, nodeSetupPair.getValue()))
                            .collect(Collectors.toList());

            KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, kubeServicesConfig, deadIps);

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            // Nodes setup
            systemService.performPooledOperation(nodesSetup, parallelismInstallThreadCount, baseInstallWaitTimout,
                    (operation, error) -> {
                        String node = operation.getNode();
                        if (nodesConfig.getNodeAddresses().contains(node) && liveIps.contains(node)) {

                            systemOperationService.applySystemOperation(
                                    new ServiceOperationsCommand.ServiceOperationId(ServiceOperationsCommand.CHECK_INSTALL_OP_TYPE, ServiceOperationsCommand.BASE_SYSTEM, node),
                                    ml -> {

                                        if (!operationsMonitoringService.isInterrupted() && error.get() == null) {
                                            operationsMonitoringService.addInfo(operation, "Checking / Installing Base system");
                                            if (isMissingOnNode("base_system", node)) {
                                                installEskimoBaseSystem(ml, node);
                                                flagInstalledOnNode("base_system", node);
                                            }
                                        }

                                        // topology
                                        if (!operationsMonitoringService.isInterrupted() && (error.get() == null)) {
                                            operationsMonitoringService.addInfo(operation, "Installing Topology and settings");
                                            installTopologyAndSettings(nodesConfig, kubeServicesConfig, servicesInstallStatus, memoryModel, node);
                                        }

                                        if (!operationsMonitoringService.isInterrupted() && (error.get() == null)) {
                                            operationsMonitoringService.addInfo(operation, "Checking / Installing Kubernetes");
                                            if (isMissingOnNode("k8s", node)) {
                                                uploadKubernetes(node);
                                                ml.addInfo(installK8s(node));
                                                flagInstalledOnNode("k8s", node);
                                            }
                                        }

                                    }, null);
                        }
                    });

            // first thing first, flag services that need to be restarted as "needing to be restarted"
            for (List<ServiceOperationsCommand.ServiceOperationId> restarts : command.getRestartsInOrder(servicesInstallationSorter, nodesConfig)) {
                for (ServiceOperationsCommand.ServiceOperationId restart : restarts) {
                    try {
                        configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                            String nodeName = restart.getNode().replace(".", "-");
                            if (restart.getNode().equals(ServiceOperationsCommand.KUBERNETES_FLAG)) {
                                nodeName = ServicesInstallStatusWrapper.KUBERNETES_NODE;
                            }
                            servicesInstallationStatus.setInstallationFlag(restart.getService(), nodeName, "restart");
                        });
                    } catch (FileException | SetupException e) {
                        logger.error(e, e);
                        throw new SystemException(e);
                    }
                }
            }

            // Installation in batches (groups following dependencies)
            for (List<ServiceOperationsCommand.ServiceOperationId> installations : command.getInstallationsInOrder(servicesInstallationSorter, nodesConfig)) {

                systemService.performPooledOperation(installations, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            if (liveIps.contains(operation.getNode())) {
                                installService(operation);
                            }
                        });
            }

            // uninstallations
            for (List<ServiceOperationsCommand.ServiceOperationId> uninstallations : command.getUninstallationsInOrder(servicesInstallationSorter, nodesConfig)) {
                systemService.performPooledOperation(uninstallations, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            if (!deadIps.contains(operation.getNode())) {
                                uninstallService(operation);
                            } else {
                                if (!liveIps.contains(operation.getNode())) {
                                    // this means that the node has been de-configured
                                    // (since if it is neither dead nor alive then it just hasn't been tested since it's not
                                    // in the config anymore)
                                    // just consider it uninstalled
                                    uninstallServiceNoOp(operation);
                                }
                            }
                        });
            }

            // restarts
            for (List<ServiceOperationsCommand.ServiceOperationId> restarts : servicesInstallationSorter.orderOperations(
                    command.getRestarts(), nodesConfig)) {
                systemService.performPooledOperation(restarts, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            if (operation.getNode().equals(ServiceOperationsCommand.KUBERNETES_FLAG) || liveIps.contains(operation.getNode())) {
                                restartServiceForSystem(operation);
                            }
                        });
            }

            if (!operationsMonitoringService.isInterrupted() && (!Collections.disjoint(deadIps, nodesConfig.getNodeAddresses()))) {
                operationsMonitoringService.addGlobalInfo("At least one configured node was found dead");
                throw new NodesConfigurationException("At least one configured node was found dead");
            }

            success = true;

        } catch (SetupException | SystemException | FileException | ServiceDefinitionException e) {
            logger.error (e, e);
            throw new NodesConfigurationException(e);
        } finally {
            operationsMonitoringService.operationsFinished(success);
            logger.info ("System Deployment Operations Completed.");
        }
    }

    void installEskimoBaseSystem(MessageLogger ml, String node) throws SSHCommandException {
        SSHConnection connection = null;
        try {
            connection = connectionManagerService.getPrivateConnection(node);

            ml.addInfo(" - Calling install-eskimo-base-system.sh");
            ml.addInfo(sshCommandService.runSSHScriptPath(connection, servicesSetupPath + "/base-eskimo/install-eskimo-base-system.sh"));

            ml.addInfo(" - Copying jq program");
            copyCommand("jq-1.6-linux64", USR_LOCAL_BIN_JQ, connection);

            ml.addInfo(" - Copying gluster-mount script");
            copyCommand("gluster_mount.sh", USR_LOCAL_SBIN_GLUSTER_MOUNT_SH, connection);

            ml.addInfo(" - Copying eskimo-kubectl script");
            copyCommand("eskimo-kubectl", USR_LOCAL_BIN_ESKIMO_KUBECTL, connection);

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private String installK8s(String node) throws SSHCommandException {
        return sshCommandService.runSSHScriptPath(node, servicesSetupPath + "/base-eskimo/install-kubernetes.sh");
    }

    void copyCommand (String source, String target, SSHConnection connection) throws SSHCommandException {
        sshCommandService.copySCPFile(connection, servicesSetupPath + "/base-eskimo/" + source);
        sshCommandService.runSSHCommand(connection, new String[]{"sudo", "mv", source, target});
        sshCommandService.runSSHCommand(connection, new String[]{"sudo", "chown", "root.root", target});
        sshChmod755(connection, target);
    }

    private boolean isMissingOnNode(String installation, String node) {

        try {
            String result = sshCommandService.runSSHCommand(node, "cat /etc/eskimo_flag_" + installation + "_installed");
            return StringUtils.isBlank(result) || !result.contains("OK");
        } catch (SSHCommandException e) {
            logger.debug(e, e);
            return true;
        }
    }

    void installTopologyAndSettings(
            NodesConfigWrapper nodesConfig,
            KubernetesServicesConfigWrapper kubeServicesConfig,
            ServicesInstallStatusWrapper servicesInstallStatus,
            MemoryModel memoryModel, String node)
            throws SystemException, SSHCommandException, IOException {

        SSHConnection connection = null;
        try {

            connection = connectionManagerService.getPrivateConnection(node);

            File tempTopologyFile = systemService.createTempFile("eskimo_topology", node, ".sh");
            try {
                FileUtils.delete(tempTopologyFile);
            } catch (FileUtils.FileDeleteFailedException e) {
                logger.error (e, e);
                throw new SystemException(e);
            }
            try {
                FileUtils.writeFile(tempTopologyFile, servicesDefinition
                        .getTopology(nodesConfig, kubeServicesConfig, node)
                        .getTopologyScriptForNode(nodesConfig, kubeServicesConfig, servicesInstallStatus, memoryModel, nodesConfig.getNodeNumber (node)));
            } catch (ServiceDefinitionException | NodesConfigurationException | FileException e) {
                logger.error (e, e);
                throw new SystemException(e);
            }
            sshCommandService.copySCPFile(connection, tempTopologyFile.getAbsolutePath());
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "mv", tempTopologyFile.getName(), ESKIMO_TOPOLOGY_SH});
            sshChmod755(connection, ESKIMO_TOPOLOGY_SH);

            ServicesSettingsWrapper servicesConfig = configurationService.loadServicesConfigNoLock();

            File tempServicesSettingsFile = systemService.createTempFile("eskimo_services-settings", node, ".json");
            try {
                FileUtils.delete(tempServicesSettingsFile);
            } catch (FileUtils.FileDeleteFailedException e) {
                logger.error (e, e);
                throw new SystemException(e);
            }

            FileUtils.writeFile(tempServicesSettingsFile, servicesConfig.getFormattedValue());

            sshCommandService.copySCPFile(connection, tempServicesSettingsFile.getAbsolutePath());
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "mv", tempServicesSettingsFile.getName(), "/etc/eskimo_services-settings.json"});
            sshChmod755(connection, "/etc/eskimo_services-settings.json");

        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new SystemException(e);

        } catch (ConnectionManagerException e) {
            throw new SystemException(e);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private void flagInstalledOnNode(String installation, String node) throws SystemException {
        try {
            sshCommandService.runSSHCommand(node, "sudo bash -c \"echo OK > /etc/eskimo_flag_" + installation + "_installed\"");
        } catch (SSHCommandException e) {
            logger.error(e, e);
            throw new SystemException(e.getMessage(), e);
        }
    }

    private void uploadKubernetes(String node) throws SSHCommandException, SystemException {
        SSHConnection connection = null;
        try {
            connection = connectionManagerService.getPrivateConnection(node);

            File packageDistributionDir = new File (packageDistributionPath);

            String kubeFileName = setupService.findLastPackageFile("_", "kube");
            File kubeDistrib = new File (packageDistributionDir, kubeFileName);

            sshCommandService.copySCPFile(connection, kubeDistrib.getAbsolutePath());

        } catch (ConnectionManagerException e) {
            throw new SystemException(e);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    void uninstallService(ServiceOperationsCommand.ServiceOperationId operationId) throws SystemException {
        String nodeName = operationId.getNode().replace(".", "-");
        systemOperationService.applySystemOperation(operationId,
                ml -> proceedWithServiceUninstallation(ml, operationId.getNode(), operationId.getService()),
                status -> status.removeInstallationFlag(operationId.getService(), nodeName));
        proxyManagerService.removeServerForService(operationId.getService(), operationId.getNode());
    }

    void uninstallServiceNoOp(ServiceOperationsCommand.ServiceOperationId operationId) throws SystemException {
        String nodeName = operationId.getNode().replace(".", "-");
        systemOperationService.applySystemOperation(operationId,
                builder -> {},
                status -> status.removeInstallationFlag(operationId.getService(), nodeName));
        proxyManagerService.removeServerForService(operationId.getService(), operationId.getNode());
    }

    void installService(ServiceOperationsCommand.ServiceOperationId operationId)
            throws SystemException {
        String nodeName = operationId.getNode().replace(".", "-");
        systemOperationService.applySystemOperation(operationId,
                ml -> proceedWithServiceInstallation(ml, operationId.getNode(), operationId.getService()),
                status -> status.setInstallationFlag(operationId.getService(), nodeName, "OK"));
    }

    void restartServiceForSystem(SimpleOperationCommand.SimpleOperationId operationId) throws SystemException {
        String nodeName = operationId.getNode().replace(".", "-");

        if (servicesDefinition.getService(operationId.getService()).isKubernetes()) {

            systemOperationService.applySystemOperation(operationId,
                    ml -> {
                        try {
                            ml.addInfo(kubernetesService.restartServiceInternal(servicesDefinition.getService(operationId.getService()), operationId.getNode()));
                        } catch (KubernetesException e) {
                            logger.error (e, e);
                            throw new SystemException (e);
                        }
                    },
                    status -> status.setInstallationFlag(operationId.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE, "OK") );

        } else {
            systemOperationService.applySystemOperation(operationId,
                    ml -> ml.addInfo(sshCommandService.runSSHCommand(operationId.getNode(), "sudo systemctl restart " + operationId.getService())),
                    status -> status.setInstallationFlag(operationId.getService(), nodeName, "OK"));
        }
    }


    private void proceedWithServiceUninstallation(MessageLogger ml, String node, String service)
            throws SSHCommandException, SystemException {

        SSHConnection connection = null;
        try {
            connection = connectionManagerService.getPrivateConnection(node);

            // 1. Calling uninstall.sh script if it exists
            systemService.callUninstallScript(ml, connection, service);

            // 2. Stop service
            ml.addInfo(" - Stopping Service");
            sshCommandService.runSSHCommand(connection, "sudo systemctl stop " + service);

            // 3. Uninstall systemd service file
            ml.addInfo(" - Removing systemd Service File");
            // Find systemd unit config files directory
            String foundStandardFlag = sshCommandService.runSSHScript(connection, "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi");
            if (foundStandardFlag.contains("found_standard")) {
                sshCommandService.runSSHCommand(connection, "sudo rm -f  /lib/systemd/system/" + service + ".service");
            } else {
                sshCommandService.runSSHCommand(connection, "sudo rm -f  /usr/lib/systemd/system/" + service + ".service");
            }

            // 4. Delete docker container
            ml.addInfo(" - Removing docker container");
            sshCommandService.runSSHCommand(connection, "sudo docker rm -f " + service + " || true ");

            // 5. Delete docker image
            ml.addInfo(" - Removing docker image");
            sshCommandService.runSSHCommand(connection, "sudo docker image rm -f eskimo:" + servicesDefinition.getService(service).getImageName());

            // 6. Reloading systemd daemon
            ml.addInfo(" - Reloading systemd daemon");
            sshCommandService.runSSHCommand(connection, "sudo systemctl daemon-reload");
            sshCommandService.runSSHCommand(connection, "sudo systemctl reset-failed");

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private void proceedWithServiceInstallation(MessageLogger ml, String node, String service)
            throws IOException, SystemException, SSHCommandException {

        String imageName = servicesDefinition.getService(service).getImageName();

        SSHConnection connection = null;
        try {
            connection = connectionManagerService.getPrivateConnection(node);

            ml.addInfo(" - Creating archive and copying it over");
            File tmpArchiveFile = systemService.createRemotePackageFolder(ml, connection, node, service, imageName);

            // 4. call setup script
            ml.addInfo(" - Calling setup script");
            systemService.installationSetup(ml, connection, node, service);

            // 5. cleanup
            ml.addInfo(" - Performing cleanup");
            systemService.installationCleanup(ml, connection, service, imageName, tmpArchiveFile);

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private void sshChmod755 (SSHConnection connection, String file) throws SSHCommandException {
        sshChmod (connection, file, "755");
    }

    private void sshChmod (SSHConnection connection, String file, String mode) throws SSHCommandException {
        sshCommandService.runSSHCommand(connection, new String[]{"sudo", "chmod", mode, file});
    }

    String getNodeFlavour(SSHConnection connection) throws SSHCommandException, SystemException {
        // Find out if debian or RHEL or SUSE
        String flavour = null;
        String rawIsDebian = sshCommandService.runSSHScript(connection, "if [[ -f /etc/debian_version ]]; then echo debian; fi");
        if (rawIsDebian.contains("debian")) {
            flavour = "debian";
        }

        if (flavour == null) {
            String rawIsRedHat = sshCommandService.runSSHScript(connection, "if [[ -f /etc/redhat-release ]]; then echo redhat; fi");
            if (rawIsRedHat.contains("redhat")) {
                flavour = "redhat";
            }
        }

        if (flavour == null) {
            String rawIsSuse = sshCommandService.runSSHScript(connection, "if [[ -f /etc/SUSE-brand ]]; then echo suse; fi");
            if (rawIsSuse.contains("suse")) {
                flavour = "suse";
            }
        }

        if (flavour == null) {
            throw new SystemException ("Unknown OS flavour. None of the known OS type marker files has been found.");
        }
        return flavour;
    }

}
