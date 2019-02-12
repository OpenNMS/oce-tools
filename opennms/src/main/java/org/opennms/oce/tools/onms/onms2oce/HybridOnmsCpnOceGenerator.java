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

package org.opennms.oce.tools.onms.onms2oce;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.netmgt.model.alarm.AlarmSummary;
import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.AlarmRef;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Event;
import org.opennms.oce.datasource.v1.schema.Severity;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.cpn.model.EventSeverity;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetViewer;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.svc.NodeAndFactsService;
import org.opennms.oce.tools.ticketdiag.TicketDetails;
import org.opennms.oce.tools.ticketdiag.TicketDiagnostic;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to generate data that can be consumed by OCE.
 */
public class HybridOnmsCpnOceGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(HybridOnmsCpnOceGenerator.class);

    private final CpnDatasetViewer viewer;
    private final NodeAndFactsService nodeAndFactsService;
    private final String ticketId;

    public static class Builder {
        private CpnDatasetViewer viewer;
        private String ticketId;
        private NodeAndFactsService nodeAndFactsService;

        public Builder withViewer(CpnDatasetViewer viewer) {
            this.viewer = viewer;
            return this;
        }

        public Builder withNodeAndFactsService(NodeAndFactsService nodeAndFactsService) {
            this.nodeAndFactsService = nodeAndFactsService;
            return this;
        }

        public Builder withTicketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public HybridOnmsCpnOceGenerator build() {
            Objects.requireNonNull(viewer, "viewer is required");
            Objects.requireNonNull(nodeAndFactsService, "nodeAndFactsService is required.");
            return new HybridOnmsCpnOceGenerator(this);
        }
    }

    private HybridOnmsCpnOceGenerator(Builder builder) {
        this.viewer = builder.viewer;
        this.nodeAndFactsService = builder.nodeAndFactsService;
        this.ticketId = builder.ticketId;
    }

    public FaultDataset generate() {
        LOG.info("Generating the situations and alarms..");
        final Situations situations = new Situations();
        final Alarms alarms = new Alarms();

        final List<TicketRecord> filteredTickets = new LinkedList<>();
        if (ticketId == null) {
            viewer.getTicketRecordsWithRootEventTimeInRange(filteredTickets::addAll);
        } else {
            TicketRecord t = viewer.getTicketWithId(ticketId);
            if (t == null) {
                throw new IllegalStateException("No ticket found with id: " + ticketId);
            }
            filteredTickets.add(t);
        }

        int n = 0;
        final Map<TicketRecord, TicketDetails> ticketDetailsByTicket = new LinkedHashMap<>();
        for (TicketRecord t : filteredTickets) {
            final TicketDiagnostic ticketDiag = new TicketDiagnostic(t, viewer, nodeAndFactsService);
            final TicketDetails ticketDetails = ticketDiag.getTicketDetails();
            if (ticketDetails == null) {
                System.out.println("Skipping ticket: " + t.getTicketId());
            } else {
                ticketDetailsByTicket.put(t, ticketDetails);
            }
            n++;
            System.out.printf("%d of %d (matched %d)\n", n, filteredTickets.size(), ticketDetailsByTicket.size());
        }

        // Generate the list of situations and alarms
        final List<OnmsAlarmSummary> alarmSummaries = new LinkedList<>();
        for (Map.Entry<TicketRecord,TicketDetails> entry : ticketDetailsByTicket.entrySet()) {
            final TicketRecord t = entry.getKey();
            final TicketDetails details = entry.getValue();
            alarmSummaries.addAll(details.getAlarmSummaries());

            final Situation situation = new Situation();
            situation.setId(t.getTicketId());
            situation.setCreationTime(t.getCreationTime().getTime());
            situation.setSeverity(toSeverity(t.getSeverity()));
            situation.setSummary(t.getDescription());
            situation.setDescription(t.getDescription());

            // Group the summaries by reduction key
            final Map<String, List<OnmsAlarmSummary>> alarmSummariesByReductionKey = details.getAlarmSummaries().stream()
                    .collect(Collectors.groupingBy(OnmsAlarmSummary::getReductionKey));

            // Associate the situation with the reduction key, scoped by ticket id
            for (String reductionKey : alarmSummariesByReductionKey.keySet()) {
                final AlarmRef cause = new AlarmRef();
                cause.setId(reductionKey + "-" + t.getTicketId());
                situation.getAlarmRef().add(cause);
            }

            // Convert the alarm summaries to alarms
            alarmSummariesByReductionKey.forEach((reductionKey, summaries) -> {
                final List<Alarm> alarmsForSummaries = summaries.stream()
                        .map(OnmsAlarmSummary::toAlarm)
                        .collect(Collectors.toList());
                alarms.getAlarm().add(merge(reductionKey + "-" + t.getTicketId(), alarmsForSummaries));
            });

            situations.getSituation().add(situation);
        }

        // Generate the model
        OnmsOceModelGenerator gen = new OnmsOceModelGenerator(alarmSummaries);
        gen.generate();

        // Return the dataset
        return new FaultDataset(situations, alarms, gen.getMetaModel(), gen.getInventory());
    }

    private static Alarm merge(String id, List<Alarm> alarms) {
        // Sort by event time
        alarms.sort(Comparator.comparing(Alarm::getFirstEventTime));
        if (alarms.isEmpty()) {
            throw new IllegalStateException("empty alarms for: "  + id);
        }

        final Alarm firstAlarm = alarms.get(0);
        final Alarm lastAlarm = alarms.get(alarms.size() - 1);

        final Alarm alarm = new Alarm();
        alarm.setId(id);

        alarm.setSummary(firstAlarm.getSummary());
        alarm.setDescription(firstAlarm.getDescription());
        alarm.setFirstEventTime(firstAlarm.getFirstEventTime());
        alarm.setLastEventTime(lastAlarm.getLastEventTime());

        for (Alarm a: alarms) {
            alarm.getEvent().addAll(a.getEvent());
        }
        alarm.getEvent().sort(Comparator.comparing(Event::getTime));

        alarm.setInventoryObjectType(firstAlarm.getInventoryObjectType());
        alarm.setInventoryObjectId(firstAlarm.getInventoryObjectId());
        alarm.setLastSeverity(lastAlarm.getLastSeverity());
        return alarm;

    }

    private static Severity toSeverity(EventSeverity severity) {
        switch(severity) {
            case Critical:
                return Severity.CRITICAL;
            case Major:
                return Severity.MAJOR;
            case Minor:
                return Severity.MINOR;
            case Warning:
                return Severity.WARNING;
            case Information:
                return Severity.NORMAL;
            case Cleared:
                return Severity.CLEARED;
        }
        return Severity.INDETERMINATE;
    }

}
