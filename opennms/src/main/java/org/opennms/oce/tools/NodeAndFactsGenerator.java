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

package org.opennms.oce.tools;

import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.QueryBuilder;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.EventMatcher;
import org.opennms.oce.tools.tsaudit.GenericSyslogMessage;
import org.opennms.oce.tools.tsaudit.Lifespan;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.opennms.oce.tools.tsaudit.StateCache;
import org.opennms.oce.tools.tsaudit.TicketAndEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeAndFactsGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(NodeAndFactsGenerator.class);
    
    private final ESDataProvider esDataProvider;
    private final EventClient eventClient;
    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final List<String> hostnameSubstringsToFilter;
    private final StateCache stateCache;
    private final List<QueryBuilder> cpnEventExcludes;

    private final long startMs;
    private final long endMs;

    private NodeAndFactsGenerator(ESDataProvider esDataProvider, EventClient eventClient, ZonedDateTime start,
                                 ZonedDateTime end, List<String> hostnameSubstringsToFilter,
                                 List<QueryBuilder> cpnEventExcludes) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.eventClient = Objects.requireNonNull(eventClient);
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.hostnameSubstringsToFilter = Objects.requireNonNull(hostnameSubstringsToFilter);
        this.cpnEventExcludes = Objects.requireNonNull(cpnEventExcludes);
        
        startMs = start.toInstant().toEpochMilli();
        endMs=end.toInstant().toEpochMilli();
        stateCache = new StateCache(startMs, endMs);
    }

    public static class NodeAndFactsGeneratorBuilder {
        private ESDataProvider esDataProvider;
        private EventClient eventClient;
        private ZonedDateTime start;
        private ZonedDateTime end;
        private List<String> hostnameSubstringsToFilter = Collections.emptyList();
        private List<QueryBuilder> cpnEventExcludes = Collections.emptyList();

        private NodeAndFactsGeneratorBuilder() {
        }

        public NodeAndFactsGeneratorBuilder setEsDataProvider(ESDataProvider esDataProvider) {
            this.esDataProvider = esDataProvider;
            return this;
        }

        public NodeAndFactsGeneratorBuilder setEventClient(EventClient eventClient) {
            this.eventClient = eventClient;
            return this;
        }

        public NodeAndFactsGeneratorBuilder setStart(ZonedDateTime start) {
            this.start = start;
            return this;
        }

        public NodeAndFactsGeneratorBuilder setEnd(ZonedDateTime end) {
            this.end = end;
            return this;
        }

        public NodeAndFactsGeneratorBuilder setHostnameSubstringsToFilter(List<String> hostnameSubstringsToFilter) {
            this.hostnameSubstringsToFilter = hostnameSubstringsToFilter;
            return this;
        }

        public NodeAndFactsGeneratorBuilder setCpnEventExcludes(List<QueryBuilder> cpnEventExcludes) {
            this.cpnEventExcludes = cpnEventExcludes;
            return this;
        }

        public NodeAndFactsGenerator build() {
            return new NodeAndFactsGenerator(esDataProvider, eventClient, start, end, hostnameSubstringsToFilter, cpnEventExcludes);
        }
    }

    public static NodeAndFactsGeneratorBuilder newBuilder() {
        return new NodeAndFactsGeneratorBuilder();
    }

    public List<NodeAndFacts> getNodesAndFacts() throws IOException {
        // Build the unique set of hostnames by scrolling through all locations and extracting
        // the hostname portion
        final Set<String> hostnames = new LinkedHashSet<>();
        esDataProvider.getDistinctLocations(start, end, cpnEventExcludes, locations -> {
            for (String location : locations) {
                hostnames.add(EventUtils.getNodeLabelFromLocation(location));
            }
        });

        return getNodesAndFacts(hostnames);
    }

    public List<NodeAndFacts> getNodesAndFacts(Set<String> hostnames) throws IOException {
        final List<NodeAndFacts> nodesAndFacts = new LinkedList<>();
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
                } catch (NullPointerException npe) {
                    // skip
                    continue;
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

    public NodeAndEvents retrieveAndPairEvents(NodeAndFacts nodeAndFacts) throws IOException {
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

    public List<TicketAndEvents> getTicketsAndPairEvents(NodeAndEvents nodeAndEvents) throws IOException {
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

    public List<SituationAndEvents> getSituationsAndPairEvents(NodeAndEvents nodeAndEvents) throws IOException {
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
                String moInstance = relatedAlarmDtos.iterator().next().getManagedObjectInstance();
                String moType = relatedAlarmDtos.iterator().next().getManagedObjectType();
                final OnmsAlarmSummary alarmSummary = new OnmsAlarmSummary(relatedAlarmId, relatedReductionKey, alarmLifespan, logMessage, moInstance, moType, eventsInAlarm);
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
}
