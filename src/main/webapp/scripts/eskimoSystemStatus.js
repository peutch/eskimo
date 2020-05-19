/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software, 
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/

if (typeof eskimo === "undefined" || eskimo == null) {
    window.eskimo = {}
}
eskimo.SystemStatus = function(constructorObject) {

    // will be injected eventually from constructorObject
    this.eskimoNotifications = null;
    this.eskimoMessaging = null;
    this.eskimoNodesConfig = null;
    this.eskimoSetup = null;
    this.eskimoServices = null;
    this.eskimoMain = null;

    var that = this;

    // constants
    var STATUS_UPDATE_INTERVAL = 4000;

    // initialized by backend
    var STATUS_SERVICES = [];
    var SERVICES_STATUS_CONFIG = {};

    var renderInTable = true;
    var nodeFilter = "";

    var disconnectedFlag = true;

    var statusUpdateTimeoutHandler = null;

    var prevHidingMessageTimeout = null;

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-status").load("html/eskimoSystemStatus.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                loadUIStatusServicesConfig();

                $('#show-machine-view-btn').click($.proxy (function () {
                    setRenderInTable (false);
                    showStatus(true);
                }, this));

                $('#show-table-view-btn').click($.proxy (function () {
                    setRenderInTable (true);
                    showStatus(true);
                }, this));

                $('#show-all-nodes-btn').click($.proxy (function () {
                    $(".filter-btn").attr("class", "btn btn-default filter-btn");
                    setNodeFilter (null);
                    showStatus(true);
                }, this));

                $('#show-master-services-btn').click($.proxy (function () {
                    $(".filter-btn").attr("class", "btn btn-default filter-btn");
                    $("#show-master-services-btn").attr("class", "btn filter-btn btn-success");
                    setNodeFilter ("master");
                    showStatus(true);
                }, this));

                $('#show-issues-btn').click($.proxy (function () {
                    $(".filter-btn").attr("class", "btn btn-default filter-btn");
                    $("#show-issues-btn").attr("class", "btn filter-btn btn-success");
                    setNodeFilter ("issues");
                    showStatus(true);
                }, this));

                $('#empty-nodes-configure').click(function () {
                    that.eskimoNodesConfig.showNodesConfig();
                });

                // initialize menu
                var menuContent = '' +
                    '    <li><a id="show_journal" tabindex="-1" href="#" title="Show Journal"><i class="fa fa-file"></i> Show Journal</a></li>\n' +
                    '    <li class="divider"></li>'+
                    '    <li><a id="start" tabindex="-1" href="#" title="Start Service"><i class="fa fa-play"></i> Start Service</a></li>\n' +
                    '    <li><a id="stop" tabindex="-1" href="#" title="Stop Service"><i class="fa fa-stop"></i> Stop Service</a></li>\n' +
                    '    <li><a id="restart" tabindex="-1" href="#" title="Restart Service"><i class="fa fa-refresh"></i> Restart Service</a></li>\n' +
                    '    <li class="divider"></li>'+
                    '    <li><a id="reinstall" tabindex="-1" href="#" title="Reinstall Service"><i class="fa fa-undo"></i> Reinstall Service</a></li>\n';

                $("#serviceContextMenu").html(menuContent);

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });

        // register menu handler on services
        $.fn.serviceContextMenu = function (settings) {

            return this.each(function () {

                // Open context menu
                $(this).on("click", function (e) {

                    //open menu
                    var $menu = $(settings.menuSelector)
                        .data("invokedOn", $(e.target))
                        .show()
                        .css({
                            position: "absolute",
                            left: getMenuPosition(settings, e.clientX, 'width', 'scrollLeft') - $("#inner-content-status").offset().left,
                            top: getMenuPosition(settings, e.clientY, 'height', 'scrollTop') - $("#inner-content-status").offset().top
                        })
                        .off('click')
                        .on('click', 'a', function (evt) {
                            $menu.hide();

                            var $invokedOn = $menu.data("invokedOn");
                            var $selectedMenu = $(evt.target);

                            settings.menuSelected.call(this, $invokedOn, $selectedMenu);
                        });

                    return false;
                });

                //make sure menu closes on any click
                $('body').click(function () {
                    $(settings.menuSelector).hide();
                });
            });
        };

    };

    this.isDisconnected = function() {
        return disconnectedFlag;
    };

    function getMenuPosition(settings, mouse, direction, scrollDir) {
        var win = $("#inner-content-status")[direction](),
            scroll = $("#inner-content-status")[scrollDir](),
            menu = $(settings.menuSelector)[direction](),
            position = mouse + scroll;

        // opening menu would pass the side of the page
        if (mouse + menu > win && menu < mouse)
            position -= menu;

        return position;
    }

    function loadUIStatusServicesConfig() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-ui-services-status-config",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    SERVICES_STATUS_CONFIG = data.uiServicesStatusConfig;

                } else {
                    alert(data.error);
                }

                loadListServices();
            },
            error: errorHandler
        });
    }

    function loadListServices () {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "list-services",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    STATUS_SERVICES = data.services;

                    that.eskimoSetup.loadSetup(true);

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    function shouldRenderInTable () {
        return renderInTable;
    }
    this.shouldRenderInTable = shouldRenderInTable;

    function setRenderInTable (doTable) {
        renderInTable = doTable;
    }
    this.setRenderInTable = setRenderInTable;

    function setNodeFilter (doNodeFilter) {
        nodeFilter = doNodeFilter;
    }
    this.setNodeFilter = setNodeFilter;

    /** For tests */
    this.setStatusServices = function (statusServices) {
        STATUS_SERVICES = statusServices;
    };
    this.setServicesStatusConfig = function (servicesStatusConfig) {
        SERVICES_STATUS_CONFIG = servicesStatusConfig;
    };

    function showStatus (blocking) {

        if (!that.eskimoMain.isSetupLoaded()) {

            that.eskimoSetup.loadSetup();

            // retry after a ŵhile
            setTimeout (function() {
                showStatus(blocking);
            }, 100);

        } else {
            if (!that.eskimoMain.isSetupDone()) {

                that.eskimoMain.showSetupNotDone(blocking ? "" : "Cannot show nodes status as long as initial setup is not completed");

                // Still initialize the status update timeer (also used for notifications)
                updateStatus(false);

            } else {

                // maybe Progress bar was shown previously and we don't show it on status page
                that.eskimoMain.hideProgressbar();

                that.eskimoMain.showOnlyContent("status");

                updateStatus(blocking);
            }
        }
    }
    this.showStatus = showStatus;

    function showStatusMessage (message, error) {

        if (prevHidingMessageTimeout != null) {
            clearTimeout(prevHidingMessageTimeout);
        }

        var serviceStatusWarning = $("#service-status-warning");
        serviceStatusWarning.css("display", "block");
        serviceStatusWarning.css("visibility", "visible");

        $("#service-status-warning-message").html(message);

        if (error) {
            $("#service-status-warning-message").attr('class', "alert alert-danger");
        } else {
            $("#service-status-warning-message").attr('class', "alert alert-warning");
        }

        prevHidingMessageTimeout = setTimeout(function () {
            serviceStatusWarning.css("display", "none");
            serviceStatusWarning.css("visibility", "hidden");
        }, 5000);

    }
    this.showStatusMessage = showStatusMessage;

    function showStatusWhenServiceUnavailable (service) {
        showStatusMessage (service + " is not up and running");
    }
    this.showStatusWhenServiceUnavailable = showStatusWhenServiceUnavailable;

    function serviceAction (action, service, nodeAddress) {

        that.eskimoMessaging.showMessages();

        that.eskimoMain.startOperationInProgress();

        // 1 hour timeout
        $.ajax({
            type: "GET",
            dataType: "json",
            timeout: 1000 * 3600,
            contentType: "application/json; charset=utf-8",
            url: action + "?service=" + service + "&address=" + nodeAddress,
            success: function (data, status, jqXHR) {

                // OK
                console.log(data);

                if (!data || data.error) {
                    console.error(data.error);
                    that.eskimoMain.scheduleStopOperationInProgress (false);
                } else {
                    that.eskimoMain.scheduleStopOperationInProgress (true);

                    if (data.message != null) {
                        showStatusMessage (data.message);
                    }
                }
            },

            error: function (jqXHR, status) {
                errorHandler (jqXHR, status);
                that.eskimoMain.scheduleStopOperationInProgress (false);
            }
        });
    }

    function showJournal (service, nodeAddress) {
        console.log("showJournal", service, nodeAddress);

        serviceAction("show-journal", service, nodeAddress);
    }
    this.showJournal = showJournal;

    function startService (service, nodeAddress) {
        console.log("startService ", service, nodeAddress);

        serviceAction("start-service", service, nodeAddress);
    }
    this.startService = startService;

    function stopService (service, nodeAddress) {
        console.log("stoptService ", service, nodeAddress);

        serviceAction("stop-service", service, nodeAddress);
    }
    this.stopService = stopService;

    function restartService (service, nodeAddress) {
        console.log("restartService ", service, nodeAddress);

        serviceAction("restart-service", service, nodeAddress);
    }
    this.restartService = restartService;

    function reinstallService (service, nodeAddress) {
        console.log("reinstallService ", service, nodeAddress);
        if (confirm ("Are you sure you want to reinstall " + service + " on " + nodeAddress + " ?")) {
            serviceAction("reinstall-service", service, nodeAddress);
        }
    }
    this.reinstallService = reinstallService;

    this.serviceIsUp = function (nodeServicesStatus, service) {
        var serviceAvailable = false;
        for (var key in nodeServicesStatus) {
            if (key.indexOf("service_"+service+"_") > -1) {
                var serviceStatus = nodeServicesStatus[key];
                if (serviceStatus == "OK") {
                    serviceAvailable = true;
                    break;
                }
            }
        }
        return serviceAvailable;
    };

    this.displayMonitoringDashboard = function (monitoringDashboardId, refreshPeriod) {

        $.ajax({
            type: "GET",
            url: "grafana/api/dashboards/uid/" +monitoringDashboardId,
            success: function (data, status, jqXHR) {

                var forceRefresh = false;
                if ($("#status-monitoring-dashboard-frame").css("display") == "none") {


                    setTimeout (function() {
                        $("#status-monitoring-dashboard-frame").css("display", "inherit");
                        $("#status-monitoring-no-dashboard").css("display", "none");
                    }, 500);

                    forceRefresh = true;
                }

                var url = "grafana/d/" + monitoringDashboardId + "/eskimo-system-wide-monitoring?orgId=1&&kiosk&refresh="
                    + (refreshPeriod == null || refreshPeriod == "" ? "30s" : refreshPeriod);

                var prevUrl = $("#status-monitoring-dashboard-frame").attr('src');
                if (prevUrl == null || prevUrl == "" || prevUrl != url || forceRefresh) {
                    $("#status-monitoring-dashboard-frame").attr('src', url);

                    setTimeout(that.monitoringDashboardFrameTamper, 4000);
                }
            },
            error: function (jqXHR, status) {

                // ignore
                console.debug("error : could not fetch dashboard " + monitoringDashboardId);

                // mention the fact that dashboard does not exist
                $('#status-monitoring-no-dashboard').html("<b>Grafana doesn't know dashboard with ID " + monitoringDashboardId + "</b>");

                // retry periodically
                setTimeout(function () {
                    that.displayMonitoringDashboard(monitoringDashboardId, refreshPeriod);
                }, 5000);
            }
        });

    };

    this.handleSystemStatus = function (nodeServicesStatus, systemStatus, blocking) {

        // A. Handle Grafana Dashboard ID display

        // A.1 Find out if grafana is available
        var grafanaAvailable = this.serviceIsUp (nodeServicesStatus, "grafana");

        var monitoringDashboardId = systemStatus.monitoringDashboardId;

        // no dashboard configured
        if (   !grafanaAvailable
            || monitoringDashboardId == null
            || monitoringDashboardId == ""
            || monitoringDashboardId == "null"
            // or service grafana not yet available
            || !that.eskimoServices.isServiceAvailable("grafana")
            ) {

            $("#status-monitoring-no-dashboard").css("display", "inherit");
            $("#status-monitoring-dashboard-frame").css("display", "none");

            $("#status-monitoring-dashboard-frame").attr('src', "html/emptyPage.html");

        }
        // render iframe with refresh period (default 30s)
        else {

            var refreshPeriod = systemStatus.monitoringDashboardRefreshPeriod;

            setTimeout(function () {
                that.displayMonitoringDashboard(monitoringDashboardId, refreshPeriod);
            }, 5000);
        }

        // B. Inject information

        $("#eskimo-flavour").html()

        $("#system-information-version").html(systemStatus.buildVersion);

        $("#system-information-timestamp").html(systemStatus.buildTimestamp);

        $("#system-information-user").html(systemStatus.sshUsername);

        $("#system-information-start-timestamp").html (systemStatus.startTimestamp);

        // C. Cluster nodes and services
        var nodesWithproblem = [];
        for (var key in nodeServicesStatus) {
            if (key.indexOf("node_alive_") > -1) {
                var nodeName = key.substring("node_alive_".length);
                var nodeAlive = nodeServicesStatus[key];
                if (nodeAlive != "OK") {
                    nodesWithproblem.push(nodeName.replace(/-/g, "."));
                }
            }
        }

        if (nodesWithproblem.length == 0) {
            $("#system-information-nodes-status").html("<span style='color: darkgreen;'>OK</span>");
        } else {
            $("#system-information-nodes-status").html(
                "Following nodes are reporting problems : <span style='color: darkred;'>" +
                nodesWithproblem.join(", ") +
                "</span>");
        }

        // find out about services status
        var servicesWithproblem = [];
        for (var key in nodeServicesStatus) {
            if (key.indexOf("service_") > -1) {
                var serviceName = key.substring("service_".length, key.indexOf("_", "service_".length));
                var serviceAlive = nodeServicesStatus[key];
                if (serviceAlive != "OK") {
                    if (servicesWithproblem.length <= 0 || !servicesWithproblem.includes(serviceName)) {
                        servicesWithproblem.push(serviceName);
                    }
                }
            }
        }

        if (servicesWithproblem.length == 0) {
            if (nodesWithproblem.length == 0) {
                $("#system-information-services-status").html("<span style='color: darkgreen;'>OK</span>");
            } else {
                $("#system-information-services-status").html("<span style='color: darkred;'>-</span>");
            }
        } else {
            $("#system-information-services-status").html("Following services are reporting problems : " +
                "<span style='color: darkred;'>" +
                servicesWithproblem.join(", ") +
                "</span>");
        }

        // C. System Information Actions

        var systemInformationActions = '';

        if (systemStatus.links && systemStatus.links.length && systemStatus.links.length > 0) {
            for (var i = 0; i < systemStatus.links.length; i++) {

                var link = systemStatus.links[i];

                if (that.eskimoServices.isServiceAvailable(link.service)
                    && this.serviceIsUp (nodeServicesStatus, link.service)) {
                    systemInformationActions += '' +
                        '<a href="javascript:eskimoMain.getServices().showServiceIFrame(\''+link.service+'\');">' +
                        '<table class=".status-monitoring-action-table">' +
                        '<tr>' +
                        '<td>' +
                        '<img class="control-logo-logo" src="images/'+link.service+'-logo.png"/>' +
                        '</td><td>&nbsp;' +
                        link.title +
                        '</td>' +
                        '</tr>' +
                        '</table>' +
                        '</a>';
                }
            }
        }

        $("#system-information-actions").html(systemInformationActions);

        // D. General configuration

        that.eskimoMain.handleMarathonSubsystem (systemStatus.enableMarathon);

        that.eskimoSetup.setSnapshot(systemStatus.isSnapshot);
    };

    this.monitoringDashboardFrameTamper = function() {
        // remove widgets menus from iframe DOM
        $("#status-monitoring-dashboard-frame").contents().find(".panel-menu").remove();
        setTimeout (that.monitoringDashboardFrameTamper, 10000);
    };

    this.renderNodesStatus = function (nodeServicesStatus, blocking) {

        var nodeNamesByNbr = [];

        that.eskimoMain.handleSetupCompleted();

        var availableNodes = [];

        // loop on node nbrs and get Node Name + create table row
        for (var key in nodeServicesStatus) {
            if (key.indexOf("node_nbr_") > -1) {
                var nodeName = key.substring("node_nbr_".length);
                var nbr = nodeServicesStatus[key];
                nodeNamesByNbr [parseInt(nbr)] = nodeName;
            }
        }

        for (var nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            var nodeName = nodeNamesByNbr[nbr];

            var nodeAddress = nodeServicesStatus["node_address_" + nodeName];
            var nodeAlive = nodeServicesStatus["node_alive_" + nodeName];

            // if at least one node is up, show the consoles menu
            if (nodeAlive == 'OK') {

                // Show SFTP and Terminal Menu entries
                $("#folderMenuConsoles").attr("class", "folder-menu-items");
                $("#folderMenuFileManagers").attr("class", "folder-menu-items");

                availableNodes.push({"nbr": nbr, "nodeName": nodeName, "nodeAddress": nodeAddress});
            }

            for (var sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {
                var service = STATUS_SERVICES[sNb];
                if (nodeAlive == 'OK') {

                    var serviceStatus = nodeServicesStatus["service_" + service + "_" + nodeName];

                    if (serviceStatus) {

                        if (serviceStatus == "NA" || serviceStatus == "KO") {

                            that.eskimoServices.serviceMenuServiceFoundHook(nodeName, nodeAddress, service, false, blocking);

                        } else if (serviceStatus == "OK") {

                            that.eskimoServices.serviceMenuServiceFoundHook(nodeName, nodeAddress, service, true, blocking);
                        }
                    }
                }
            }
        }

        if (nodeNamesByNbr.length == 0) {

            this.renderNodesStatusEmpty();

        } else {

            if (this.shouldRenderInTable()) {

                this.renderNodesStatusTable(nodeServicesStatus, blocking, availableNodes, nodeNamesByNbr);

            } else {

                this.renderNodesStatusCarousel(nodeServicesStatus, blocking, availableNodes, nodeNamesByNbr);
            }
        }

        that.eskimoMain.setAvailableNodes(availableNodes);
    };

    this.renderNodesStatusEmpty = function() {

        var statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        var statusContainerEmpty = $("#status-node-container-empty");
        statusContainerEmpty.css("visibility", "inherit");
        statusContainerEmpty.css("display", "inherit");
    };

    function registerMenu(selector, dataSelector) {
        // register menu
        $(selector).serviceContextMenu({
            menuSelector: "#serviceContextMenu",
            menuSelected: function (invokedOn, selectedMenu) {

                var action = selectedMenu.attr('id');
                var nodeAddress = $(invokedOn).closest("td."+dataSelector).data('eskimo-node');
                var service = $(invokedOn).closest("td."+dataSelector).data('eskimo-service');

                if (action == "show_journal") {
                    showJournal(service, nodeAddress);

                } else if (action == "start") {
                    startService(service, nodeAddress);

                } else if (action == "stop") {
                    stopService(service, nodeAddress);

                } else if (action == "restart") {
                    restartService(service, nodeAddress);

                } else if (action == "reinstall") {
                    reinstallService(service, nodeAddress);

                } else {
                    alert("Unknown action : " + action);
                }
            }
        })
    }
    this.registerMenu = registerMenu;

    this.renderNodesStatusCarousel = function (data, blocking, availableNodes, nodeNamesByNbr) {

        var statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        var statusContainerCarousel = $("#status-node-container-carousel");
        statusContainerCarousel.css("visibility", "inherit");
        statusContainerCarousel.css("display", "inherit");

        var carouselContent = $("#nodes-status-carousel-content");
        carouselContent.html("");

        for (var nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            var nodeName = nodeNamesByNbr[nbr];

            var nodeAddress = data["node_address_" + nodeName];
            var nodeAlive = data["node_alive_" + nodeName];

            var arrayRow = ' ' +
                '<div class="col-lg-3 col-md-4 col-sm-6 col-xs-12 status-node-carousel" >\n' +
                '    <div class="pad15"><div class="status-node-node-rep">\n';

            if (nodeAlive == 'OK') {
                arrayRow += '<div class="text-center"><p><image src="images/node-icon-white.png" class="status-node-image"></image></p></div>\n';
            } else {
                arrayRow += '<div class="text-center"><p><image src="images/node-icon-red.png" class="status-node-image"></image></p></div>\n';
            }

            arrayRow += '   <div class="text-center"> <p>' + nbr + ' : ' + nodeAddress + '</p></div>\n'

            arrayRow += '    <p>\n';

            for (var sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {
                var service = STATUS_SERVICES[sNb];
                if (nodeAlive == 'OK') {

                    var serviceStatus = data["service_" + service + "_" + nodeName];
                    //console.log ("For service '" + service + "' on node '" + nodeName + "' got '"+ serviceStatus + "'");
                    if (!serviceStatus) {

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td><span class="font-weight-bold">&nbsp;</span></td>\n' +
                            '        </tr></tbody></table>';

                    } else if (serviceStatus == "NA") {

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td data-eskimo-node="'+nodeAddress+'" data-eskimo-service="'+service+'" ' +
                            '            class="nodes-status-carousel-status-na">' +
                            '            <span class="font-weight-bold service-status-error '+
                            '        '+(that.eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        ">' +
                            service +
                            '        </span></td>\n' +
                            '        </tr>' +
                            '</tbody></table>';

                    } else if (serviceStatus == "KO") {

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td data-eskimo-node="'+nodeAddress+'" data-eskimo-service="'+service+'" ' +
                            '            class="nodes-status-carousel-status'+(that.eskimoMain.isOperationInProgress() ? '-pending': '') +'">' +
                            '             <span class="font-weight-bold service-status-error '+
                            '        '+(that.eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        ">' +
                            service +
                            '        </span></td>\n' +
                            '    </tr>\n' +
                            '</tbody></table>\n';

                    } else {

                        var color = "#EEEEEE;";
                        if (serviceStatus == "TD") {
                            color = "violet";
                        } else if (serviceStatus == "restart") {
                            color= "#CB4335"
                        }

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td data-eskimo-node="'+nodeAddress+'" data-eskimo-service="'+service+'" ' +
                            '            class="nodes-status-carousel-status'+(that.eskimoMain.isOperationInProgress() ? '-pending': '') +'"><span class="font-weight-bold '+
                            '        '+(that.eskimoMain.isOperationInProgress() && color == "violet" ? 'blinking-status' : '') +
                            '         " style="color: '+color+';">' +
                            '            <div class="status-service-icon">' +
                            '                <img class="status-service-icon-image" src="' + that.eskimoNodesConfig.getServiceIconPath(service) + '"/> ' +
                            '            </div>' +
                            '            <div class="status-service-text">' +
                            '&nbsp;' + service +
                            '            </div>' +
                            '        </span></td>\n' +
                            '    </tr>\n' +
                            '</tbody></table>\n';

                    }
                } else {
                    arrayRow +=
                        '<table class="node-status-carousel-table">\n' +
                        '    <tbody><tr>\n' +
                        '        <td><span class="font-weight-bold">-</span></td>\n' +
                        '        <td class="nodes-status-carousel-actions">'+
                        '</td></tr></tbody></table>';
                }
            }

            arrayRow += '</p></div></div>';

            var newRow = $(arrayRow);

            carouselContent.append(newRow);
        }

        registerMenu("#nodes-status-carousel-content td.nodes-status-carousel-status", "nodes-status-carousel-status");

    };

    this.generateTableHeader = function() {

        var tableHeaderHtml = ''+
            '<tr id="header_1" class="status-node-table-header">\n'+
            '<td class="status-node-cell" rowspan="2">Status</td>\n' +
            '<td class="status-node-cell" rowspan="2">No</td>\n' +
            '<td class="status-node-cell" rowspan="2">IP Address</td>\n';

        // Phase 1 : render first row
        var prevGroup = null;
        for (var i = 0; i < STATUS_SERVICES.length; i++) {

            var serviceName = STATUS_SERVICES[i];
            var serviceStatusConfig = SERVICES_STATUS_CONFIG[serviceName];

            if (serviceStatusConfig.group != null && serviceStatusConfig.group != "") {

                if (prevGroup == null || serviceStatusConfig.group != prevGroup) {

                    // first need to know size of group
                    var sizeOfGroup = 1;
                    for (var j = i + 1; j < STATUS_SERVICES.length; j++) {
                        var nextGroup = SERVICES_STATUS_CONFIG[STATUS_SERVICES[j]].group;
                        if (nextGroup != null && nextGroup == serviceStatusConfig.group) {
                            sizeOfGroup++;
                        } else {
                            break;
                        }
                    }

                    tableHeaderHtml +=
                            '<td class="status-node-cell" colspan="' + sizeOfGroup + '">' + serviceStatusConfig.group + '</td>\n';

                    prevGroup = serviceStatusConfig.group;
                }
            } else {

                tableHeaderHtml +=
                    '<td class="status-node-cell" rowspan="2">' +
                    //'   <img class="control-logo-logo" src="' + that.eskimoNodesConfig.getServiceLogoPath(serviceName) +
                    //'   "/><br>' +
                    serviceStatusConfig.name +
                    '</td>\n';
            }
        }

        tableHeaderHtml +=
                '</tr>\n' +
                '<tr id="header_2" class="status-node-table-header">\n';

        // Phase 2 : render second row
        for (var i = 0; i < STATUS_SERVICES.length; i++) {

            var serviceName = STATUS_SERVICES[i];
            var serviceStatusConfig = SERVICES_STATUS_CONFIG[serviceName];

            if (serviceStatusConfig.group && serviceStatusConfig.group != null && serviceStatusConfig.group != "") {
                tableHeaderHtml = tableHeaderHtml +
                    '<td class="status-node-cell">' +
                    //'   <img class="control-logo-logo" src="' + that.eskimoNodesConfig.getServiceLogoPath(serviceName) +
                    //'   "/><br>' +
                    serviceStatusConfig.name + '</td>\n';
            }
        }

        tableHeaderHtml += "</tr>";

        return tableHeaderHtml;
    };

    this.renderNodesStatusTable = function (data, blocking, availableNodes, nodeNamesByNbr) {

        var statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        var statucContainerTable = $("#status-node-container-table");
        statucContainerTable.css("visibility", "inherit");
        statucContainerTable.css("display", "inherit");

        // clear table
        $("#status-node-table-head").html(this.generateTableHeader());

        var statusContainerTableBody = $("#status-node-table-body");
        statusContainerTableBody.html("");

        for (var nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            var nodeHasIssues = false;
            var nodeHasMasters = false;

            var nodeName = nodeNamesByNbr[nbr];

            var nodeAddress = data["node_address_" + nodeName];
            var nodeAlive = data["node_alive_" + nodeName];

            var arrayRow = ' ' +
                '<tr id="' + nodeName + '">\n' +
                '    <td class="status-node-cell-intro">\n';

            if (nodeAlive == 'OK') {
                arrayRow +=
                    '        <image src="images/node-icon.png" class="status-node-image"></image>\n';
            } else {
                arrayRow +=
                    '        <image src="images/node-icon-red.png" class="status-node-image"></image>\n';
                nodeHasIssues = true;
            }

            arrayRow +=
                '    </td>\n' +
                '    <td class="status-node-cell-intro">' + nbr + '</td>\n' +
                '    <td class="status-node-cell-intro">' + nodeAddress + '</td>\n';

            for (var sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {

                var service = STATUS_SERVICES[sNb];

                if (nodeAlive == 'OK') {

                    var serviceStatus = data["service_" + service + "_" + nodeName];
                    //console.log ("For service '" + service + "' on node '" + nodeName + "' got '"+ serviceStatus + "'");
                    if (!serviceStatus) {

                        arrayRow += '    <td class="status-node-cell-empty"></td>\n'

                    } else if (serviceStatus == "NA") {

                        if (that.eskimoNodesConfig.isServiceUnique(service)) {
                            nodeHasMasters = true;
                        }

                        arrayRow +=
                            '    <td class="status-node-cell-empty"><span class="service-status-error '+
                            '        '+(that.eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '      ">NA</span></td>\n';
                        nodeHasIssues = true;

                    } else if (serviceStatus == "KO") {

                        if (that.eskimoNodesConfig.isServiceUnique(service)) {
                            nodeHasMasters = true;
                        }

                        arrayRow +=
                            '    <td class="status-node-cell'+(that.eskimoMain.isOperationInProgress() ? "-empty": "")+'"' +
                            '         data-eskimo-node="'+nodeAddress+'" data-eskimo-service="'+service+'" \'>' +
                            '<span class="service-status-error">\n' +
                            '<table class="node-status-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td colspan="5" class="nodes-status-status"><span class="font-weight-bold ' +
                            '        '+(that.eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        ">KO</span></td>\n' +
                            '    </tr>\n' +
                            '</tbody></table>\n' +
                            '\n' +
                            '</span>' +
                            '</td>\n';
                        nodeHasIssues = true;

                    } else {

                        if (that.eskimoNodesConfig.isServiceUnique(service)) {
                            nodeHasMasters = true;
                        }

                        var color = "darkgreen";
                        if (serviceStatus == "TD") {
                            color = "violet";
                        } else if (serviceStatus == "restart") {
                            color = "#CB4335";
                            nodeHasIssues = true;
                        }

                        arrayRow +=
                            '    <td class="status-node-cell'+(that.eskimoMain.isOperationInProgress() ? "-empty": "")+'"' +
                            '         data-eskimo-node="'+nodeAddress+'" data-eskimo-service="'+service+'" \'>' +
                            '<span style="color: '+color+';">\n' +
                            '<table class="node-status-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td colspan="5" class="nodes-status-status"><span class="font-weight-bold '+
                            '        '+(that.eskimoMain.isOperationInProgress() && color == "violet" ? 'blinking-status' : '') +
                            '        ">OK</span></td>\n' +
                            '    </tr>\n' +
                            '</tbody></table>\n' +
                            '\n' +
                            '</span>' +
                            '</td>\n'

                    }
                } else {
                    arrayRow += '    <td class="status-node-cell-empty">-</td>\n'
                }
            }

            arrayRow += '</tr>';

            var newRow = $(arrayRow);

            // filtering
            if (   (!nodeFilter || nodeFilter == null)
                ||
                   ((nodeFilter == "master") && nodeHasMasters)
                ||
                   ((nodeFilter == "issues") && nodeHasIssues)) {
                statusContainerTableBody.append(newRow);
            }
        }

        registerMenu("#status-node-table-body td.status-node-cell", "status-node-cell");
    };

    this.fetchOperationResult = function() {
        $.ajax({
            type: "GET",
            dataType: "json",
            url: "get-last-operation-result",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {
                    that.eskimoMain.scheduleStopOperationInProgress (data.success);
                } else {
                    alert (data.error);
                }
            },
            error: errorHandler
        });
    };

    var inUpdateStatus = false;
    function updateStatus(blocking) {

        if (inUpdateStatus) {
            return;
        }
        inUpdateStatus = true;

        // cancel previous timer. update status will be rescheduled at the end of this method
        if (statusUpdateTimeoutHandler != null) {
            clearTimeout(statusUpdateTimeoutHandler);
        }

        if (blocking) {
            that.eskimoMain.showProgressbar();
        }

        $.ajax({
            type: "GET",
            dataType: "json",
            context: that,
            url: "get-status",
            success: function (data, status, jqXHR) {

                disconnectedFlag = false;

                that.eskimoMain.serviceMenuClear(data.nodeServicesStatus);

                //console.log (data);

                if (!data.clear) {

                    this.handleSystemStatus(data.nodeServicesStatus, data.systemStatus, blocking);

                    this.renderNodesStatus (data.nodeServicesStatus, blocking);

                } else if (data.clear == "setup"){

                    that.eskimoMain.handleSetupNotCompleted();

                    if (   !that.eskimoMain.isCurrentDisplayedService("setup")
                        && !that.eskimoMain.isCurrentDisplayedService("pending")) {
                        that.eskimoMain.showSetupNotDone();
                    }

                } else if (data.clear == "nodes"){

                    this.renderNodesStatusEmpty();
                }

                if (data.processingPending) {  // if backend says there is some processing going on
                    that.eskimoMain.recoverOperationInProgress();

                } else {                         // if backend says there is nothing going on
                    if (that.eskimoMain.isOperationInProgress()  // but frontend still things there is ...
                            && that.eskimoMain.isOperationInProgressOwner()) {  // ... and if that is my fault
                        this.fetchOperationResult();
                    }
                }

                if (blocking) {
                    that.eskimoMain.hideProgressbar();
                }

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            },

            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log(status);

                if (jqXHR.status == "401") {
                    window.location = "login.html";
                }

                if (blocking) {
                    alert('fail : ' + status);

                    that.eskimoMain.hideProgressbar();

                } else {

                    showStatusMessage("Couldn't fetch latest status from Eskimo Backend. Shown status is the latest known status. ", true)
                }

                disconnectedFlag = true;

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            }
        });

        // use same timer to fetch notifications
        that.eskimoNotifications.fetchNotifications();

        // show a message on status page if there is some operations in progress pending
        if (that.eskimoMain.isOperationInProgress()) {
            showStatusMessage("Pending operations in progress on backend. See 'Backend Messages' for more information.");
        }
    }
    this.updateStatus = updateStatus;


    // inject constructor object in the end
    if (constructorObject != null) {
        $.extend(this, constructorObject);
    }

    // call constructor
    this.initialize();
};