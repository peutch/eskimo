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


package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Topology;
import ch.niceideas.eskimo.services.*;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(AbstractSetupShellTest.class);

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    protected void copyFile(String jailPath, String source) throws IOException {
        FileUtils.copy(
                new File ("./services_setup/" + getServiceName() + "/" + source),
                new File (jailPath + "/" + source));
    }

    protected final String setupJail(String serviceName) throws Exception {
        String jailPath = createJail();

        // Enhance setup script
        String setupScript = FileUtils.readFile(new File("./services_setup/" + serviceName + "/setup.sh"));

        // inject custom topology loading
        setupScript = setupScript.replace("loadTopology", ". ./eskimo-topology.sh");
        FileUtils.writeFile(new File(jailPath + "/setup.sh"), setupScript);


        // Enhance common script
        String commonScript = FileUtils.readFile(new File("./services_setup/" + serviceName + "/common.sh"));

        // inject custom topology loading
        commonScript = commonScript.replace(
                "function create_binary_wrapper(){",
                "function create_binary_wrapper(){\n" +
                        "return\n");

        FileUtils.writeFile(new File(jailPath + "/common.sh"), commonScript);


        // generate custom topology file
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();

        ServicesDefinition def = new ServicesDefinition();
        SetupService setupService = new SetupService();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
        def.setSetupService(setupService);
        def.afterPropertiesSet();

        ConfigurationService configurationService = new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        };

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        FileUtils.writeFile(new File (jailPath + "/eskimo-topology.sh"),
                topology.getTopologyScriptForNode(
                        nodesConfig, kubeServicesConfig, StandardSetupHelpers.getStandard2NodesInstallStatus(), new MemoryModel(new HashMap<>()), 1));
        ProcessHelper.exec(new String[]{"chmod", "755", jailPath + "/eskimo-topology.sh"}, true);

        String testFileConf = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getCamelCaseServiceName()+"SetupShellTest/testFile.conf"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(testFileConf)) {
            copyResource("testFile.conf", jailPath, testFileConf);
        }

        copyScripts(jailPath);

        enhanceScripts (jailPath);

        logger.debug ( executeScripts(jailPath));

        return jailPath;
    }

    protected abstract String getJailPath();

    private void enhanceScripts(String jailPath) throws FileException {

        for (String scriptToExecute: getScriptsToEnhance()) {

            enhanceScript(jailPath, scriptToExecute);
        }

    }

    protected void enhanceScript(String jailPath, String scriptToExecute) throws FileException {
        String scriptContent = FileUtils.readFile(new File(jailPath + "/" + scriptToExecute));
        if (StringUtils.isNotBlank(scriptContent)) {

            // inject overriding of path to use jail commands
            scriptContent = scriptContent.replace("#!/usr/bin/env bash\n", "" +
                    "#!/usr/bin/env bash\n" +
                    "\n" +
                    "SCRIPT_DIR=\"$( cd \"$( dirname \"${BASH_SOURCE[0]}\" )\" && pwd )\"\n" +
                    "\n" +
                    "# Change current folder to script dir (important !)\n" +
                    "cd $SCRIPT_DIR\n"+
                    "\n" +
                    "# Avoid sleeps everywhere\n" +
                    "export NO_SLEEP=true\n" +
                    "\n"+
                    "# Set test mode\n" +
                    "export TEST_MODE=true\n" +
                    "\n"+
                    "# Using local commands\n" +
                    "export PATH=$SCRIPT_DIR:$PATH\n");

            // inject custom topology loading
            scriptContent = scriptContent.replace(". /etc/eskimo_topology.sh", ". " + jailPath + "/eskimo-topology.sh");

            scriptContent = scriptContent.replace(". /host_etc/eskimo_topology.sh", ". " + jailPath + "/eskimo-topology.sh");

            scriptContent = scriptContent.replace("/etc/eskimo_mesos_environment", jailPath + "/eskimo_mesos_environment");

            copyResource(scriptToExecute, jailPath, scriptContent);
        }
    }

    protected abstract String getServiceName();

    protected String getImageName() {
        return getServiceName();
    }

    protected String getTemplateName() {
        return getServiceName();
    }

    protected abstract void copyScripts(String jailPath) throws IOException;

    protected abstract String[] getScriptsToExecute();

    protected String[][] getCustomScriptsToExecute() {
        return new String[][]{};
    }

    protected String[] getScriptsToEnhance() {
        List<String> scripts = new ArrayList<>(Arrays.asList(getScriptsToExecute()));
        Arrays.stream(getCustomScriptsToExecute())
                .map (cusScriptArray -> cusScriptArray[0])
                .forEach(scripts::add);
        return scripts.toArray(new String[0]);
    }

    protected final String executeScripts(String jailPath) throws ProcessHelper.ProcessHelperException {
        StringBuilder resultBuilder = new StringBuilder();
        for (String scriptToExecute: getScriptsToExecute()) {
            logger.info ("Executing " + scriptToExecute);
            resultBuilder.append(ProcessHelper.exec(new String[]{"bash", jailPath + "/" + scriptToExecute}, true));
        }
        for (String[] custScriptToExecute: getCustomScriptsToExecute()) {
            logger.info ("Executing " + custScriptToExecute[0]);
            List<String> args = new ArrayList<>(Arrays.asList("bash", jailPath + "/" + custScriptToExecute[0]));
            args.addAll(Arrays.asList(Arrays.copyOfRange(custScriptToExecute, 1, custScriptToExecute.length)));
            resultBuilder.append(ProcessHelper.exec(args.toArray(new String[0]), true));
        }
        return resultBuilder.toString();
    }

    /**
     * @return the path of the new jail where setup has to be executed
     */
    public static String createJail() throws Exception {
        File tempFile = File.createTempFile("eskimoshell_", "_test");
        tempFile.delete();
        tempFile.mkdir();

        // copy bash and everything bash requires to jail bin
        createLoggingExecutable("cp", tempFile.getAbsolutePath());
        createLoggingExecutable("gunzip", tempFile.getAbsolutePath());
        createLoggingExecutable("curl", tempFile.getAbsolutePath());
        createLoggingExecutable("mkdir", tempFile.getAbsolutePath());
        createLoggingExecutable("useradd", tempFile.getAbsolutePath());
        createLoggingExecutable("chown", tempFile.getAbsolutePath());
        createLoggingExecutable("chmod", tempFile.getAbsolutePath());
        createLoggingExecutable("curl", tempFile.getAbsolutePath());
        createLoggingExecutable("ln", tempFile.getAbsolutePath());
        createLoggingExecutable("rm", tempFile.getAbsolutePath());
        createLoggingExecutable("bash", tempFile.getAbsolutePath());
        createLoggingExecutable("systemctl", tempFile.getAbsolutePath());
        createLoggingExecutable("echo", tempFile.getAbsolutePath());
        createLoggingExecutable("touch", tempFile.getAbsolutePath());
        createLoggingExecutable("wget", tempFile.getAbsolutePath());
        createLoggingExecutable("mv", tempFile.getAbsolutePath());
        createLoggingExecutable("gluster_call_remote.sh", tempFile.getAbsolutePath());
        createLoggingExecutable("kubectl", tempFile.getAbsolutePath());
        createLoggingExecutable("envsubst", tempFile.getAbsolutePath());

        createDummyExecutable("id", tempFile.getAbsolutePath());
        createDummyExecutable("docker", tempFile.getAbsolutePath());
        createDummyExecutable("sed", tempFile.getAbsolutePath());
        createDummyExecutable("sudo", tempFile.getAbsolutePath());

        File var = new File (tempFile, "var"); var.mkdirs();
        File lib = new File (var, "lib"); var.mkdirs();
        File eskimo = new File (lib, "eskimo"); var.mkdirs();
        new File (eskimo, "kube-services"); var.mkdirs();

        return tempFile.getAbsolutePath();
    }

    protected static void createLoggingExecutable(String command, String targetDir) throws Exception {

        File targetPath = new File (targetDir + "/" + command);
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "echo \"$@\" >> .log_" + command + "\n");

        ProcessHelper.exec("chmod 755 " + targetPath, true);
    }

    private static void createDummyExecutable(String script, String targetDir) throws Exception {
        File targetPath = createResourceFile(script, targetDir);
        ProcessHelper.exec("chmod 755 " + targetPath, true);
    }

    private static File createResourceFile(String resourceFile, String targetDir) throws IOException, FileException {
        String resourceString = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("AbstractSetupShellTest/" + resourceFile), StandardCharsets.UTF_8);

        return copyResource(resourceFile, targetDir, resourceString);
    }

    private static File copyResource(String resourceFile, String targetDir, String resourceString) throws FileException {
        File targetPath = new File (targetDir + "/" + resourceFile);
        FileUtils.writeFile(targetPath, resourceString);

        return targetPath;
    }

    public String getCamelCaseServiceName() {
        return StringUtils.toCamelCase(getServiceName());
    }

    protected final void assertKubernetesCommands() throws IOException {
        String kubeCtlLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_kubectl"), StandardCharsets.UTF_8);
        System.err.println (kubeCtlLogs);

        if (StringUtils.isNotBlank(kubeCtlLogs)) {

            int indexOfDelete = kubeCtlLogs.indexOf("delete -f " + getServiceName() + ".k8s.yaml");
            assertTrue(indexOfDelete > -1);

            int indexOfApply = kubeCtlLogs.indexOf("apply -f -", indexOfDelete);
            assertTrue(indexOfApply > -1);
        } else {
            fail ("No kubectl manipulations found");
        }
    }

    protected final void assertSystemDInstallation() throws IOException {
        //System.err.println (setupLogs);

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_sudo"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(sudoLogs)) {

            //System.err.println(sudoLogs);
            assertTrue(sudoLogs.contains("cp " + getJailPath() + "/" + getServiceName() + ".service /lib/systemd/system/"));

            int indexOfDaemonReload = sudoLogs.indexOf("systemctl daemon-reload");
            assertTrue(indexOfDaemonReload > -1);

            int indexOfStatusFirst = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfDaemonReload + 1);
            assertTrue(indexOfStatusFirst > -1);

            int indexOfStatusSecond = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfStatusFirst + 1);
            assertTrue(indexOfStatusSecond > -1);

            int indexOfStart = sudoLogs.indexOf("systemctl start " + getServiceName(), indexOfStatusSecond + 1);
            assertTrue(indexOfStart > -1);

            int indexOfStatusThird = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfStart + 1);
            assertTrue(indexOfStatusThird > -1);

            int indexOfEnable = sudoLogs.indexOf("systemctl enable " + getServiceName(), indexOfStatusThird + 1);
            assertTrue(indexOfEnable > -1);


        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }

        String systemctlLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_systemctl"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(systemctlLogs)) {

            //System.err.println(systemctlLogs);

            assertTrue(systemctlLogs.contains("show -p SubState " + getServiceName()));

        } else {
            fail ("Expected to find systemctl logs in .log_systemctl");
        }
    }

    protected final void assertSystemDServiceDockerCommands() throws IOException {
        //System.err.println(setupLogs);
        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfImagesQ = dockerLogs.indexOf("images -q eskimo:" + getTemplateName() + "_template");
            assertTrue(indexOfImagesQ > -1);

            int indexOfLoad = dockerLogs.indexOf("load", indexOfImagesQ + 1);
            assertTrue(indexOfLoad > -1);

            int indexOfPs = dockerLogs.indexOf("ps -a -q -f name=" + getServiceName(), indexOfLoad + 1);
            assertTrue(indexOfPs > -1);

            int indexOfBuild = dockerLogs.indexOf("build --iidfile id_file --tag eskimo:" + getServiceName() + " .", indexOfPs + 1);
            assertTrue(indexOfBuild > -1);

            int indexOfCommit = dockerLogs.indexOf("commit " + getServiceName() + " eskimo:" + getServiceName(), indexOfBuild + 1);
            assertTrue(indexOfCommit > -1);

            int indexOfStop = dockerLogs.indexOf("stop " + getServiceName(), indexOfCommit + 1);
            assertTrue(indexOfStop > -1);

            int indexOfRm = dockerLogs.indexOf("container rm " + getServiceName(), indexOfStop + 1);
            assertTrue(indexOfRm > -1);

        } else {
            fail ("No docker manipulations found");
        }
    }

    protected final void assertKubernetesServiceDockerCommands() throws IOException {
        assertKubernetesServiceDockerCommands (getJailPath(), getImageName(), false);
    }

    protected final void assertKubernetesServiceDockerCommands(String currentFolder, String imageName, boolean simplified) throws IOException {
        //System.err.println(setupLogs);
        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(currentFolder + "/.log_docker"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(dockerLogs)) {

            System.err.println (dockerLogs);

            int indexOfImagesQ = dockerLogs.indexOf("images -q eskimo:" + getTemplateName() + "_template");
            assertTrue(indexOfImagesQ > -1);

            int indexOfLoad = dockerLogs.indexOf("load", indexOfImagesQ + 1);
            assertTrue(indexOfLoad > -1);

            int indexOfPs = dockerLogs.indexOf("ps -a -q -f name=" + imageName, indexOfLoad + 1);
            assertTrue(indexOfPs > -1);

            int indexOfBuild = dockerLogs.indexOf("build --iidfile id_file --tag eskimo:" + imageName + " .", indexOfPs + 1);
            assertTrue(indexOfBuild > -1);

            int indexOfRm = indexOfBuild;
            if (!simplified) {
                int indexOfCommit = dockerLogs.indexOf("commit " + imageName + " eskimo:" + imageName, indexOfBuild + 1);
                assertTrue(indexOfCommit > -1);

                int indexOfStop = dockerLogs.indexOf("stop " + imageName, indexOfCommit + 1);
                assertTrue(indexOfStop > -1);

                indexOfRm = dockerLogs.indexOf("container rm " + imageName, indexOfStop + 1);
                assertTrue(indexOfRm > -1);
            }

            int indexOfTag = dockerLogs.indexOf("tag eskimo:" + imageName + " kubernetes.registry:5000/" + imageName, indexOfRm + 1);
            assertTrue(indexOfTag > -1);

            int indexOfPush = dockerLogs.indexOf("push kubernetes.registry:5000/" + imageName, indexOfTag + 1);
            assertTrue(indexOfPush > -1);

            int indexOfImageRm = dockerLogs.indexOf("image rm eskimo:" + imageName, indexOfTag + 1);
            assertTrue(indexOfImageRm > -1);


        } else {
            fail ("No docker manipulations found");
        }
    }

    protected final void assertTestConfFileUpdate() throws Exception {
        String testFileConfResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getCamelCaseServiceName()+"SetupShellTest/testFile.conf.result"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(testFileConfResult)) {

            File updatedTestConfFilePath = new File (getJailPath() + "/testFile.conf");
            String updatedTestConfFile = FileUtils.readFile(updatedTestConfFilePath);

            assertEquals(testFileConfResult.trim(), updatedTestConfFile.trim());

        } else {
            fail ("file 'testFile.conf.result' is missing in " + getCamelCaseServiceName()+"SetupShellTest/");
        }
    }

}


