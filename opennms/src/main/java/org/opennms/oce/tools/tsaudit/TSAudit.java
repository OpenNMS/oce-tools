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

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.elasticsearch.index.query.QueryBuilder;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.events.MatchingSyslogEventRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.rest.RestServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

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
    private final List<String> hostnameSubstringsToFilter;
    private final boolean csvOutput;
    private final boolean restServerEnabled;
    private final NodeAndFactsGenerator nodeAndFactsGenerator;

    // Don't consider authentication failure traps
    public static final List<QueryBuilder> cpnEventExcludes = Arrays.asList(termQuery("description.keyword", "SNMP authentication failure"));

    public TSAudit(ESDataProvider esDataProvider, EventClient eventClient, ZonedDateTime start, ZonedDateTime end, List<String> hostnames,
                   boolean csvOutput, boolean restServerEnabled) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.eventClient = Objects.requireNonNull(eventClient);

        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.startMs = start.toInstant().toEpochMilli();
        this.endMs = end.toInstant().toEpochMilli();

        this.hostnameSubstringsToFilter = Objects.requireNonNull(hostnames);
        this.csvOutput = csvOutput;
        this.restServerEnabled = restServerEnabled;
        
        nodeAndFactsGenerator = NodeAndFactsGenerator.newBuilder()
                .setCpnEventExcludes(cpnEventExcludes)
                .setEnd(this.end)
                .setCpnEntityDao(this.esDataProvider)
                .setOnmsEntityDao(this.eventClient)
                .setHostnameSubstringsToFilter(this.hostnameSubstringsToFilter)
                .setStart(this.start)
                .build();
    }

    public void run() throws IOException {
        // Build the complete list of nodes that have either trap or syslog events in CPN
        // in the given time range and and gather facts related to these
        final List<NodeAndFacts> nodesAndFacts = nodeAndFactsGenerator.getNodesAndFacts();
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

        final List<NodeAndEvents> allNodesAndEvents = new LinkedList<>();
        final Map<Integer, List<TicketAndEvents>> allTicketAndEvents = new HashMap<>();
        final Map<Integer, List<SituationAndEvents>> allSituationAndEvents = new HashMap<>();

        for (NodeAndFacts nodeAndFacts : nodesToProcess) {
            // Gather all the events of interest for the node we want to process
            final NodeAndEvents nodeAndEvents = nodeAndFactsGenerator.retrieveAndPairEvents(nodeAndFacts);

            // Print the matches
            printEventMatches(nodeAndEvents.getMatchedTraps(), nodeAndEvents.getCpnTrapEvents(), nodeAndEvents.getOnmsTrapEvents());
            printEventMatches(nodeAndEvents.getMatchedSyslogs(), nodeAndEvents.getCpnSyslogEvents(), nodeAndEvents.getOnmsSyslogEvents());

            // Gather the tickets for the node
            final List<TicketAndEvents> ticketsAndEvents = nodeAndFactsGenerator.getTicketsAndPairEvents(nodeAndEvents);

            // Gather the situations for the node
            final List<SituationAndEvents> situationsAndEvents = nodeAndFactsGenerator.getSituationsAndPairEvents(nodeAndEvents);

            // Match
            final List<SituationMatchResult> matchResults = match(nodeAndEvents, ticketsAndEvents, situationsAndEvents);
            printSituationMatches(matchResults);

            if (restServerEnabled) {
                // Only keep these around if we need to
                allNodesAndEvents.add(nodeAndEvents);
                final int nodeId = nodeAndEvents.getNodeAndFacts().getOpennmsNodeId();
                allTicketAndEvents.put(nodeId, ticketsAndEvents);
                allSituationAndEvents.put(nodeId, situationsAndEvents);
            }
        }

        if (restServerEnabled) {
            final RestServer restServer = new RestServer(nodesAndFacts, allNodesAndEvents, allTicketAndEvents, allSituationAndEvents);
            restServer.startAndBlock();
        }
    }

    private List<SituationMatchResult> match(NodeAndEvents nodeAndEvents, List<TicketAndEvents> ticketsAndEvents, List<SituationAndEvents> situationsAndEvents) {
        final Map<String, Integer> matchedEvents = nodeAndEvents.getMatchedEvents();

        // Convert to canonical situations for easy comparision
        List<CanonicalSituation> cpnCanonicalSituations = ticketsAndEvents.stream()
                .map(t -> new CanonicalSituation(t, matchedEvents))
                .collect(Collectors.toList());

        List<CanonicalSituation> onmsCanonicalSituations = situationsAndEvents.stream()
                .map(CanonicalSituation::new)
                .collect(Collectors.toList());

        // Now try to match up
        final List<SituationMatchResult> situationMatchResults = new LinkedList<>();
        for (CanonicalSituation cpnCanonicalSituation : cpnCanonicalSituations) {
            CanonicalSituation exactMatch = null;
            final List<CanonicalSituation> partialMatches = new LinkedList<>();

            for (CanonicalSituation onmsCanonicalSituation : onmsCanonicalSituations) {
                if (cpnCanonicalSituation.equals(onmsCanonicalSituation)) {
                    exactMatch = onmsCanonicalSituation;
                    LOG.debug("Found exact match on CPN ticket id: {} to OpenNMS situation id: {}",
                            cpnCanonicalSituation.getSourceId(),
                            onmsCanonicalSituation.getSourceId());
                    break;
                } else if (onmsCanonicalSituation.contains(cpnCanonicalSituation)) {
                    partialMatches.add(onmsCanonicalSituation);
                    LOG.debug("Found partial match (CPN ticket in ONMS situation) on CPN ticket id: {} to OpenNMS situation id: {}",
                            cpnCanonicalSituation.getSourceId(),
                            onmsCanonicalSituation.getSourceId());
                } else if (cpnCanonicalSituation.contains(onmsCanonicalSituation)) {
                    partialMatches.add(onmsCanonicalSituation);
                    LOG.debug("Found partial match (ONMS situation in CPN ticket) on CPN ticket id: {} to OpenNMS situation id: {}",
                            cpnCanonicalSituation.getSourceId(),
                            onmsCanonicalSituation.getSourceId());
                }
            }
            situationMatchResults.add(new SituationMatchResult(cpnCanonicalSituation, exactMatch, partialMatches));
        }

        return situationMatchResults;
    }

    private void printSituationMatches(List<SituationMatchResult> matchResults) {
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow("CPN Ticket ID", "Any Match?", "OpenNMS Situation ID (Exact Match)", "OpenNMS Situation IDs (Partial Matches)");
        at.addRule();

        for (SituationMatchResult matchResult : matchResults) {
            at.addRow(matchResult.getSource().getSourceId(),
                    matchResult.anyMatch() ? "Yes" : "No",
                    naWhenNull(matchResult.getExactMatch() != null ? matchResult.getExactMatch().getSourceId() : null),
                    matchResult.getPartialMatches().isEmpty() ? "N/A" : matchResult.getPartialMatches().stream()
                            .map(CanonicalSituation::getSourceId).collect(Collectors.joining(",")));
            at.addRule();
        }


        CWC_LongestLine cwc = new CWC_LongestLine();
        at.getRenderer().setCWC(cwc);

        System.out.println(at.render());
    }

    private void printEventMatches(Map<String, Integer> pairs, List<? extends MatchingSyslogEventRecord> cpnEvents, List<ESEventDTO> onmsEvents) {
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow("CPN Event ID", "CPN Event Descr", "CPN Event Location", "CPN Event Time", "OpenNMS Event Time", "OpenNMS Event ID", "OpenNMS Event LogMsg");
        at.addRule();

        final List<? extends MatchingSyslogEventRecord> sortedCpnEvents = Lists.newArrayList(cpnEvents);
        sortedCpnEvents.sort(Comparator.comparing(MatchingSyslogEventRecord::getTime)
                .thenComparing(MatchingSyslogEventRecord::getEventId));
        for (MatchingSyslogEventRecord cpnEvent : sortedCpnEvents) {

            Date onmsEventTime = null;
            Integer onmsEventId = pairs.get(cpnEvent.getEventId());
            String onmsEventLogMsg = null;
            if (onmsEventId != null) {
                ESEventDTO onmsEvent = onmsEvents.stream().filter(e -> onmsEventId.equals(e.getId())).findAny().get();
                onmsEventTime = onmsEvent.getTimestamp();
                onmsEventLogMsg = onmsEvent.getLogMessage();
            }

            at.addRow(cpnEvent.getEventId(),
                    cpnEvent.getDescription(),
                    cpnEvent.getLocation(),
                    cpnEvent.getTime(),
                    naWhenNull(onmsEventTime),
                    naWhenNull(onmsEventId),
                    naWhenNull(onmsEventLogMsg));
            at.addRule();
        }


        CWC_LongestLine cwc = new CWC_LongestLine();
        at.getRenderer().setCWC(cwc);

        System.out.println(at.render());
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

    private static String naWhenNull(Date date) {
        return date != null ? date.toString() : "N/A";
    }
}
