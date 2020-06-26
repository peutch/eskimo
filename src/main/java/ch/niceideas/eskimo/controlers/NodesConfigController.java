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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.JSONOpCommand;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.HashMap;


@Controller
public class NodesConfigController {

    private static final Logger logger = Logger.getLogger(NodesConfigController.class);

    public static final String PENDING_OPERATIONS_STATUS_OVERRIDE = "PENDING_MARATHON_OPERATIONS_STATUS_OVERRIDE";
    public static final String PENDING_OPERATIONS_COMMAND = "PENDING_MARATHON_OPERATIONS_COMMAND";

    @Resource
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemService systemService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private NodesConfigurationChecker nodesConfigChecker;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Value("${eskimo.demoMode}")
    private boolean demoMode = false;

    /* For tests */
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }
    void setNodeRangeResolver(NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setNodesConfigChecker(NodesConfigurationChecker nodesConfigChecker) {
        this.nodesConfigChecker = nodesConfigChecker;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setNodesConfigurationService (NodesConfigurationService nodesConfigurationService) {
        this.nodesConfigurationService = nodesConfigurationService;
    }
    void setMessagingService(MessagingService messagingService) { this.messagingService = messagingService; }
    void setNotificationService (NotificationService notificationService) { this.notificationService = notificationService; }
    void setDemoMode (boolean demoMode) {
        this.demoMode = demoMode;
    }

    @GetMapping("/load-nodes-config")
    @ResponseBody
    public String loadNodesConfig() {
        try {
            setupService.ensureSetupCompleted();
            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
            if (nodesConfig == null || nodesConfig.isEmpty()) {
                return ErrorStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
            }
            return nodesConfig.getFormattedValue();

        } catch (SystemException | JSONException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e.getMessage());

        } catch (SetupException e) {
            // this is OK. means application is not yet initialized
            logger.debug (e, e);
            return ErrorStatusHelper.createClearStatus("setup", systemService.isProcessingPending());
        }
    }

    @PostMapping("/reinstall-nodes-config")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String reinstallNodesConfig(@RequestBody String reinstallModel, HttpSession session) {

        logger.info ("Got model : " + reinstallModel);

        try {

            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();
            ServicesInstallStatusWrapper newServicesInstallStatus = ServicesInstallStatusWrapper.empty();

            NodesConfigWrapper reinstallModelConfig = new NodesConfigWrapper(reinstallModel);

            for (String serviceInstallStatusFlag : servicesInstallStatus.getInstalledServicesFlags()) {

                String serviceInstallation = servicesInstallStatus.getServiceInstallation(serviceInstallStatusFlag);

                if (!reinstallModelConfig.hasServiceConfigured(serviceInstallation)) {
                    newServicesInstallStatus.copyFrom (serviceInstallStatusFlag, servicesInstallStatus);
                }
            }

            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
            if (nodesConfig == null || nodesConfig.isEmpty()) {
                return ErrorStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
            }

            // Create OperationsCommand
            OperationsCommand command = OperationsCommand.create(servicesDefinition, nodeRangeResolver, newServicesInstallStatus, nodesConfig);

            // store command and config in HTTP Session
            session.setAttribute(PENDING_OPERATIONS_STATUS_OVERRIDE, newServicesInstallStatus);
            session.setAttribute(PENDING_OPERATIONS_COMMAND, command);

            return returnCommand (command);

        } catch (JSONException | FileException | NodesConfigurationException | SetupException | SystemException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }

    static String returnCommand(JSONOpCommand<? extends Serializable> command) {
        try {
            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("command", command.toJSON());
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @PostMapping("/save-nodes-config")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String saveNodesConfig(@RequestBody String configAsString, HttpSession session) {

        logger.info ("Got config : " + configAsString);

        try {

            // first of all check nodes config
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(configAsString);
            nodesConfigChecker.checkNodesSetup(nodesConfig);

            ServicesInstallStatusWrapper serviceInstallStatus = configurationService.loadServicesInstallationStatus();

            // Create OperationsCommand
            OperationsCommand command = OperationsCommand.create(servicesDefinition, nodeRangeResolver, serviceInstallStatus, nodesConfig);

            // store command and config in HTTP Session
            session.removeAttribute(PENDING_OPERATIONS_STATUS_OVERRIDE);
            session.setAttribute(PENDING_OPERATIONS_COMMAND, command);

            return returnCommand (command);

        } catch (JSONException | SetupException | FileException | NodesConfigurationException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Nodes installation failed !");
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }

    @PostMapping("/apply-nodes-config")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String applyNodesConfig(HttpSession session) {

        try {

            if (systemService.isProcessingPending()) {

                String message = "Some backend operations are currently running. Please retry after they are completed.";

                messagingService.addLines (message);
                notificationService.addError("Operation In Progress");

                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("messages", message);
                }}).toString(2);
            }

            if (demoMode) {

                String message = "Unfortunately, re-applying nodes configuration or changing nodes configuration is not possible in DEMO mode.";

                messagingService.addLines (message);
                notificationService.addError("Demo Mode");

                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("messages", message);
                }}).toString(2);
            }

            OperationsCommand command = (OperationsCommand) session.getAttribute(PENDING_OPERATIONS_COMMAND);
            session.removeAttribute(PENDING_OPERATIONS_COMMAND);

            ServicesInstallStatusWrapper newServicesInstallationStatus = (ServicesInstallStatusWrapper) session.getAttribute(PENDING_OPERATIONS_STATUS_OVERRIDE);
            session.removeAttribute(PENDING_OPERATIONS_STATUS_OVERRIDE);

            // newNodesStatus is null in case of nodes config change (as opposed to forced reinstall)
            if (newServicesInstallationStatus != null) {
                configurationService.saveServicesInstallationStatus(newServicesInstallationStatus);
            }

            configurationService.saveNodesConfig(command.getRawConfig());

            nodesConfigurationService.applyNodesConfig(command);

            return "{\"status\": \"OK\" }";

        } catch (SystemException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createEncodedErrorStatus(e);

        } catch (JSONException | SetupException | FileException | NodesConfigurationException | ServiceDefinitionException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Nodes installation failed !");
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }
}
