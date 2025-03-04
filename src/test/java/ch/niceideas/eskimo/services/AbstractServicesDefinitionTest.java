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
import ch.niceideas.eskimo.model.service.Dependency;
import ch.niceideas.eskimo.model.service.MasterElectionStrategy;
import ch.niceideas.eskimo.model.service.MemoryConsumptionSize;
import ch.niceideas.eskimo.model.service.Service;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractServicesDefinitionTest {

    protected SetupService setupService = new SetupService();

    protected ServicesDefinition def;

    protected NodeRangeResolver nrr = new NodeRangeResolver();

    @BeforeEach
    public void setUp() throws Exception {
        def = new ServicesDefinition();
        def.setSetupService (setupService);
        setupService.setServicesDefinition(def);
        def.afterPropertiesSet();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    public void initFirstNodeDependencies() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.FIRST_NODE);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.FIRST_NODE);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(2);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initConditionalDependency() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.FIRST_NODE);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.FIRST_NODE);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(1);
        depB.setConditional("service_d");
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");
        def.addService(serviceD);
    }

    public void initConditionalMandatory() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        serviceA.setMandatory(true);
        serviceA.setConditional(ConditionalInstallation.MULTIPLE_NODES);
        def.addService(serviceA);

    }

    public void initSameNodeOrRandomDependencies() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.SAME_NODE_OR_RANDOM);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.SAME_NODE_OR_RANDOM);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(1);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomDependencies() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(2);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(2);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomDependenciesFewer() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(2);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(2);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomNodeAfterOrSameDependencies() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(1);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.RANDOM);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomNodeAfterDependencies() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depB.setMasterService("service_c");
        depB.setNumberOfMasters(1);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.RANDOM);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initAdditionalEnvironment() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        serviceA.addAdditionalEnvironment("SERVICE_NUMBER_0_BASED");
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        serviceC.addAdditionalEnvironment("SERVICE_NUMBER_1_BASED");
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");
        serviceD.setKubernetes(true);
        def.addService(serviceD);
    }

    public void initAdditionalNodeList() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        serviceA.addAdditionalEnvironment("ALL_NODES_LIST_service_a");
        serviceA.setMemoryConsumptionSize(MemoryConsumptionSize.LARGE);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        serviceB.setMemoryConsumptionSize(MemoryConsumptionSize.MEDIUM);
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        serviceC.addAdditionalEnvironment("ALL_NODES_LIST_service_b");
        serviceC.setMemoryConsumptionSize(MemoryConsumptionSize.NEGLIGIBLE);
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }


    public void initAdditionalNodeListWithAdditionalMemory() {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        serviceA.addAdditionalEnvironment("ALL_NODES_LIST_service_a");
        serviceA.setMemoryConsumptionSize(MemoryConsumptionSize.LARGE);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        serviceB.setMemoryConsumptionSize(MemoryConsumptionSize.MEDIUM);
        serviceB.addAdditionalMemory("service_c");
        def.addService(serviceB);

        Service serviceC = new Service();
        serviceC.setName("service_c");
        serviceC.addAdditionalEnvironment("ALL_NODES_LIST_service_b");
        serviceC.setMemoryConsumptionSize(MemoryConsumptionSize.NEGLIGIBLE);
        def.addService(serviceC);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService("service_c");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }
}