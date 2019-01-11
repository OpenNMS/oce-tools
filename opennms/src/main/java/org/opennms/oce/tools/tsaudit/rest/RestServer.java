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

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.opennms.oce.tools.tsaudit.TicketAndEvents;

public class RestServer {
    static final URI BASE_URI = getBaseURI();

    private static URI getBaseURI() {
        return UriBuilder.fromUri("http://localhost/").port(8080).build();
    }

    private final List<NodeAndFacts> nodesAndFacts;
    private final List<NodeAndEvents> allNodesAndEvents;
    private final Map<Integer, List<TicketAndEvents>> allTicketAndEvents;
    private final Map<Integer, List<SituationAndEvents>> allSituationAndEvents;

    public RestServer(List<NodeAndFacts> nodesAndFacts, List<NodeAndEvents> allNodesAndEvents,
                      Map<Integer, List<TicketAndEvents>> allTicketAndEvents, Map<Integer, List<SituationAndEvents>> allSituationAndEvents) {
        this.nodesAndFacts = nodesAndFacts;
        this.allNodesAndEvents = allNodesAndEvents;
        this.allTicketAndEvents = allTicketAndEvents;
        this.allSituationAndEvents = allSituationAndEvents;
    }

    public void startAndBlock() throws IOException {
        System.out.println("Starting grizzly...");
        HttpServer httpServer = startServer();
        System.out.println("Server started....");
        // System.out.printf("Jersey app started with WADL available at %sapplication.wadl%n", BASE_URI);
        System.out.println("Hit enter to stop it...");
        System.in.read();
        httpServer.shutdownNow();
    }

    private HttpServer startServer() {
        ResourceConfig rc = ResourceConfig.forApplication(new TSAuditApplication(this));
        rc.register(new CorsFilter());
        return GrizzlyHttpServerFactory.createHttpServer(BASE_URI, rc);
    }

    public List<NodeAndFacts> getNodesAndFacts() {
        return nodesAndFacts;
    }

    public List<NodeAndEvents> getAllNodesAndEvents() {
        return allNodesAndEvents;
    }

    public Map<Integer, List<TicketAndEvents>> getAllTicketAndEvents() {
        return allTicketAndEvents;
    }

    public Map<Integer, List<SituationAndEvents>> getAllSituationAndEvents() {
        return allSituationAndEvents;
    }
}
