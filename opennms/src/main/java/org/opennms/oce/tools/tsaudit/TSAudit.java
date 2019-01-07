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

package org.opennms.oce.tools.tsaudit;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;

public class TSAudit {
    private static final Logger LOG = LoggerFactory.getLogger(TSAudit.class);

    private final ESDataProvider esDataProvider;
    private final EventClient eventClient;
    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final List<String> nodes;
    private final StateCache stateCache;

    public TSAudit(ESDataProvider esDataProvider, EventClient eventClient, ZonedDateTime start, ZonedDateTime end, List<String> nodes) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.eventClient = Objects.requireNonNull(eventClient);

        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.nodes = Objects.requireNonNull(nodes);

        this.stateCache = new StateCache(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    public void run() throws IOException {
        final List<NodeAndFacts> nodesAndFacts = getNodesAndFacts();
        if (nodesAndFacts.isEmpty()) {
            System.out.println("No nodes found.");
            return;
        }
        printNodesAndFactsAsTable(nodesAndFacts);
    }

    private List<NodeAndFacts> getNodesAndFacts() throws IOException {
        final List<NodeAndFacts> nodesAndFacts = new LinkedList<>();

        // Build the unique set of hostnames by scrolling through all locations and extracting
        // the hostname portion
        final Set<String> hostnames = new LinkedHashSet<>();
        esDataProvider.getDistinctLocations(start, end, locations -> {
            for (String location : locations) {
                hostnames.add(EventUtils.getNodeLabelFromLocation(location));
            }
        });

        // Build the initial objects from the set of hostname
        for (String hostname : hostnames) {
            nodesAndFacts.add(new NodeAndFacts(hostname));
        }

        // Now try and find *some* event for where the node label starts with the given hostname
        for (NodeAndFacts nodeAndFacts : nodesAndFacts) {
            findOpennmsNodeInfo(nodeAndFacts);
        }

        return nodesAndFacts;
    }

    private void findOpennmsNodeInfo(NodeAndFacts nodeAndFacts) throws IOException {
        LOG.debug("Trying to find OpenNMS node info for hostname: {}", nodeAndFacts.getCpnHostname());
        if (stateCache.findOpennmsNodeInfo(nodeAndFacts)) {
            LOG.debug("Results successfully loaded from cache.");
            return;
        }

        final Optional<ESEventDTO> firstEvent = eventClient.findFirstEventForHostname(start.toInstant().toEpochMilli(),
                end.toInstant().toEpochMilli(),
                nodeAndFacts.getCpnHostname());
        if (firstEvent.isPresent()) {
            final ESEventDTO event = firstEvent.get();
            nodeAndFacts.setOpennmsNodeId(event.getNodeId());
            nodeAndFacts.setOpennmsNodeLabel(event.getNodeLabel());
            LOG.debug("Matched {} with {} (id={}).", nodeAndFacts.getCpnHostname(), event.getNodeLabel(), event.getNodeId());
        } else {
            LOG.debug("No match found for: {}", nodeAndFacts.getCpnHostname());
        }
        // Save the results in the cache
        stateCache.saveOpennmsNodeInfo(nodeAndFacts);
    }

    private static void printNodesAndFactsAsTable(List<NodeAndFacts> nodesAndFacts) {
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow("CPN Hostname", "OpenNMS Node Label", "OpenNMS Node ID");
        at.addRule();
        for (NodeAndFacts n : nodesAndFacts) {
            at.addRow(n.getCpnHostname(),
                    naWhenNull(n.getOpennmsNodeLabel()),
                    naWhenNull(n.getOpennmsNodeId()));
            at.addRule();
        }

        CWC_LongestLine cwc = new CWC_LongestLine();
        at.getRenderer().setCWC(cwc);

        System.out.println(at.render());
    }

    private static String naWhenNull(String text) {
        return text != null ? text : "N/A";
    }

    private static String naWhenNull(Number number) {
        return number != null ? number.toString() : "N/A";
    }
}
