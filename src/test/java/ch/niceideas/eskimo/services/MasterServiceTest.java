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

import ch.niceideas.eskimo.model.*;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MasterServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(MasterServiceTest.class);

    private MasterService ms = new MasterService();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());

        ms.setConfigurationService(configurationService);
        ms.setNodeRangeResolver(nodeRangeResolver);
        ms.setNotificationService(notificationService);
        ms.setServicesDefinition(servicesDefinition);
        ms.setSshCommandService(sshCommandService);
        ms.setSystemService(systemService);
    }

    @Override
    protected ConfigurationService createConfigurationService() {
        return new ConfigurationService() {
            @Override
            public NodesConfigWrapper loadNodesConfig() {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
            @Override
            public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() {
                return StandardSetupHelpers.getStandardKubernetesConfig();
            }
        };
    }

    @Override
    protected SystemService createSystemService() {
        return new SystemService(false) {
            @Override
            public SystemStatusWrapper getStatus() {
                return StandardSetupHelpers.getStandard2NodesSystemStatus();
            }
        };
    }

    @Test
    public void testUpdateStatus() throws Exception {

        assertEquals(0, ms.getServiceMasterNodes().size());
        assertEquals(0, ms.getServiceMasterTimestamps().size());

        ms.updateStatus();

        assertEquals(1, ms.getServiceMasterNodes().size());
        assertEquals(1, ms.getServiceMasterTimestamps().size());

        assertEquals("192.168.10.11", ms.getServiceMasterNodes().get("gluster"));
        assertNotNull(ms.getServiceMasterTimestamps().get("gluster"));
    }

    @Test
    public void testGetMasterStatus() throws Exception {

        assertEquals(0, ms.getServiceMasterNodes().size());
        assertEquals(0, ms.getServiceMasterTimestamps().size());

        // this sets a default master
        ms.getMasterStatus();

        assertEquals(1, ms.getServiceMasterNodes().size());
        assertEquals(1, ms.getServiceMasterTimestamps().size());

        assertEquals("192.168.10.11", ms.getServiceMasterNodes().get("gluster"));
        assertNotNull(ms.getServiceMasterTimestamps().get("gluster"));
    }

}
