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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;

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
