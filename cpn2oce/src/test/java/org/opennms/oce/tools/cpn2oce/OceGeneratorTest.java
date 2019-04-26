/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.tools.cpn2oce;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.opennms.alec.datasource.api.ResourceKey;
import org.opennms.alec.datasource.v1.schema.Alarm;
import org.opennms.alec.datasource.v1.schema.Alarms;
import org.opennms.alec.datasource.v1.schema.Inventory;
import org.opennms.alec.datasource.v1.schema.MetaModel;
import org.opennms.alec.datasource.v1.schema.Situations;
import org.opennms.oce.tools.cpn.view.StaticCpnDatasetViewer;

public class OceGeneratorTest {

    @Test
    public void canGenerateModelAlarmsAndSituationsForTicket() {
        // Ticket ID = 4015708
        StaticCpnDatasetViewer viewer = TestDataUtils.loadDataForTicket(4015708);
        OceGenerator oceGenerator = new OceGenerator.Builder()
                .withViewer(viewer)
                .build();
        oceGenerator.generate();

        // Ensure we have some meta-model
        MetaModel metaModel = oceGenerator.getMetaModel();
        assertThat(metaModel, notNullValue());

        // Inventory should contain
        // - root object
        // - 2 nodes (the switches)
        // - 2 ports
        // - 2 neighbors
        // - 1 link
        // = 8 objects
        Inventory inventory = oceGenerator.getInventory();
        assertThat(inventory.getModelObjectEntry(), hasSize(8));

        // A single situation
        Situations situations = oceGenerator.getSituations();
        assertThat(situations.getSituation(), hasSize(1));

        // 9 alarms
        Alarms alarms = oceGenerator.getAlarms();
        assertThat(alarms.getAlarm(), hasSize(9));

        Set<ResourceKey> alarmsAssociatedTo = new HashSet<>();
        for (Alarm alarm : alarms.getAlarm()) {
            alarmsAssociatedTo.add(ResourceKey.key(alarm.getInventoryObjectType(), alarm.getInventoryObjectId()));
        }
        assertThat(alarmsAssociatedTo, containsInAnyOrder(ResourceKey.key("SnmpInterface", "dc1-sw01: gigabitethernet2/0/27"),
                ResourceKey.key("Node", "dc1-sw01: gigabitethernet2/0/27: 10.1.1.85"),
                ResourceKey.key("SnmpInterfaceLink", "dc1-sw01: gigabitethernet2/0/27<->dc2-sw01: gigabitethernet2/0/27"),
                ResourceKey.key("SnmpInterface", "dc2-sw01: gigabitethernet2/0/27"),
                ResourceKey.key("Node", "dc2-sw01: gigabitethernet2/0/27: 10.1.1.86")));
    }

}
