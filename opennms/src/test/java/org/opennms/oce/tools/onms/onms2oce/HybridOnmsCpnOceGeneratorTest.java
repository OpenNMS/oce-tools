/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.oce.tools.onms.onms2oce;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import org.junit.Test;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.cpn.view.StaticCpnDatasetViewer;
import org.opennms.oce.tools.svc.NodeAndFactsService;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.SituationsAlarmsAndEvents;

public class HybridOnmsCpnOceGeneratorTest {

    @Test
    public void canDoIt() {
        // Mock all the data for ticket #4743230
        StaticCpnDatasetViewer viewer = CpnTestDataUtils.loadDataForTicket(4743230);
        NodeAndFactsService nodeAndFactsService = mock(NodeAndFactsService.class);

        NodeAndFacts nodeAndFacts = new NodeAndFacts("sw1");
        nodeAndFacts.setOpennmsNodeLabel("sw1.opennms.org");
        nodeAndFacts.setOpennmsNodeId(1);
        assertThat(nodeAndFacts.shouldProcess(), equalTo(true)); // ensure the node is marked for processing

        when(nodeAndFactsService.getNodesAndFacts(anySetOf(String.class), any(), any())).thenReturn(Collections.singletonList(nodeAndFacts));

        NodeAndEvents nodeAndEvents = mock(NodeAndEvents.class);
        when(nodeAndFactsService.retrieveAndPairEvents(nodeAndFacts)).thenReturn(nodeAndEvents);

        SituationsAlarmsAndEvents situationsAlarmsAndEvents = mock(SituationsAlarmsAndEvents.class);
        when(nodeAndFactsService.getSituationsAlarmsAndEvents(nodeAndEvents)).thenReturn(situationsAlarmsAndEvents);

        HybridOnmsCpnOceGenerator oceGenerator = new HybridOnmsCpnOceGenerator.Builder()
                .withViewer(viewer)
                .withNodeAndFactsService(nodeAndFactsService)
                .build();
        oceGenerator.generate();

        // Given a simple ticket in CPN with 2 alarms, each having 2 events
        //   linkDown (syslog, trap)


        // And corresponding alarms in OpenNMS

        // Invoke the generator
        FaultDataset faultDataset = generateDataset();


        // We should have a situation
        assertThat(faultDataset.getSituations().getSituation(), hasSize(1));
    }

    private FaultDataset generateDataset() {
        final Situations situations = new Situations();
        final Alarms alarms = new Alarms();
        final MetaModel metaModel = new MetaModel();
        final Inventory inventory = new Inventory();
        return new FaultDataset(situations, alarms, metaModel, inventory);
    }

    private static class FaultDataset {
        private final Situations situations;
        private final Alarms alarms;
        private final MetaModel metaModel;
        private final Inventory inventory;

        public FaultDataset(Situations situations, Alarms alarms, MetaModel metaModel, Inventory inventory) {
            this.situations = Objects.requireNonNull(situations);
            this.alarms = Objects.requireNonNull(alarms);
            this.metaModel = Objects.requireNonNull(metaModel);
            this.inventory = Objects.requireNonNull(inventory);
        }

        public Situations getSituations() {
            return situations;
        }

        public Alarms getAlarms() {
            return alarms;
        }

        public MetaModel getMetaModel() {
            return metaModel;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }
}
