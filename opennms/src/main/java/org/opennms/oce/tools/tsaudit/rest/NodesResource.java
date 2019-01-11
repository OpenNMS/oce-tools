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

package org.opennms.oce.tools.tsaudit.rest;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.elasticsearch.common.Strings;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.tsaudit.Lifespan;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.opennms.oce.tools.tsaudit.TicketAndEvents;

@Path("nodes")
public class NodesResource {

    public static final String OPENNMS_SOURCE = "onms";
    public static final String CPN_SOURCE = "cpn";

    private final RestServer restServer;

    public NodesResource(RestServer restServer) {
        this.restServer = Objects.requireNonNull(restServer);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNodes() {
        final List<VizNodeDTO> nodes = restServer.getNodesAndFacts().stream()
                .filter(NodeAndFacts::shouldProcess)
                .map(n -> new VizNodeDTO(n.getOpennmsNodeId(), n.getOpennmsNodeLabel()))
                .collect(Collectors.toList());
        return Response.ok(nodes).build();
    }

    @GET
    @Path("/{nodeId}/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEventsForNode(@PathParam("nodeId") Integer nodeId) {
        final Optional<NodeAndEvents> nodeAndEventsOptional = getNodeAndEventsForNode(nodeId);
        if (!nodeAndEventsOptional.isPresent()) {
            return Response.noContent().build();
        }

        final NodeAndEvents nodeAndEvents = nodeAndEventsOptional.get();
        final List<VizEventDTO> events = new LinkedList<>();
        nodeAndEvents.getOnmsEvents().stream()
                .map(NodesResource::toEvent)
                .forEach(events::add);
        nodeAndEvents.getCpnEvents().stream()
                .map(NodesResource::toEvent)
                .forEach(events::add);
        return Response.ok(events).build();
    }

    @GET
    @Path("/{nodeId}/events/map")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEventMapForNode(@PathParam("nodeId") int nodeId) {
        final Optional<NodeAndEvents> nodeAndEventsOptional = getNodeAndEventsForNode(nodeId);
        if (!nodeAndEventsOptional.isPresent()) {
            return Response.noContent().build();
        }

        final NodeAndEvents nodeAndEvents = nodeAndEventsOptional.get();
        final Map<String,String> eventIdMap = nodeAndEvents.getMatchedEvents().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        final VizEventMapDTO vizEventMapDTO = new VizEventMapDTO(CPN_SOURCE, OPENNMS_SOURCE, eventIdMap);

        return Response.ok(vizEventMapDTO).build();
    }

    @GET
    @Path("/{nodeId}/alarms")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAlarmsForNode(@PathParam("nodeId") Integer nodeId) {
        final Optional<NodeAndEvents> nodeAndEventsOptional = getNodeAndEventsForNode(nodeId);
        if (!nodeAndEventsOptional.isPresent()) {
            return Response.noContent().build();
        }

        final List<VizAlarmDTO> alarms = new LinkedList<>();
        final NodeAndEvents nodeAndEvents = nodeAndEventsOptional.get();

        // Group the CPN events by alarm id
        final Map<String,List<EventRecord>> cpnEventsByAlarmId = nodeAndEvents.getCpnEvents().stream()
                .filter(e -> !Strings.isNullOrEmpty(e.getAlarmId()))
                .collect(Collectors.groupingBy(EventRecord::getAlarmId));
        cpnEventsByAlarmId.entrySet().stream().forEach(entry -> {
            // Convert each group of events to an "alarm"
            final LongSummaryStatistics stats = entry.getValue().stream().mapToLong(e -> e.getTime().getTime()).summaryStatistics();
            final String label = entry.getValue().get(0).getDescription() + " @ " + entry.getValue().get(0).getLocation();
            final List<String> eventIds = entry.getValue().stream().map(EventRecord::getEventId).collect(Collectors.toList());
            final VizAlarmDTO vizAlarmDTO = new VizAlarmDTO(entry.getKey(), CPN_SOURCE, stats.getMin(), stats.getMax(), label, eventIds);
            alarms.add(vizAlarmDTO);
        });

        // Map the ONMS alarms
        final List<SituationAndEvents> allStuationAndEvents = restServer.getAllSituationAndEvents().get(nodeId);
        for (SituationAndEvents situationAndEvents : allStuationAndEvents) {
            for (OnmsAlarmSummary onmsAlarmSummary : situationAndEvents.getAlarmSummaries()) {
                final String id =  Integer.toString(onmsAlarmSummary.getId());
                final List<String> eventIds = onmsAlarmSummary.getEvents().stream().map(e -> e.getId().toString()).collect(Collectors.toList());;
                final VizAlarmDTO vizAlarmDTO = new VizAlarmDTO(id, OPENNMS_SOURCE,
                        onmsAlarmSummary.getLifespan().getStartMs(), onmsAlarmSummary.getLifespan().getEndMs(),
                        onmsAlarmSummary.getLogMessage(), eventIds);
                alarms.add(vizAlarmDTO);
            }
        }

        return Response.ok(alarms).build();
    }

    @GET
    @Path("/{nodeId}/situations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSituationsForNode(@PathParam("nodeId") Integer nodeId) {
        final Optional<NodeAndEvents> nodeAndEventsOptional = getNodeAndEventsForNode(nodeId);
        if (!nodeAndEventsOptional.isPresent()) {
            return Response.noContent().build();
        }

        final List<VizSituationDTO> situations = new LinkedList<>();
        final NodeAndEvents nodeAndEvents = nodeAndEventsOptional.get();

        // Build the map of ticket id to alarm id
        final Map<String,Set<String>> ticketIdToAlarmIds = new HashMap<>();
        for (EventRecord e : nodeAndEvents.getCpnEvents()) {
            if (Strings.isNullOrEmpty(e.getAlarmId()) || Strings.isNullOrEmpty(e.getTicketId())) {
                continue;
            }

            ticketIdToAlarmIds.compute(e.getTicketId(), (k,v) -> {
                Set<String> alarmIds = v;
                if (alarmIds == null) {
                    alarmIds = new HashSet<>();
                }
                alarmIds.add(e.getAlarmId());
                return alarmIds;
            });
        }

        // Gather the ticket details
        final List<TicketAndEvents> allTicketsAndEvents = restServer.getAllTicketAndEvents().get(nodeId);
        for (TicketAndEvents ticketAndEvents : allTicketsAndEvents) {
            final TicketRecord ticket = ticketAndEvents.getTicket();
            final VizSituationDTO vizSituationDTO = new VizSituationDTO(ticket.getTicketId(), CPN_SOURCE,
                    ticket.getRootEventTime().getTime(), ticket.getLastModificationTime().getTime(),
                    ticket.getDescription(), ticketIdToAlarmIds.getOrDefault(ticket.getTicketId(), Collections.emptySet()));
            situations.add(vizSituationDTO);
        }

        // Convert the situations
        final List<SituationAndEvents> situationsAndEvents = restServer.getAllSituationAndEvents().get(nodeId);
        for (SituationAndEvents situationAndEvents : situationsAndEvents) {
            final Lifespan lifespan = situationAndEvents.getLifespan();
            final List<String> alarmIds = situationAndEvents.getAlarmSummaries().stream().map(a -> Integer.toString(a.getId())).collect(Collectors.toList());

            final VizSituationDTO vizSituationDTO = new VizSituationDTO(situationAndEvents.getId().toString(), OPENNMS_SOURCE,
                    lifespan.getStartMs(), lifespan.getEndMs(),
                    situationAndEvents.getLogMessage(), alarmIds);
            situations.add(vizSituationDTO);
        }

        return Response.ok(situations).build();
    }

    private Optional<NodeAndEvents> getNodeAndEventsForNode(int nodeId) {
        return restServer.getAllNodesAndEvents().stream()
                .filter(nes -> Objects.equals(nodeId, nes.getNodeAndFacts().getOpennmsNodeId()))
                .findAny();
    }

    private static VizEventDTO toEvent(ESEventDTO event) {
        return new VizEventDTO(event.getId().toString(), event.getTimestamp().getTime(), OPENNMS_SOURCE, event.getLogMessage());
    }

    private static VizEventDTO toEvent(EventRecord event) {
        return new VizEventDTO(event.getEventId(), event.getTime().toInstant().toEpochMilli(), CPN_SOURCE,  event.getDescription() + " @ " + event.getLocation());
    }
}
