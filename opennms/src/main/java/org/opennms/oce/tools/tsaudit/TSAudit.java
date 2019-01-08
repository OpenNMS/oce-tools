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

import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.elasticsearch.index.query.QueryBuilder;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
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
    private final long startMs;
    private final long endMs;
    private final List<String> nodes;
    private final StateCache stateCache;
    private final boolean csvOutput;

    public TSAudit(ESDataProvider esDataProvider, EventClient eventClient, ZonedDateTime start, ZonedDateTime end, List<String> nodes, boolean csvOutput) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.eventClient = Objects.requireNonNull(eventClient);

        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.startMs = start.toInstant().toEpochMilli();
        this.endMs = end.toInstant().toEpochMilli();

        this.nodes = Objects.requireNonNull(nodes);
        this.csvOutput = csvOutput;

        this.stateCache = new StateCache(startMs, endMs);
    }

    public void run() throws IOException {
        // Build the complete list of nodes that have either trap or syslog events in CPN
        // in the given time range and and gather facts related to these
        final List<NodeAndFacts> nodesAndFacts = getNodesAndFacts();
        if (nodesAndFacts.isEmpty()) {
            System.out.println("No nodes found.");
            return;
        }

        // Render the results
        if (csvOutput) {
            printNodesAndFactsAsCsv(nodesAndFacts);
        } else {
            printNodesAndFactsAsTable(nodesAndFacts);
        }

        // Determine the subset of nodes that should be processed
        final List<NodeAndFacts> nodesToProcess = nodesAndFacts.stream()
                .filter(NodeAndFacts::shouldProcess)
                .collect(Collectors.toList());

        // TODO: Do something with the nodesToProcess
    }

    private List<NodeAndFacts> getNodesAndFacts() throws IOException {
        final List<NodeAndFacts> nodesAndFacts = new LinkedList<>();

        // Don't consider authentication failure traps
        List<QueryBuilder> cpnEventExcludes = Arrays.asList(termQuery("description.keyword", "SNMP authentication failure"));

        // Build the unique set of hostnames by scrolling through all locations and extracting
        // the hostname portion
        final Set<String> hostnames = new LinkedHashSet<>();
        esDataProvider.getDistinctLocations(start, end, cpnEventExcludes, locations -> {
            for (String location : locations) {
                hostnames.add(EventUtils.getNodeLabelFromLocation(location));
            }
        });

        // Build the initial objects from the set of hostname
        for (String hostname : hostnames) {
            nodesAndFacts.add(new NodeAndFacts(hostname));
        }

        for (NodeAndFacts nodeAndFacts : nodesAndFacts) {
            // Now try and find *some* event for where the node label starts with the given hostname
            findOpennmsNodeInfo(nodeAndFacts);

            // Don't do any further processing if there is no node associated
            if (!nodeAndFacts.hasOpennmsNode()) {
                continue;
            }

            // Count the number of syslogs and traps received in CPN
            nodeAndFacts.setNumCpnSyslogs(esDataProvider.getNumSyslogEvents(start, end, nodeAndFacts.getCpnHostname(), cpnEventExcludes));
            nodeAndFacts.setNumCpnTraps(esDataProvider.getNumTrapEvents(start, end, nodeAndFacts.getCpnHostname(), cpnEventExcludes));

            // Count the number of syslogs and traps received in OpenNMS
            nodeAndFacts.setNumOpennmsSyslogs(eventClient.getNumSyslogEvents(startMs, endMs, nodeAndFacts.getOpennmsNodeId()));
            nodeAndFacts.setNumOpennmsTraps(eventClient.getNumTrapEvents(startMs, endMs, nodeAndFacts.getOpennmsNodeId()));

            // Detect clock skew if we have 1+ syslog messages from both CPN and OpenNMS
            if (nodeAndFacts.getNumCpnSyslogs() > 0 && nodeAndFacts.getNumOpennmsSyslogs() > 0) {
                detectClockSkewUsingSyslogEvents(nodeAndFacts);
            }
        }

        // Sort
        nodesAndFacts.sort(Comparator.comparing(NodeAndFacts::shouldProcess)
                .thenComparing(n -> n.getNumCpnSyslogs() != null ? n.getNumCpnSyslogs() : 0)
                .thenComparing(n -> n.getNumCpnTraps() != null ? n.getNumCpnTraps() : 0)
                .reversed());

        return nodesAndFacts;
    }

    private void findOpennmsNodeInfo(NodeAndFacts nodeAndFacts) throws IOException {
        LOG.debug("Trying to find OpenNMS node info for hostname: {}", nodeAndFacts.getCpnHostname());
        if (stateCache.findOpennmsNodeInfo(nodeAndFacts)) {
            LOG.debug("Results successfully loaded from cache.");
            return;
        }

        final Optional<ESEventDTO> firstEvent = eventClient.findFirstEventForNodeLabelPrefix(startMs, endMs,
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

    private void detectClockSkewUsingSyslogEvents(NodeAndFacts nodeAndFacts) throws IOException {
        LOG.debug("Detecting clock skew for hostname: {}", nodeAndFacts.getCpnHostname());
        final List<Long> deltas = new LinkedList<>();
        final AtomicReference<Date> minTimeRef = new AtomicReference<>(new Date());
        final AtomicReference<Date> maxTimeRef = new AtomicReference<>(new Date(0));

        // Retrieve syslog records for the given host
        esDataProvider.getSyslogRecordsInRange(start, end, syslogs -> {
            for (EventRecord syslog : syslogs) {
                // Skip clears
                if (EventUtils.isClear(syslog)) {
                    continue;
                }

                Date processedTime = syslog.getTime();
                Date messageTime;

                // Parse the syslog record and extract the date from the message
                try {
                    GenericSyslogMessage parsedSyslogMessage = GenericSyslogMessage.fromCpn(nodeAndFacts.getCpnHostname(), syslog.getDetailedDescription());
                    messageTime = parsedSyslogMessage.getDate();
                } catch (ExecutionException|InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // Compute the delta between the message date, and the time at which the event was processed
                deltas.add(Math.abs(processedTime.getTime() - messageTime.getTime()));

                // Keep track of the min and max times
                if (processedTime.compareTo(minTimeRef.get()) < 0) {
                    minTimeRef.set(processedTime);
                }
                if (processedTime.compareTo(maxTimeRef.get()) > 0) {
                    maxTimeRef.set(processedTime);
                }

            }
        }, matchPhraseQuery("location", nodeAndFacts.getCpnHostname()));


        NodeAndFacts.ClockSkewStatus clockSkewStatus = NodeAndFacts.ClockSkewStatus.INDETERMINATE;
        Long clockSkew = null;

        // Ensure we have at least 3 samples and that the messages span at least 10 minutes
        if (deltas.size() > 3 && Math.abs(maxTimeRef.get().getTime() - minTimeRef.get().getTime()) >= TimeUnit.MINUTES.toMillis(10)) {
            OptionalDouble avg = deltas.stream()
                    .mapToLong(d -> d)
                    .average();
            if (avg.isPresent() && avg.getAsDouble() > TimeUnit.SECONDS.toMillis(30)) {
                clockSkewStatus = NodeAndFacts.ClockSkewStatus.DETECTED;
                clockSkew = new Double(avg.getAsDouble()).longValue();
            } else {
                clockSkewStatus = NodeAndFacts.ClockSkewStatus.NOT_DETECTED;
            }
        }

        nodeAndFacts.setClockSkewStatus(clockSkewStatus);
        nodeAndFacts.setClockSkew(clockSkew);
        LOG.debug("Clock skew results for hostname: {}, status: {}, skew: {}", nodeAndFacts.getCpnHostname(), clockSkewStatus, clockSkew);
    }

    private static List<List<String>> nodesAndFactsAsTable(List<NodeAndFacts> nodesAndFacts) {
        List<List<String>> rows = new LinkedList<>();
        List<String> header = Arrays.asList("Index", "CPN Hostname", "OpenNMS Node Label", "OpenNMS Node ID", "OpenNMS Syslogs", "OpenNMS Traps", "CPN Syslogs", "CPN Traps", "Clock Skew", "Process?");
        rows.add(header);
        int k = 1;
        for (NodeAndFacts n : nodesAndFacts) {
            String clockSkewString = "Indeterminate";
            if (NodeAndFacts.ClockSkewStatus.DETECTED.equals(n.getClockSkewStatus())) {
                clockSkewString = String.format("Yes (%d ms)", n.getClockSkew());
            } else if (NodeAndFacts.ClockSkewStatus.NOT_DETECTED.equals(n.getClockSkewStatus())) {
                clockSkewString = "No";
            }
            List<String> row = Arrays.asList(Integer.toString(k), n.getCpnHostname(),
                    naWhenNull(n.getOpennmsNodeLabel()),
                    naWhenNull(n.getOpennmsNodeId()),
                    naWhenNull(n.getNumOpennmsSyslogs()),
                    naWhenNull(n.getNumOpennmsTraps()),
                    naWhenNull(n.getNumCpnSyslogs()),
                    naWhenNull(n.getNumCpnTraps()),
                    clockSkewString,
                    n.shouldProcess() ? "Yes" : "No");
            k++;
            rows.add(row);
        }
        return rows;
    }

    private static void printNodesAndFactsAsTable(List<NodeAndFacts> nodesAndFacts) {
        final List<List<String>> rows = nodesAndFactsAsTable(nodesAndFacts);
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow(rows.get(0).toArray(new String[0]));
        at.addRule();
        for (List<String> row : rows.subList(1, rows.size())) {
            at.addRow(row.toArray(new String[0]));
            at.addRule();
        }

        CWC_LongestLine cwc = new CWC_LongestLine();
        at.getRenderer().setCWC(cwc);

        System.out.println(at.render());
    }

    private static void printNodesAndFactsAsCsv(List<NodeAndFacts> nodesAndFacts) {
        final List<List<String>> rows = nodesAndFactsAsTable(nodesAndFacts);
        try (
                StringWriter writer = new StringWriter();
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader(rows.get(0).toArray(new String[0])));
        ) {
            for (List<String> row : rows.subList(1, rows.size())) {
                csvPrinter.printRecord(row.toArray(new String[0]));
            }
            System.out.println(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String naWhenNull(String text) {
        return text != null ? text : "N/A";
    }

    private static String naWhenNull(Number number) {
        return number != null ? number.toString() : "N/A";
    }
}
