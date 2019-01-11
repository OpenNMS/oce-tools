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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
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
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
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
    private final StateCache stateCache;
    private final boolean csvOutput;
    private final boolean restServerEnabled;

    // Don't consider authentication failure traps
    private static final List<QueryBuilder> cpnEventExcludes = Arrays.asList(termQuery("description.keyword", "SNMP authentication failure"));

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

        final List<NodeAndEvents> allNodesAndEvents = new LinkedList<>();
        final Map<Integer, List<TicketAndEvents>> allTicketAndEvents = new HashMap<>();
        final Map<Integer, List<SituationAndEvents>> allSituationAndEvents = new HashMap<>();

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
        final List<AlarmDocumentDTO> alarmDtos = eventClient.getAlarmsOnNodeId(startMs, endMs, nodeId);

        // Group the situations by id
        final Map<Integer, List<AlarmDocumentDTO>> situationsById = allSituationDtos.stream()
                .collect(groupingBy(AlarmDocumentDTO::getId));

        // Group the alarms by alarm id
        final Map<Integer, List<AlarmDocumentDTO>> alarmsById = alarmDtos.stream()
                .collect(groupingBy(AlarmDocumentDTO::getId));

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
            final Lifespan situationLifespan = getLifespan(situationDtos, startMs, endMs);
            final List<OnmsAlarmSummary> alarmSummaries = new LinkedList<>();
            final String situationReductionKey = situationDtos.get(0).getReductionKey();
            // Gather all of the related alarm ids
            final Set<Integer> relatedAlarmIds = situationDtos.stream()
                    .flatMap(s -> s.getRelatedAlarmIds().stream())
                    .collect(Collectors.toSet());

            // Gather the events in the related alarms
            boolean didFindAlarmDocumentsForAtLeaseOneRelatedAlarm = false;
            final List<ESEventDTO> allEventsInSituation = new LinkedList<>();
            for (Integer relatedAlarmId : relatedAlarmIds) {
                // For every related alarm, determine it's lifespan
                final List<AlarmDocumentDTO> relatedAlarmDtos = alarmsById.getOrDefault(relatedAlarmId, Collections.emptyList());
                if (relatedAlarmDtos.isEmpty()) {
                    LOG.warn("No alarms documents found for related alarm id: {} on situation with reduction key: {}",
                            relatedAlarmId, situationReductionKey);

                    // No events to gather here
                    continue;
                }
                didFindAlarmDocumentsForAtLeaseOneRelatedAlarm = true;

                // Grab the reduction key from the first document, it must be the same on the remainder of
                // the documents for the same id
                final String relatedReductionKey = relatedAlarmDtos.iterator().next().getReductionKey();

                // Now find events that relate to this reduction key in the computed lifespan
                final Lifespan alarmLifespan = getLifespan(relatedAlarmDtos, startMs, endMs);
                final List<ESEventDTO> eventsInAlarm = new LinkedList<>();
                eventsByReductionKey.getOrDefault(relatedReductionKey, Collections.emptyList()).stream()
                        .filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs() && e.getTimestamp().getTime() <= alarmLifespan.getEndMs())
                        .forEach(eventsInAlarm::add);

                // Now find events that relate to this clear key in the given lifespan
                eventsByClearKey.getOrDefault(relatedReductionKey, Collections.emptyList()).stream()
                        .filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs() && e.getTimestamp().getTime() <= alarmLifespan.getEndMs())
                        .forEach(eventsInAlarm::add);

                if (eventsInAlarm.isEmpty()) {
                    LOG.warn("No events found for alarm with reduction key: {}", relatedReductionKey);
                }

                allEventsInSituation.addAll(eventsInAlarm);

                // Build the alarm summary
                final String logMessage = relatedAlarmDtos.iterator().next().getLogMessage();
                final OnmsAlarmSummary alarmSummary = new OnmsAlarmSummary(relatedAlarmId, relatedReductionKey, alarmLifespan, logMessage, eventsInAlarm);
                alarmSummaries.add(alarmSummary);
            }

            if (!didFindAlarmDocumentsForAtLeaseOneRelatedAlarm) {
                LOG.warn("No alarms found for situation: {}", situationReductionKey);
                continue;
            } else if (allEventsInSituation.isEmpty()) {
                LOG.warn("No events found for situation: {}", situationReductionKey);
                continue;
            }

            situationsAndEvents.add(new SituationAndEvents(situationDtos, situationLifespan, allEventsInSituation, alarmSummaries));
        }
        return situationsAndEvents;
    }

    /**
     * Compute the lifespan of an alarm given some subset of the it's alarm documents.
     */
    private static Lifespan getLifespan(List<AlarmDocumentDTO> alarmDtos, long startMs, long endMs) {
        // Use the first-event time of the first record, or default to startMs if none was found
        final long minTime = alarmDtos.stream()
                .filter(a -> a.getDeletedTime() == null)
                .findAny().map(AlarmDocumentDTO::getFirstEventTime)
                .orElse(startMs);

        // Use the delete time, or default to endMs if none was found
        final long maxTime = alarmDtos.stream()
                .filter(a -> a.getDeletedTime() != null)
                .findAny().map(AlarmDocumentDTO::getDeletedTime)
                .orElse(endMs);

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

    private static String naWhenNull(Date date) {
        return date != null ? date.toString() : "N/A";
    }
}
