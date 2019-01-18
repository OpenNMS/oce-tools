package org.opennms.oce.tools.tsaudit;

import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
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

import org.elasticsearch.index.query.QueryBuilder;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESClusterConfiguration;
import org.opennms.oce.tools.es.ESConfigurationDao;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//
// Bucket of tickets/situations
// Input: List of nodes:
// List of tickets (attached to Nodes)
// List of situations (attached to Nodes)
// Output: buckets with meta-data
public class TicketMatcher {

    private static final Logger LOG = LoggerFactory.getLogger(TicketMatcher.class);

    private List<Match> matches = new ArrayList<>();
    private List<Match> partialMatches = new ArrayList<>();
    private List<TicketAndEvents> unmatchedTickets = new ArrayList<>();

    private final ESDataProvider esDataProvider;

    private final Date started = new Date();

    public TicketMatcher(ESDataProvider esDataProvider) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
    }

    EventClient eventClient;

    // TODO - delete main method when done.
    public static void main(String[] args) {
        File esConfigFile = Paths.get(System.getProperty("user.home"), ".oce", "es-config.yaml").toFile();
        ESConfigurationDao dao = new ESConfigurationDao(esConfigFile);
        ESClusterConfiguration clusterConfiguration = dao.getConfig().getFirstCluster();

        ESClient esClient = new ESClient(clusterConfiguration);
        ESDataProvider esDataProvider = new ESDataProvider(esClient);
        TicketMatcher b = new TicketMatcher(esDataProvider);
        NodeAndFacts node = new NodeAndFacts(System.getProperty("hostname"));
        node.setOpennmsNodeLabel(System.getProperty("host.fqdn"));
        node.setOpennmsNodeId(859);

        b.eventClient = new EventClient(esClient);

        List<NodeAndFacts> nodes = Arrays.asList(node);
        ZonedDateTime start = ZonedDateTime.of(2019, 1, 6, 18, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime end = ZonedDateTime.of(2019, 1, 6, 23, 59, 59, 999, ZoneId.systemDefault());
        try {
            b.run(nodes, start, end);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void run(List<NodeAndFacts> nodes, ZonedDateTime start, ZonedDateTime end) throws IOException, ExecutionException, InterruptedException {
        /////////////////////////////////////////////
        ///// SETUP START - FOR TESTING ONLY
        /////////////////////////////////////////////
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.startMs = start.toInstant().toEpochMilli();
        this.endMs = end.toInstant().toEpochMilli();
        stateCache = new StateCache(startMs, endMs);

        final List<NodeAndFacts> nodesAndFacts = getNodesAndFacts();
        if (nodesAndFacts.isEmpty()) {
            System.out.println("No nodes found.");
            return;
        }
        final List<NodeAndFacts> nodesToProcess = nodesAndFacts.stream().filter(NodeAndFacts::shouldProcess).collect(Collectors.toList());
        for (NodeAndFacts nodeAndFacts : nodesToProcess) {
            if (!nodeAndFacts.getCpnHostname().equals("kc7o24-red2")) {
                continue;
            }
            NodeAndEvents nodeAndEvents = retrieveAndPairEvents(nodeAndFacts);
            // Gather the tickets for the node
            final List<TicketAndEvents> tickets = getTicketsAndPairEvents(nodeAndEvents);
            // Gather the situations for the node
            final List<SituationAndEvents> situations = getSituationsAndPairEvents(nodeAndEvents);

            /////////////////////////////////////////////
            ////// DONE SETUP - for TESTING ONLY
            /////////////////////////////////////////////

            // Try the matching
            matchTicketsAndSituations(nodeAndEvents, tickets, situations);

        }
    }

    // Attempt to match all of the Tickets on the Node during the time window.
    public List<List<Match>> matchTicketsAndSituations(NodeAndEvents nodeAndEvents, List<TicketAndEvents> tickets, List<SituationAndEvents> situations) {
        // a modifiable list - remove situations when matched.
        List<SituationAndEvents> matchableSituations = new ArrayList<>();
        matchableSituations.addAll(situations);

        for (TicketAndEvents ticket : tickets) {
            boolean ticketIsUnmatched = true;
            //
            for (Iterator<SituationAndEvents> iterator = matchableSituations.iterator(); iterator.hasNext();) {
                SituationAndEvents situation = iterator.next();
                if (matches(nodeAndEvents, ticket, situation)) {
                    matches.add(new Match(ticket, situation));
                    ticketIsUnmatched = false;
                    matchableSituations.remove(situation);
                    break;
                }
                if (partiallyMatches(nodeAndEvents, ticket, situation)) {
                    partialMatches.add(new Match(ticket, situation));
                    ticketIsUnmatched = false;
                    matchableSituations.remove(situation);
                    break;
                }
            }
            if (ticketIsUnmatched) {
                unmatchedTickets.add(ticket);
            }
        }

        Date finish = new Date();

        System.out.printf("There were %d matches out of %d tickets:\n\n", matches.size(), tickets.size());

        System.out.printf("There were %d partial matches:\n\n", partialMatches.size());
        partialMatches.forEach(TicketMatcher::printPartialMatch);

        System.out.printf("There were %d tickets that were not matched:\n\n", unmatchedTickets.size());
        // unmatchedTickets.forEach(Buckets::printUnmatchedTicket);

        LOG.info("TIMER::: started: {} and finished: {}", started, finish);

        return Arrays.asList(matches, partialMatches);
    }

    private boolean partiallyMatches(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        return anySyslogMatched(nodeAndEvents, ticket, situation) || anyTrapMatched(nodeAndEvents, ticket, situation);
    }

    private boolean anyTrapMatched(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        List<String> ticketTrapIds = getTrapIdsForTicket(nodeAndEvents, ticket);
        List<Integer> situationTrapIds = getTrapIdsForSituation(nodeAndEvents, situation);
        Map<String, Integer> trapMatches = nodeAndEvents.getMatchedEvents();
        boolean anyTrapMatched = ticketTrapIds.stream().anyMatch(trapId -> eventMatch(trapId, trapMatches, situationTrapIds));
        return anyTrapMatched;
    }

    private List<String> getTrapIdsForTicket(NodeAndEvents nodeAndEvents, TicketAndEvents ticket) {
        List<String> ticketEventIds = ticket.getEvents().stream().map(EventRecord::getEventId).collect(Collectors.toList());
        return nodeAndEvents.getCpnTrapEvents().stream().map(TrapRecord::getEventId).filter(id -> ticketEventIds.contains(id)).collect(Collectors.toList());
    }

    private List<Integer> getTrapIdsForSituation(NodeAndEvents nodeAndEvents, SituationAndEvents situation) {
        List<Integer> onmsNodeTrapEventIds = nodeAndEvents.getOnmsTrapEvents().stream().map(ESEventDTO::getId).collect(Collectors.toList());
        return situation.getEventsInSituation().stream().map(ESEventDTO::getId).filter(id -> onmsNodeTrapEventIds.contains(id)).collect(Collectors.toList());
    }

    private boolean anySyslogMatched(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        List<String> ticketSyslogIds = getSyslogIdsForTicket(nodeAndEvents, ticket);
        List<Integer> situationSyslogIds = getSyslogIdsForSituation(nodeAndEvents, situation);
        Map<String, Integer> syslogMatches = nodeAndEvents.getMatchedEvents();
        boolean anySyslogMatched = ticketSyslogIds.stream().anyMatch(syslogId -> eventMatch(syslogId, syslogMatches, situationSyslogIds));
        return anySyslogMatched;
    }

    private List<String> getSyslogIdsForTicket(NodeAndEvents nodeAndEvents, TicketAndEvents ticket) {
        List<String> ticketEventIds = ticket.getEvents().stream().map(EventRecord::getEventId).collect(Collectors.toList());
        return nodeAndEvents.getCpnSyslogEvents().stream().map(EventRecord::getEventId).filter(id -> ticketEventIds.contains(id)).collect(Collectors.toList());
    }

    private List<Integer> getSyslogIdsForSituation(NodeAndEvents nodeAndEvents, SituationAndEvents situation) {
        List<Integer> onmsNodeSyslogEventIds = nodeAndEvents.getOnmsSyslogEvents().stream().map(ESEventDTO::getId).collect(Collectors.toList());
        return situation.getEventsInSituation().stream().map(ESEventDTO::getId).filter(id -> onmsNodeSyslogEventIds.contains(id)).collect(Collectors.toList());
    }

    private boolean eventMatch(String cpnEventId, Map<String, Integer> eventMatches, List<Integer> onmsEventIds) {
        if (eventMatches == null || onmsEventIds == null) {
            return false;
        }
        return eventMatches.containsKey(cpnEventId) && 
                eventMatches.get(cpnEventId) != null && 
                onmsEventIds.contains(eventMatches.get(cpnEventId));
    }

    private boolean matches(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        return equalNumberOfSyslogs(nodeAndEvents, ticket, situation) && equalNumberOfTraps(nodeAndEvents, ticket, situation) && 
                allSyslogMatched(nodeAndEvents, ticket, situation) && allTrapMatched(nodeAndEvents, ticket, situation);
    }

    private boolean equalNumberOfTraps(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        return getSyslogIdsForTicket(nodeAndEvents, ticket).size() == getTrapIdsForSituation(nodeAndEvents, situation).size();
    }

    private boolean equalNumberOfSyslogs(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        return getSyslogIdsForTicket(nodeAndEvents, ticket).size() == getSyslogIdsForSituation(nodeAndEvents, situation).size();
    }

    private boolean allTrapMatched(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        // return traps.stream().allMatch(trap -> trap.matchesAny(s.getTraps()));
        List<String> ticketTrapIds = getTrapIdsForTicket(nodeAndEvents, ticket);
        List<Integer> situationTrapIds = getTrapIdsForSituation(nodeAndEvents, situation);
        Map<String, Integer> trapMatches = nodeAndEvents.getMatchedEvents();
        boolean allTrapMatched = ticketTrapIds.stream().allMatch(trapId -> eventMatch(trapId, trapMatches, situationTrapIds));
        return allTrapMatched;
    }

    private boolean allSyslogMatched(NodeAndEvents nodeAndEvents, TicketAndEvents ticket, SituationAndEvents situation) {
        List<String> ticketSyslogIds = getSyslogIdsForTicket(nodeAndEvents, ticket);
        List<Integer> situationSyslogIds = getSyslogIdsForSituation(nodeAndEvents, situation);
        Map<String, Integer> syslogMatches = nodeAndEvents.getMatchedEvents();
        boolean allSyslogMatched = ticketSyslogIds.stream().allMatch(syslogId -> eventMatch(syslogId, syslogMatches, situationSyslogIds));
        return allSyslogMatched;
    }

    private static void printPartialMatch(Match m) {
        System.out.println("Partial Match:");
        System.out.println("TICKET[" + m.ticket.getTicket().getTicketId() + "]");
        System.out.println("SITUATION" + m.situation.getSituationDtos().stream().map(AlarmDocumentDTO::getId).distinct().collect(Collectors.toList()));
        System.out.println("-----");
    }

    ////////// REMOVE all of these copies of LCOAL METHODS>...
    private static final List<QueryBuilder> cpnEventExcludes = Arrays.asList(termQuery("description.keyword", "SNMP authentication failure"));

    private ZonedDateTime start;

    private ZonedDateTime end;

    private long startMs;

    private long endMs;

    private NodeAndEvents retrieveAndPairEvents(NodeAndFacts nodeAndFacts) throws IOException {
        final List<EventRecord> cpnSyslogEvents = new ArrayList<>();
        final List<TrapRecord> cpnTrapEvents = new ArrayList<>();
        final List<ESEventDTO> onmsTrapEvents = new ArrayList<>();
        final List<ESEventDTO> onmsSyslogEvents = new ArrayList<>();

        // Retrieve syslog records for the given host
        LOG.debug("Retrieving CPN syslog records for: {}", nodeAndFacts.getCpnHostname());
        esDataProvider.getSyslogRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndFacts.getCpnHostname())), cpnEventExcludes, syslogs -> {
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
        esDataProvider.getTrapRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndFacts.getCpnHostname())), cpnEventExcludes, traps -> {
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
        LOG.debug("Done retrieving events for host. Found {} CPN syslogs, {} CPN traps, {} ONMS syslogs and {} ONMS traps.", cpnSyslogEvents.size(), cpnTrapEvents.size(),
                  onmsSyslogEvents.size(), onmsTrapEvents.size());

        // Perform the matching
        LOG.debug("Matching syslogs...");
        Map<String, Integer> matchedSyslogs = EventMatcher.matchSyslogEventsScopedByTimeAndHost(cpnSyslogEvents, onmsSyslogEvents);
        LOG.info("Matched {} syslog events.", matchedSyslogs.size());

        LOG.debug("Matching traps.");
        Map<String, Integer> matchedTraps = EventMatcher.matchTrapEventsScopedByTimeAndHost(cpnTrapEvents, onmsTrapEvents);
        LOG.debug("Matched {} trap events.", matchedTraps.size());

        return new NodeAndEvents(nodeAndFacts, cpnSyslogEvents, onmsSyslogEvents, matchedSyslogs, cpnTrapEvents, onmsTrapEvents, matchedTraps);
    }

    private StateCache stateCache;

    private void findOpennmsNodeInfo(NodeAndFacts nodeAndFacts) throws IOException {
        LOG.debug("Trying to find OpenNMS node info for hostname: {}", nodeAndFacts.getCpnHostname());
        if (stateCache.findOpennmsNodeInfo(nodeAndFacts)) {
            LOG.debug("Results successfully loaded from cache.");
            return;
        }

        final Optional<ESEventDTO> firstEvent = eventClient.findFirstEventForNodeLabelPrefix(startMs, endMs, nodeAndFacts.getCpnHostname());
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
        nodesAndFacts.sort(Comparator.comparing(NodeAndFacts::shouldProcess).thenComparing(n -> n.getNumCpnSyslogs() != null ? n.getNumCpnSyslogs()
            : 0).thenComparing(n -> n.getNumCpnTraps() != null ? n.getNumCpnTraps() : 0).reversed());

        return nodesAndFacts;
    }

    private void detectClockSkewUsingSyslogEvents(NodeAndFacts nodeAndFacts) throws IOException {
        LOG.debug("Detecting clock skew for hostname: {}", nodeAndFacts.getCpnHostname());
        final List<Long> deltas = new LinkedList<>();
        final AtomicReference<Date> minTimeRef = new AtomicReference<>(new Date());
        final AtomicReference<Date> maxTimeRef = new AtomicReference<>(new Date(0));

        // Retrieve syslog records for the given host
        esDataProvider.getSyslogRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndFacts.getCpnHostname())), cpnEventExcludes, syslogs -> {
            for (EventRecord syslog : syslogs) {
                // Skip clears
                if (EventUtils.isClear(syslog)) {
                    continue;
                }

                Date processedTime = syslog.getTime();
                Date messageTime;

                // Parse the syslog record and extract the date from the message
                GenericSyslogMessage parsedSyslogMessage = GenericSyslogMessage.fromCpn(syslog.getEventId(),
                        nodeAndFacts.getCpnHostname(), syslog.getDetailedDescription());
                messageTime = parsedSyslogMessage.getDate();

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
            OptionalDouble avg = deltas.stream().mapToLong(d -> d).average();
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

    private List<TicketAndEvents> getTicketsAndPairEvents(NodeAndEvents nodeAndEvents) throws IOException {
        // Retrieve the tickets
        final List<TicketRecord> ticketsOnNode = new LinkedList<>();
        esDataProvider.getTicketRecordsInRange(start, end, Arrays.asList(matchPhraseQuery("location", nodeAndEvents.getNodeAndFacts().getCpnHostname()), // must be for the node in
                                                                                                                                                         // question
                                                                         termQuery("affectedDevicesCount", 1) // must only affect a single device (this node)
        ), Collections.emptyList(), ticketsOnNode::addAll);
        LOG.debug("Found {} tickets.", ticketsOnNode.size());

        // Match the events up with the tickets
        final List<TicketAndEvents> ticketsAndEvents = new LinkedList<>();
        for (TicketRecord ticket : ticketsOnNode) {
            final List<EventRecord> eventsInTicket = nodeAndEvents.getCpnEvents().stream().filter(e -> ticket.getTicketId().equals(e.getTicketId())).collect(Collectors.toList());

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
        final Integer nodeId = nodeAndEvents.getNodeAndFacts().getOpennmsNodeId();
        final List<AlarmDocumentDTO> allSituationDtos = eventClient.getSituationsOnNodeId(startMs, endMs, nodeId);
        final List<AlarmDocumentDTO> alarmDtos = eventClient.getAlarmsOnNodeId(startMs, endMs, nodeId);

        // Group the situations by id
        final Map<Integer, List<AlarmDocumentDTO>> situationsById = allSituationDtos.stream().collect(groupingBy(AlarmDocumentDTO::getId));

        // Group the alarms by alarm id
        final Map<Integer, List<AlarmDocumentDTO>> alarmsById = alarmDtos.stream().collect(groupingBy(AlarmDocumentDTO::getId));

        // Group the events by reduction key
        final Map<String, List<ESEventDTO>> eventsByReductionKey = nodeAndEvents.getOnmsEvents().stream().filter(e -> e.getAlarmReductionKey() != null).collect(groupingBy(ESEventDTO::getAlarmReductionKey));
        // Group the events by clear key
        final Map<String, List<ESEventDTO>> eventsByClearKey = nodeAndEvents.getOnmsEvents().stream().filter(e -> e.getAlarmClearKey() != null).collect(groupingBy(ESEventDTO::getAlarmClearKey));

        // Process each situation
        final List<SituationAndEvents> situationsAndEvents = new LinkedList<>();
        for (List<AlarmDocumentDTO> situationDtos : situationsById.values()) {
            final Lifespan situationLifespan = getLifespan(situationDtos, startMs, endMs);
            final List<OnmsAlarmSummary> alarmSummaries = new LinkedList<>();
            final String situationReductionKey = situationDtos.get(0).getReductionKey();
            // Gather all of the related alarm ids
            final Set<Integer> relatedAlarmIds = situationDtos.stream().flatMap(s -> s.getRelatedAlarmIds().stream()).collect(Collectors.toSet());

            // Gather the events in the related alarms
            boolean didFindAlarmDocumentsForAtLeaseOneRelatedAlarm = false;
            final List<ESEventDTO> allEventsInSituation = new LinkedList<>();
            for (Integer relatedAlarmId : relatedAlarmIds) {
                // For every related alarm, determine it's lifespan
                final List<AlarmDocumentDTO> relatedAlarmDtos = alarmsById.getOrDefault(relatedAlarmId, Collections.emptyList());
                if (relatedAlarmDtos.isEmpty()) {
                    LOG.warn("No alarms documents found for related alarm id: {} on situation with reduction key: {}", relatedAlarmId, situationReductionKey);

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
                eventsByReductionKey.getOrDefault(relatedReductionKey, Collections.emptyList()).stream().filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs()
                        && e.getTimestamp().getTime() <= alarmLifespan.getEndMs()).forEach(eventsInAlarm::add);

                // Now find events that relate to this clear key in the given lifespan
                eventsByClearKey.getOrDefault(relatedReductionKey, Collections.emptyList()).stream().filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs()
                        && e.getTimestamp().getTime() <= alarmLifespan.getEndMs()).forEach(eventsInAlarm::add);

                if (eventsInAlarm.isEmpty()) {
                    LOG.warn("No events found for alarm with reduction key: {}", relatedReductionKey);
                }

                allEventsInSituation.addAll(eventsInAlarm);

                // Build the alarm summary
                final String logMessage = relatedAlarmDtos.iterator().next().getLogMessage();
                String nodeLabel = nodeAndEvents.getNodeAndFacts().getOpennmsNodeLabel();
                final OnmsAlarmSummary alarmSummary = new OnmsAlarmSummary(relatedAlarmId, relatedReductionKey, alarmLifespan, logMessage, nodeId.toString(), nodeLabel, eventsInAlarm);
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
        final long minTime = alarmDtos.stream().filter(a -> a.getDeletedTime() == null).findAny().map(AlarmDocumentDTO::getFirstEventTime).orElse(startMs);

        // Use the delete time, or default to endMs if none was found
        final long maxTime = alarmDtos.stream().filter(a -> a.getDeletedTime() != null).findAny().map(AlarmDocumentDTO::getDeletedTime).orElse(endMs);

        return new Lifespan(minTime, maxTime);
    }

}
