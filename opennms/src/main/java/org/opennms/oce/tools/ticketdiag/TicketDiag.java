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

import java.io.IOException;
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
import java.util.stream.Collectors;
import java.util.Date;

import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;

public class TicketDiag {

    private final ESClient esClient;
    private final long ticketId;
    private final ESDataProvider esDataProvider;

    public TicketDiag(ESClient esClient, long ticketId) {
        this.esClient = Objects.requireNonNull(esClient);
        this.ticketId = ticketId;
        this.esDataProvider = new ESDataProvider(esClient);
    }

    public void doDiag() throws IOException {

        // Retrieve the ticket
        final TicketRecord ticketRecord = esDataProvider.getTicketRecord(ticketId);
        if (ticketRecord == null) {
            System.out.printf("Ticket with id='%d' was not found.\n", ticketId);
            return;
        }
        System.out.printf("Ticket: %s\n", ticketRecord);

        // Retrieve the events
        final List<EventRecord> eventsInTicket = esDataProvider.getEventsByTicketId(ticketRecord.getTicketId());

        // Now group the events by alarm id
        final Map<Long,List<EventRecord>> eventsByAlarmId = eventsInTicket.stream()
                .collect(Collectors.groupingBy(e -> Long.parseLong(e.getAlarmId())));
        // Sort the alarm ids in ascending order
        final List<Long> alarmIds = eventsByAlarmId.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (Long alarmId : alarmIds) {
            System.out.printf("\t%d\n", alarmId);
            // Sort the events in ascending order
            List<EventRecord> eventsForAlarm = eventsByAlarmId.get(alarmId).stream()
                    .sorted(Comparator.comparing(EventRecord::getTime))
                    .collect(Collectors.toList());
            for (EventRecord e : eventsForAlarm) {
                System.out.printf("\t\t%s\n", e);
            }
        }

        // Gather the set of hostnames from the events
        final Set<String> hostnames = new LinkedHashSet<>();
        for (EventRecord e : eventsInTicket) {
            hostnames.add(EventUtils.getNodeLabelFromLocation(e.getLocation()));
        }

        // Build the node and fact generator using the ticket timerange
        final ZonedDateTime minEventTime = eventsInTicket.stream().min(Comparator.comparing(EventRecord::getTime))
                .map(EventRecord::getTime).orElseThrow(() -> new IllegalStateException("No events found in ticket: " + ticketRecord.getTicketId()))
                .toInstant().atZone(ZoneId.systemDefault());
        final ZonedDateTime maxEventTime = eventsInTicket.stream().max(Comparator.comparing(EventRecord::getTime))
                .map(EventRecord::getTime).orElseThrow(() -> new IllegalStateException("No events found in ticket: " + ticketRecord.getTicketId()))
                .toInstant().atZone(ZoneId.systemDefault());
        NodeAndFactsGenerator nodeAndFactsGenerator = NodeAndFactsGenerator.newBuilder()
                .setEsDataProvider(esDataProvider)
                .setEventClient(new EventClient(esClient))
                .setStart(minEventTime.minusMinutes(5))
                .setEnd(maxEventTime.plusMinutes(5))
                .build();

        // Gather the facts for the given hostnames
        boolean canProcessAllNodes = true;
        List<NodeAndFacts> nodesAndFacts = nodeAndFactsGenerator.getNodesAndFacts(hostnames);
        for (NodeAndFacts nodeAndFact : nodesAndFacts) {
            if (!nodeAndFact.shouldProcess()) {
                System.out.printf("Node %s cannot be processed: %s\n", nodeAndFact.getCpnHostname(), nodeAndFact);
                canProcessAllNodes = false;
            }
        }

        // Abort if there are problems with any of the nodes
        if (!canProcessAllNodes) {
            System.out.println("One or more nodes in the ticket cannot be processed. Aborting.");
            return;
        }

        // Build the tree on the other side
        final Map<String, Integer> cpnEventIdToOnmsEventIds = new LinkedHashMap<>();
        final Map<Integer, Integer> onmsEventIdToSituationId = new LinkedHashMap<>();
        final Map<Integer, SituationAndEvents> situationsById = new LinkedHashMap<>();

        // Unmatched
        final List<EventRecord> unmatchedCpnEvents = new LinkedList<>();
        final List<ESEventDTO> unmatchedOnmsEvents = new LinkedList<>();

        for (NodeAndFacts nodeAndFact : nodesAndFacts) {
            final NodeAndEvents nodeAndEvents = nodeAndFactsGenerator.retrieveAndPairEvents(nodeAndFact);
            for (Map.Entry<String, Integer> cpnEventIdToOnmsEventId : nodeAndEvents.getMatchedEvents().entrySet()) {
                cpnEventIdToOnmsEventIds.put(cpnEventIdToOnmsEventId.getKey(), cpnEventIdToOnmsEventId.getValue());
            }

            final List<SituationAndEvents> situationsAndEvents = nodeAndFactsGenerator.getSituationsAndPairEvents(nodeAndEvents);
            for (SituationAndEvents situationAndEvents : situationsAndEvents) {
                situationsById.put(situationAndEvents.getId(), situationAndEvents);
                for (ESEventDTO esEventDTO : situationAndEvents.getEventsInSituation()) {
                    onmsEventIdToSituationId.put(esEventDTO.getId(), situationAndEvents.getId());
                }
            }

            unmatchedCpnEvents.addAll(nodeAndEvents.getUnmatchedCpnEvents());
            unmatchedOnmsEvents.addAll(nodeAndEvents.getUnmatchedOnmsEvents());
        }


        // Let's look at the events that were not matched
        if (!unmatchedCpnEvents.isEmpty()) {
            System.out.println("\n\nUnmatched events:");
            unmatchedCpnEvents.sort(Comparator.comparing(EventRecord::getTime));
            unmatchedOnmsEvents.sort(Comparator.comparing(ESEventDTO::getTimestamp));
            for (EventRecord e : unmatchedCpnEvents) {
                System.out.println(e);
            }
            for (ESEventDTO e : unmatchedOnmsEvents) {
                System.out.println(e);
            }
            System.out.println("\n\n");
        }


        for (EventRecord e : eventsInTicket) {
            Integer onmsEventId = cpnEventIdToOnmsEventIds.get(e.getEventId());
            Integer situationId = onmsEventId != null ? onmsEventIdToSituationId.get(onmsEventId) : null;
            System.out.printf("CPN Event: %s (%s - %s) -> OpenNMS Event ID: %s -> OpenNMS Situation ID: %s\n",
                    e.getEventId(), e.getSource(), e.getTime(), onmsEventId, situationId);
            if (onmsEventId == null) {
                System.out.printf("\t%s\n", e);
            }
        }
    }
}
