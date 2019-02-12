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

package org.opennms.oce.tools.ticketdiag;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetViewer;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.svc.NodeAndFactsService;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.opennms.oce.tools.tsaudit.SituationsAlarmsAndEvents;

public class TicketDiagnostic {

    private final TicketRecord ticket;
    private final CpnDatasetViewer viewer;
    private final NodeAndFactsService nodeAndFactsService;

    public TicketDiagnostic(TicketRecord ticket, CpnDatasetViewer viewer, NodeAndFactsService nodeAndFactsService) {
        this.ticket = Objects.requireNonNull(ticket);
        this.viewer = Objects.requireNonNull(viewer);
        this.nodeAndFactsService = Objects.requireNonNull(nodeAndFactsService);
    }

    public TicketDetails getTicketDetails() {
        // Retrieve the events
        final List<EventRecord> eventsInTicket = new LinkedList<>();
        viewer.getEventsInTicket(ticket, eventsInTicket::addAll);


        final Set<String> eventIdsInTicket = new LinkedHashSet<>();
        eventsInTicket.forEach(e -> eventIdsInTicket.add(e.getEventId()));

        // Gather the set of hostnames from the events
        final Set<String> hostnames = new LinkedHashSet<>();
        for (EventRecord e : eventsInTicket) {
            hostnames.add(EventUtils.getNodeLabelFromLocation(e.getLocation()));
        }

        // Build the node and fact generator using the ticket timerange
        final ZonedDateTime minEventTime = eventsInTicket.stream().min(Comparator.comparing(EventRecord::getTime))
                .map(EventRecord::getTime).orElseThrow(() -> new IllegalStateException("No events found in ticket: " + ticket.getTicketId()))
                .toInstant().atZone(ZoneId.systemDefault());
        final ZonedDateTime maxEventTime = eventsInTicket.stream().max(Comparator.comparing(EventRecord::getTime))
                .map(EventRecord::getTime).orElseThrow(() -> new IllegalStateException("No events found in ticket: " + ticket.getTicketId()))
                .toInstant().atZone(ZoneId.systemDefault());

        final ZonedDateTime start = minEventTime.minusMinutes(5);
        final ZonedDateTime end = maxEventTime.plusMinutes(5);

        // Gather the facts for the given hostnames
        boolean canProcessAllNodes = true;
        List<NodeAndFacts> nodesAndFacts = nodeAndFactsService.getNodesAndFacts(hostnames, start, end);
        for (NodeAndFacts nodeAndFact : nodesAndFacts) {
            if (!nodeAndFact.shouldProcess()) {
                System.out.printf("Node %s cannot be processed: %s\n", nodeAndFact.getCpnHostname(), nodeAndFact);
                canProcessAllNodes = false;
            }
        }

        // Abort if there are problems with any of the nodes in the ticket
        if (!canProcessAllNodes) {
            System.out.println("One or more nodes in the ticket cannot be processed. Aborting.");
            return null;
        }

        // Compute the event and alarm maps
        final Map<String, Integer> cpnEventIdToOnmsEventIds = new LinkedHashMap<>();
        final Map<Integer, String> onmsEventIdToCpnEventIds = new LinkedHashMap<>();

        final Map<Integer, Integer> onmsEventIdToAlarmId = new LinkedHashMap<>();
        final Map<Integer, Integer> alarmIdToSituationId = new LinkedHashMap<>();
        final Map<Integer, SituationAndEvents> situationsById = new LinkedHashMap<>();
        final Map<Integer, OnmsAlarmSummary> alarmsById = new LinkedHashMap<>();
        final Map<Integer, ESEventDTO> eventsById = new LinkedHashMap<>();

        for (NodeAndFacts nodeAndFact : nodesAndFacts) {
            final NodeAndEvents nodeAndEvents = nodeAndFactsService.retrieveAndPairEvents(nodeAndFact);

            for (Map.Entry<String, Integer> cpnEventIdToOnmsEventId : nodeAndEvents.getMatchedEvents().entrySet()) {
                if (!eventIdsInTicket.contains(cpnEventIdToOnmsEventId.getKey())) {
                    // Skip events that are not in the ticket
                    continue;
                }

                cpnEventIdToOnmsEventIds.put(cpnEventIdToOnmsEventId.getKey(), cpnEventIdToOnmsEventId.getValue());
                onmsEventIdToCpnEventIds.put(cpnEventIdToOnmsEventId.getValue(), cpnEventIdToOnmsEventId.getKey());
            }

            // Only include OpenNMS events that have a corresponding CPN event
            nodeAndEvents.getOnmsEvents().forEach(e -> {
                if (cpnEventIdToOnmsEventIds.values().contains(e.getId())) {
                    eventsById.put(e.getId(), e);
                }
            });

            // Gather all alarms and situations for the given time range on the node
            final SituationsAlarmsAndEvents situationsAlarmsAndEvents = nodeAndFactsService.getSituationsAlarmsAndEvents(nodeAndEvents);
            // Only include OpenNMS events that have a corresponding CPN event
            situationsAlarmsAndEvents.getEventIdToAlarmId().forEach((onmsEventId, onmsAlarmId) -> {
                if (onmsEventIdToCpnEventIds.containsKey(onmsEventId)) {
                    onmsEventIdToAlarmId.put(onmsEventId, onmsAlarmId);
                }
            });
            // Only include OpenNMS alarms that have a corresponding mapped OpenNMS event
            situationsAlarmsAndEvents.getAlarmIdToSituationId().forEach((alarmId, situationId) -> {
                if (onmsEventIdToAlarmId.values().contains(alarmId)) { // TODO: Quicker lookup
                    alarmIdToSituationId.put(alarmId, situationId);
                }
            });
            // Only include OpenNMS alarms that have a corresponding mapped OpenNMS event
            situationsAlarmsAndEvents.getAlarmsById().forEach((alarmId, alarm) -> {
                if (onmsEventIdToAlarmId.values().contains(alarmId)) { // TODO: Quicker lookup
                    alarmsById.put(alarmId, alarm);
                }
            });
            // Only include OpenNMS situations that have a corresponding mapped OpenNMS event
            situationsAlarmsAndEvents.getSituationsById().forEach((situationId, situation) -> {
                if (alarmIdToSituationId.values().contains(situationId)) { // TODO: Quicker lookup
                    situationsById.put(situationId, situation);
                }
            });
        }

        return new TicketDetails(eventsInTicket, new ArrayList<>(eventsById.values()),
                cpnEventIdToOnmsEventIds, onmsEventIdToAlarmId, alarmIdToSituationId, alarmsById, situationsById);
    }
}
