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
import static org.hamcrest.Matchers.notNullValue;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Test;
import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.tools.cpn.api.CpnEntityDao;
import org.opennms.oce.tools.cpn.api.EmptyCpnEntityDao;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.cpn.view.StaticCpnDatasetViewer;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.api.EmptyOnmsEntityDao;
import org.opennms.oce.tools.onms.client.api.OnmsEntityDao;
import org.opennms.oce.tools.svc.DefaultNodeAndFactsService;
import org.opennms.oce.tools.svc.NodeAndFactsService;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;

import com.google.common.io.Resources;

public class HybridOnmsCpnOceGeneratorTest {

    @Test
    public void canGenerateHybridModel() {
        // Mock all the data for ticket #4743230
        int ticketId = 4743230;
        StaticCpnDatasetViewer viewer = CpnTestDataUtils.loadDataForTicket(ticketId);
        TicketRecord ticketRecord = viewer.getTicketWithId(Integer.toString(ticketId));

        NodeAndFacts nodeAndFacts = new NodeAndFacts("sw1");
        nodeAndFacts.setOpennmsNodeLabel("sw1.opennms.org");
        nodeAndFacts.setOpennmsNodeId(1);
        assertThat(nodeAndFacts.shouldProcess(), equalTo(true)); // ensure the node is marked for processing

        final List<ESEventDTO> opennmsEvents = CpnTestDataUtils.json2list(Resources.getResource("tickets/" + ticketId + "/opennms.events.json"), ESEventDTO.class);
        final List<AlarmDocumentDTO> opennmsAlarms = CpnTestDataUtils.json2list(Resources.getResource("tickets/" + ticketId + "/opennms.alarms.json"), AlarmDocumentDTO.class);
        final List<AlarmDocumentDTO> opennmsSituations = CpnTestDataUtils.json2list(Resources.getResource("tickets/" + ticketId + "/opennms.situations.json"), AlarmDocumentDTO.class);
        OnmsEntityDao onmsEntityDao = new EmptyOnmsEntityDao() {
            @Override
            public List<ESEventDTO> getTrapEvents(long startMs, long endMs, List<QueryBuilder> includeQueries) {
                return opennmsEvents.stream()
                        .filter(e -> e.getP_oids() != null)
                        .collect(Collectors.toList());
            }

            @Override
            public List<ESEventDTO> getSyslogEvents(long startMs, long endMs, List<QueryBuilder> includeQueries) {
                return opennmsEvents.stream()
                        .filter(e -> e.getSyslogMessage() != null)
                        .collect(Collectors.toList());
            }

            @Override
            public List<AlarmDocumentDTO> getSituationsOnNodeId(long startMs, long endMs, int nodeId) {
                return opennmsSituations;
            }

            @Override
            public List<AlarmDocumentDTO> getAlarmsOnNodeId(long startMs, long endMs, int nodeId) {
                return opennmsAlarms;
            }
        };

        CpnEntityDao cpnEntityDao = new EmptyCpnEntityDao() {
            @Override
            public void getSyslogRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<EventRecord>> callback, QueryBuilder... queries) {
                viewer.getEventsInTicket(ticketRecord.getTicketId(), events -> {
                    final List<EventRecord> syslogEvents = events.stream()
                            .filter(e -> e.getSource().equals("syslog"))
                            .collect(Collectors.toList());
                    callback.accept(syslogEvents);
                });
            }

            @Override
            public void getTrapRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<TrapRecord>> callback) {
                viewer.getEventsInTicket(ticketRecord.getTicketId(), events -> {
                    final List<TrapRecord> trapEvents = events.stream()
                            .filter(e -> e.getSource().equals("trap"))
                            .map(e -> (TrapRecord)e)
                            .collect(Collectors.toList());
                    callback.accept(trapEvents);
                });
            }
        };

        NodeAndFactsService nodeAndFactsService = new DefaultNodeAndFactsService(onmsEntityDao, cpnEntityDao) {
            @Override
            public List<NodeAndFacts> getNodesAndFacts(Set<String> hostnames, ZonedDateTime start, ZonedDateTime end) {
                nodeAndFacts.setStart(start);
                nodeAndFacts.setEnd(end);
                return Collections.singletonList(nodeAndFacts);
            }
        };

        // Generate the dataset
        HybridOnmsCpnOceGenerator oceGenerator = new HybridOnmsCpnOceGenerator.Builder()
                .withViewer(viewer)
                .withNodeAndFactsService(nodeAndFactsService)
                .build();
        FaultDataset faultDataset = oceGenerator.generate();

        // We should have a situation
        assertThat(faultDataset.getSituations().getSituation(), hasSize(1));

        // The situation should have 2 alarms
        Situation situation = faultDataset.getSituations().getSituation().get(0);
        assertThat(situation.getAlarmRef(), hasSize(2));

        // We should have 2 alarms
        Alarms alarms = faultDataset.getAlarms();
        assertThat(alarms.getAlarm(), hasSize(2));

        Alarm alarm1 = alarms.getAlarm().get(0);
        assertThat(alarm1.getEvent(), hasSize(5));
        assertThat(alarm1.getInventoryObjectId(), notNullValue());
        assertThat(alarm1.getInventoryObjectType(), notNullValue());

        Alarm alarm2 = alarms.getAlarm().get(1);
        assertThat(alarm2.getEvent(), hasSize(7));
        assertThat(alarm2.getInventoryObjectId(), notNullValue());
        assertThat(alarm2.getInventoryObjectType(), notNullValue());
    }

}
