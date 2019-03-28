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

import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.opennms.oce.opennms.model.ManagedObjectType;
import org.opennms.oce.tools.cpn.events.EventRecordLite;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetView;
import org.opennms.oce.tools.cpn.view.StaticCpnDatasetViewer;
import org.opennms.oce.tools.cpn2oce.model.EventDefinition;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;

import com.google.common.collect.Sets;

public class EventMapperTest {

    @Test
    public void canMapSyslogsInTicket() {
        // Load data from the ticket #4015708, and ensure we map the events to SNMP interface links
        StaticCpnDatasetViewer viewer = TestDataUtils.loadDataForTicket(4015708, new CpnDatasetView.Builder()
                .withEventTypes(Sets.newHashSet(CpnDatasetView.EventType.SYSLOG))
                .build());
        List<EventRecord> serviceEvents = new LinkedList<>();
        viewer.getEventsInTicket("4015708", serviceEvents::addAll);
        assertThat(serviceEvents, hasSize(28));
        for (EventRecord serviceEvent : serviceEvents) {
            EventDefinition def = getMachingEvenfDef(serviceEvent);
            assertThat("No match for: " + serviceEvent, def, notNullValue());
            ModelObject mo = def.getModelObjectTree(serviceEvent);
            assertThat(mo.getType(), either(equalTo(ManagedObjectType.SnmpInterface))
                    .or(equalTo(ManagedObjectType.Node)));
        }
    }

    @Test
    public void canMapServiceEventsInTicket() {
        // Load data from the ticket #4015708, and ensure we map the events to SNMP interface links
        StaticCpnDatasetViewer viewer = TestDataUtils.loadDataForTicket(4015708, new CpnDatasetView.Builder()
                .withEventTypes(Sets.newHashSet(CpnDatasetView.EventType.SERVICE))
                .build());
        List<EventRecord> serviceEvents = new LinkedList<>();
        viewer.getEventsInTicket("4015708", serviceEvents::addAll);
        assertThat(serviceEvents, hasSize(4));
        for (EventRecord serviceEvent : serviceEvents) {
            EventDefinition def = getMachingEvenfDef(serviceEvent);
            assertThat(def, notNullValue());
            ModelObject mo = def.getModelObjectTree(serviceEvent);

            assertThat(mo.getType(), equalTo(ManagedObjectType.SnmpInterfaceLink));
            assertThat(mo.getPeers().get(0).getType(), equalTo(ManagedObjectType.SnmpInterface));
            assertThat(mo.getPeers().get(1).getType(), equalTo(ManagedObjectType.SnmpInterface));
            assertThat(mo.getPeers().get(0).getParent().getType(), equalTo(ManagedObjectType.Node));
            assertThat(mo.getPeers().get(1).getParent().getType(), equalTo(ManagedObjectType.Node));
        }
    }

    @Test
    public void canMapTrapsInTicket() {
        // Load data from the ticket #4015708, and ensure we map the traps to SNMP interfaces
        StaticCpnDatasetViewer viewer = TestDataUtils.loadDataForTicket(4015708, new CpnDatasetView.Builder()
                .withEventTypes(Sets.newHashSet(CpnDatasetView.EventType.TRAP))
                .build());
        List<EventRecord> traps = new LinkedList<>();
        viewer.getEventsInTicket("4015708", traps::addAll);
        assertThat(traps, hasSize(6));
        for (EventRecord trap : traps) {
            EventDefinition def = getMachingEvenfDef(trap);
            assertThat(def, notNullValue());
            ModelObject mo = def.getModelObjectTree(trap);

            assertThat(mo.getType(), equalTo(ManagedObjectType.SnmpInterface));
            ModelObject parent = mo.getParent();
            assertThat(parent.getType(), equalTo(ManagedObjectType.Node));
        }
    }

    @Test
    public void canParseInterfaceDownServiceEvents() {
        EventRecordLite record = new EventRecordLite() {
            @Override
            public String getDescription() {
                return "Interface status down";
            }

            @Override
            public String getLocation() {
                return "CORE: IP Vlan3148";
            }
        };

        EventDefinition def = getMachingEvenfDef(record);
        assertThat(def, notNullValue());
        ModelObject mo = def.getModelObjectTree(record);
        assertThat(mo.getType(), equalTo(ManagedObjectType.SnmpInterface));
        assertThat(mo.getId(), equalTo("core: ip vlan3148"));
        ModelObject parent = mo.getParent();
        assertThat(parent.getType(), equalTo(ManagedObjectType.Node));
        assertThat(parent.getId(), equalTo("core"));
    }


    @Test
    public void canParseFexPortEvents() {
        EventRecordLite record = new EventRecordLite() {
            @Override
            public String getDescription() {
                return "Fex Port Status ****anything****";
            }

            @Override
            public String getLocation() {
                return "blue: Ethernet3/9/2";
            }
        };

        EventDefinition def = getMachingEvenfDef(record);
        assertThat(def, notNullValue());
        ModelObject mo = def.getModelObjectTree(record);
        assertThat(mo.getType(), equalTo(ManagedObjectType.SnmpInterface));
        assertThat(mo.getId(), equalTo("blue: ethernet3/9/2"));
        ModelObject parent = mo.getParent();
        assertThat(parent.getType(), equalTo(ManagedObjectType.Node));
        assertThat(parent.getId(), equalTo("blue"));
    }

    private static EventDefinition getMachingEvenfDef(EventRecordLite e) {
        for (EventDefinition def : EventMapper.EVENT_DEFS) {
            if (def.matches(e)) {
                return def;
            }
        }
        return null;
    }
}
