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

import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
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
import org.opennms.oce.tools.cpn.events.MatchingSyslogEventRecord;
import org.opennms.oce.tools.cpn.events.MatchingTrapEventRecord;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
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
    private final List<String> hostnameSubstringsToFilter;
    private final StateCache stateCache;
    private final boolean csvOutput;

    // Don't consider authentication failure traps
    private static final List<QueryBuilder> cpnEventExcludes = Arrays.asList(termQuery("description.keyword", "SNMP authentication failure"));

    public TSAudit(ESDataProvider esDataProvider, EventClient eventClient, ZonedDateTime start, ZonedDateTime end, List<String> hostnames, boolean csvOutput) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.eventClient = Objects.requireNonNull(eventClient);

        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.startMs = start.toInstant().toEpochMilli();
        this.endMs = end.toInstant().toEpochMilli();

        this.hostnameSubstringsToFilter = Objects.requireNonNull(hostnames);
        this.csvOutput = csvOutput;

        this.stateCache = new StateCache(startMs, endMs);
    }

    public void run() throws IOException, ExecutionException, InterruptedException {
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

        for (NodeAndFacts nodeAndFacts : nodesToProcess) {
            // Gather all the events of interest for the node we want to process
            final NodeAndEvents nodeAndEvents = retrieveAndPairEvents(nodeAndFacts);

            // Print the matches
            printEventMatches(nodeAndEvents.getMatchedTraps(), nodeAndEvents.getCpnTrapEvents(), nodeAndEvents.getOnmsTrapEvents());
            printEventMatches(nodeAndEvents.getMatchedSyslogs(), nodeAndEvents.getCpnSyslogEvents(), nodeAndEvents.getOnmsSyslogEvents());

            // Gather the tickets for the node
            final List<TicketAndEvents> ticketsAndEvents = getTicketsAndPairEvents(nodeAndEvents);

            // Gather the situations for the node
            final List<SituationAndEvents> situationsAndEvents = getSituationsAndPairEvents(nodeAndEvents);

            // Match
            match(nodeAndEvents, ticketsAndEvents, situationsAndEvents);

            // TODO: Further matching, and reporting
        }
    }

    private void match(NodeAndEvents nodeAndEvents, List<TicketAndEvents> ticketsAndEvents, List<SituationAndEvents> situationsAndEvents) {
        final Map<String, Integer> matchedEvents = nodeAndEvents.getMatchedEvents();

        // Convert to canonical situations for easy comparision
        List<CanonicalSituation> cpnCanonicalSituations = ticketsAndEvents.stream()
                .map(t -> new CanonicalSituation(t, matchedEvents))
                .collect(Collectors.toList());

        List<CanonicalSituation> onmsCanonicalSituations = situationsAndEvents.stream()
                .map(CanonicalSituation::new)
                .collect(Collectors.toList());

        // Now try to find exact matches
        for (CanonicalSituation cpnCanonicalSituation : cpnCanonicalSituations) {
            for (CanonicalSituation onmsCanonicalSituation : onmsCanonicalSituations) {
                if (cpnCanonicalSituation.equals(onmsCanonicalSituation)) {
                    System.out.printf("Found exact match on CPN ticket id: %s to OpenNMS situation id: %s\n",
                            cpnCanonicalSituation.getSourceId(),
                            onmsCanonicalSituation.getSourceId());
                } else if (onmsCanonicalSituation.contains(cpnCanonicalSituation)) {
                    System.out.printf("Found partial match (CPN ticket in ONMS situation) on CPN ticket id: %s to OpenNMS situation id: %s\n",
                            cpnCanonicalSituation.getSourceId(),
                            onmsCanonicalSituation.getSourceId());
                } else if (cpnCanonicalSituation.contains(onmsCanonicalSituation)) {
                    System.out.printf("Found partial match (ONMS situation in CPN ticket) on CPN ticket id: %s to OpenNMS situation id: %s\n",
                            cpnCanonicalSituation.getSourceId(),
                            onmsCanonicalSituation.getSourceId());
                }
            }
        }

        // TODO: Better matching and partial matching
    }

    private List<TicketAndEvents> getTicketsAndPairEvents(NodeAndEvents nodeAndEvents) throws IOException {
        // Retrieve the tickets
        final List<TicketRecord> ticketsOnNode = new LinkedList<>();
        esDataProvider.getTicketRecordsInRange(start, end,
                Arrays.asList(
                        matchPhraseQuery("location", nodeAndEvents.getNodeAndFacts().getCpnHostname()), // must be for the node in question
                        termQuery("affectedDevicesCount", 1) // must only affect a single device (this node)
                ),
                Collections.emptyList(),
                ticketsOnNode::addAll);
        LOG.debug("Found {} tickets.", ticketsOnNode.size());

        // Match the events up with the tickets
        final List<TicketAndEvents> ticketsAndEvents = new LinkedList<>();
        for (TicketRecord ticket : ticketsOnNode) {
            final List<EventRecord> eventsInTicket = nodeAndEvents.getCpnEvents().stream()
                    .filter(e -> ticket.getTicketId().equals(e.getTicketId()))
                    .collect(Collectors.toList());

            if (eventsInTicket.isEmpty()) {
                LOG.warn("No events found for ticket: {}", ticket.getTicketId());
                continue;
            }

            final TicketAndEvents ticketAndEvents = new TicketAndEvents(ticket, eventsInTicket);
            ticketsAndEvents.add(ticketAndEvents);
        }
        return ticketsAndEvents;
    }

    private List<SituationAndEvents> getSituationsAndPairEvents(NodeAndEvents nodeAndEvents) throws IOException {
        // Retrieve the situations and alarms
        final int nodeId = nodeAndEvents.getNodeAndFacts().getOpennmsNodeId();
        final List<AlarmDocumentDTO> allSituationDtos = eventClient.getSituationsOnNodeId(startMs, endMs, nodeId);
        final List<AlarmDocumentDTO> alarmDtos = eventClient.getAlarmsInSituationsOnNodeId(startMs, endMs, nodeId);

        // Group the situations by id
        final Map<Integer, List<AlarmDocumentDTO>> situationsById = allSituationDtos.stream()
                .collect(groupingBy(AlarmDocumentDTO::getId));

        // Group the alarms by reduction key
        final Map<String, List<AlarmDocumentDTO>> alarmsByReductionKey = alarmDtos.stream()
                .collect(groupingBy(AlarmDocumentDTO::getReductionKey));

        // Group the events by reduction key
        final Map<String, List<ESEventDTO>> eventsByReductionKey = nodeAndEvents.getOnmsEvents().stream()
                .filter(e -> e.getAlarmReductionKey() != null)
                .collect(groupingBy(ESEventDTO::getAlarmReductionKey));
        // Group the events by clear key
        final Map<String, List<ESEventDTO>> eventsByClearKey = nodeAndEvents.getOnmsEvents().stream()
                .filter(e -> e.getAlarmClearKey() != null)
                .collect(groupingBy(ESEventDTO::getAlarmClearKey));

        // Process each situation
        final List<SituationAndEvents> situationsAndEvents = new LinkedList<>();
        for (List<AlarmDocumentDTO> situationDtos : situationsById.values()) {
            // Gather all of the related reduction keys
            final Set<String> relatedReductionKeys = situationDtos.stream()
                    .flatMap(s -> s.getRelatedAlarmReductionKeys().stream())
                    .collect(Collectors.toSet());

            // Gather the events in the related alarms
            final List<ESEventDTO> allEventsInSituation = new LinkedList<>();
            for (String relatedReductionKey : relatedReductionKeys) {
                // For every related alarm, determine it's lifespan
                final List<AlarmDocumentDTO> relatedAlarmDtos = alarmsByReductionKey.getOrDefault(relatedReductionKey, Collections.emptyList());
                if (relatedAlarmDtos.isEmpty()) {
                    // No events to gather here
                    continue;
                }

                final Lifespan alarmLifespan = getLifespan(relatedAlarmDtos);

                // Now find events that relate to this reduction key in the given timespan
                final List<ESEventDTO> eventsInAlarm = new LinkedList<>();
                eventsByReductionKey.getOrDefault(relatedReductionKey, Collections.emptyList()).stream()
                        .filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs() && e.getTimestamp().getTime() <= alarmLifespan.getEndMs())
                        .forEach(eventsInAlarm::add);

                // Now find events that relate to this clear key in the given timespan
                eventsByClearKey.getOrDefault(relatedReductionKey, Collections.emptyList()).stream()
                        .filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs() && e.getTimestamp().getTime() <= alarmLifespan.getEndMs())
                        .forEach(eventsInAlarm::add);

                allEventsInSituation.addAll(eventsInAlarm);
            }

            if (allEventsInSituation.isEmpty()) {
                LOG.warn("No events found for situation: {}", situationDtos.get(0).getReductionKey());
                continue;
            }

            situationsAndEvents.add(new SituationAndEvents(situationDtos, allEventsInSituation));
        }
        return situationsAndEvents;
    }

    private static Lifespan getLifespan(List<AlarmDocumentDTO> alarmDtos) {
        final long minTime = alarmDtos.stream().mapToLong(AlarmDocumentDTO::getFirstEventTime).min().getAsLong();
        final long maxTime = alarmDtos.stream().mapToLong(AlarmDocumentDTO::getLastEventTime).max().getAsLong();
        return new Lifespan(minTime, maxTime);
    }

    private NodeAndEvents retrieveAndPairEvents(NodeAndFacts nodeAndFacts) throws IOException, ExecutionException, InterruptedException {
        final List<EventRecord> cpnSyslogEvents = new ArrayList<>();
        final List<TrapRecord> cpnTrapEvents = new ArrayList<>();
        final List<ESEventDTO> onmsTrapEvents = new ArrayList<>();
        final List<ESEventDTO> onmsSyslogEvents = new ArrayList<>();

        // Retrieve syslog records for the given host
        LOG.debug("Retrieving CPN syslog records for: {}", nodeAndFacts.getCpnHostname());
        esDataProvider.getSyslogRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndFacts.getCpnHostname())), cpnEventExcludes,  syslogs -> {
            for (EventRecord syslog : syslogs) {
                // Skip clears
                if (EventUtils.isClear(syslog)) {
                    continue;
                }
                cpnSyslogEvents.add(syslog);
            }
        });

        // Retrieve trap records for the given host
        LOG.debug("Retrieving CPN trap records for: {}", nodeAndFacts.getCpnHostname());
        esDataProvider.getTrapRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndFacts.getCpnHostname())), cpnEventExcludes,  traps -> {
            for (TrapRecord trap : traps) {
                // Skip clears
                if (EventUtils.isClear(trap)) {
                    continue;
                }
                cpnTrapEvents.add(trap);
            }
        });

        // Retrieve the ONMS trap events
        LOG.debug("Retrieving ONMS trap events for: {}", nodeAndFacts.getOpennmsNodeLabel());
        onmsTrapEvents.addAll(eventClient.getTrapEvents(startMs, endMs, Arrays.asList(termQuery("nodeid", nodeAndFacts.getOpennmsNodeId()))));

        // Retrieve the ONMS syslog events
        LOG.debug("Retrieving ONMS syslog events for: {}", nodeAndFacts.getOpennmsNodeLabel());
        onmsSyslogEvents.addAll(eventClient.getSyslogEvents(startMs, endMs, Arrays.asList(termQuery("nodeid", nodeAndFacts.getOpennmsNodeId()))));
        LOG.debug("Done retrieving events for host. Found {} CPN syslogs, {} CPN traps, {} ONMS syslogs and {} ONMS traps.",
                cpnSyslogEvents.size(), cpnTrapEvents.size(), onmsSyslogEvents.size(), onmsTrapEvents.size());

        // Perform the matching
        LOG.debug("Matching syslogs...");
        Map<String, Integer> matchedSyslogs = EventMatcher.matchSyslogEventsScopedByTimeAndHost(cpnSyslogEvents, onmsSyslogEvents);
        LOG.info("Matched {} syslog events.", matchedSyslogs.size());

        LOG.debug("Matching traps.");
        Map<String, Integer> matchedTraps = EventMatcher.matchTrapEventsScopedByTimeAndHost(cpnTrapEvents, onmsTrapEvents);
        LOG.debug("Matched {} trap events.", matchedTraps.size());

        return new NodeAndEvents(nodeAndFacts, cpnSyslogEvents, onmsSyslogEvents, matchedSyslogs, cpnTrapEvents, onmsTrapEvents, matchedTraps);
    }

    private void printEventMatches(Map<String, Integer> pairs, List<? extends MatchingSyslogEventRecord> cpnEvents, List<ESEventDTO> onmsEvents) {
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow("CPN Event ID", "CPN Event Descr", "CPN Event Location", "OpenNMS Event ID", "OpenNMS Event LogMsg");
        at.addRule();

        for (MatchingSyslogEventRecord cpnEvent : cpnEvents) {

            Integer onmsEventId = pairs.get(cpnEvent.getEventId());
            String onmsEventLogMsg = null;
            if (onmsEventId != null) {
                ESEventDTO onmsEvent = onmsEvents.stream().filter(e -> onmsEventId.equals(e.getId())).findAny().get();
                onmsEventLogMsg = onmsEvent.getLogMessage();
            }

            at.addRow(cpnEvent.getEventId(),
                    cpnEvent.getDescription(),
                    cpnEvent.getLocation(),
                    naWhenNull(onmsEventId),
                    naWhenNull(onmsEventLogMsg));
            at.addRule();
        }


        CWC_LongestLine cwc = new CWC_LongestLine();
        at.getRenderer().setCWC(cwc);

        System.out.println(at.render());
    }

    private List<NodeAndFacts> getNodesAndFacts() throws IOException {
        final List<NodeAndFacts> nodesAndFacts = new LinkedList<>();

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
            // Apply the hostname filters (could be also done in the query above to improve performance)
            if (hostnameSubstringsToFilter.size() > 0) {
                boolean matched = false;
                for (String hostnameSubstringToFilter : hostnameSubstringsToFilter) {
                    if (hostname.toLowerCase().contains(hostnameSubstringToFilter.toLowerCase())) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    LOG.info("Skipping {}. Does not match given substrings.", hostname);
                    continue;
                }
            }
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
        esDataProvider.getSyslogRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndFacts.getCpnHostname())), cpnEventExcludes,  syslogs -> {
            for (EventRecord syslog : syslogs) {
                // Skip clears
                if (EventUtils.isClear(syslog)) {
                    continue;
                }

                Date processedTime = syslog.getTime();
                Date messageTime;

                // Parse the syslog record and extract the date from the message
                try {
                    GenericSyslogMessage parsedSyslogMessage = GenericSyslogMessage.fromCpn(syslog.getEventId(), nodeAndFacts.getCpnHostname(), syslog.getDetailedDescription());
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
        });

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
