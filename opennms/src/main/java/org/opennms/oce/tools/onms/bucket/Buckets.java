package org.opennms.oce.tools.onms.bucket;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESClusterConfiguration;
import org.opennms.oce.tools.es.ESConfigurationDao;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.alarmdto.EventDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//
// Bucket of tickets/situations
// Input: List of nodes:
// List of tickets (attached to Nodes)
// List of situations (attached to Nodes)
// Output: buckets with meta-data
public class Buckets {

    private static final Logger LOG = LoggerFactory.getLogger(Buckets.class);

    private List<Match> matches = new ArrayList<>();
    private List<Match> partialMatches = new ArrayList<>();
    private List<Ticket> unmatchedTickets = new ArrayList<>();

    private Map<String, Node> nodesByName;
    private final ESDataProvider esDataProvider;

    public Buckets(ESDataProvider esDataProvider) {
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
        Buckets b = new Buckets(esDataProvider);
        NodeAndFacts node = new NodeAndFacts(System.getProperty("hostname"));
        node.setOpennmsNodeLabel(System.getProperty("host.fqdn"));
        node.setOpennmsNodeId(859);

        b.eventClient = new EventClient(esClient);

        List<NodeAndFacts> nodes = Arrays.asList(node);
        ZonedDateTime start = ZonedDateTime.of(2019, 1, 6, 18, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime end = ZonedDateTime.of(2019, 1, 6, 23, 59, 59, 999, ZoneId.systemDefault());
        try {
            b.run(nodes, start, end);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void run(List<NodeAndFacts> nodes, ZonedDateTime start, ZonedDateTime end) throws IOException {
        // Map NodeAndFacts to bucket nodes DTO
        // Retrieve Tickets and the rest of the CPN data
        nodesByName = nodes.stream().map(n -> new Node(n.getOpennmsNodeId(), n.getOpennmsNodeLabel(), n.getCpnHostname()))
                                         .collect(Collectors.toMap(Node::getCpnHostname, Function.identity()));

        // Retrieve Tickets
        esDataProvider.getTicketRecordsInRange(start, end, tickets -> {
            for (TicketRecord ticket : tickets) {
                addTicketToNode(ticket);
            }
        });

        // Filter CPN data as required and retrieve corresponding ONMS data.
        Collection<Node> filteredNodes = filter(nodesByName.values());
        for (Node n : filteredNodes) {
            getOnmsData(n, start, end);
        }

        // Try the matching
        parseNodes(filteredNodes, start, end);
    }

    private void addTicketToNode(TicketRecord record) {
        Node node = nodesByName.get(getSanitizedLocation(record.getLocation()));
        if (node != null) {
            node.addTicket(new Ticket(record));
        } else {
            node = new Node(0, "", getSanitizedLocation(record.getLocation()));
            node.addTicket(new Ticket(record));
            nodesByName.put(getSanitizedLocation(record.getLocation()), node);
            LOG.info("Failed to find node {} for ticket: {}", getSanitizedLocation(record.getLocation()), record);
        }
    }

    private static String getSanitizedLocation(String location) {
        if (location.contains(":")) {
            return location.substring(0, location.indexOf(":"));
        }
        return location;
    }

    private void parseNodes(Collection<Node> nodes, ZonedDateTime start, ZonedDateTime end) {
        // TODO - sort chrono Tickets and Situations
        // TODO - only search a small sliding window of time
        // TODO - remove matched situations from SituationSet
        for (Node node : nodes) {
            // Populate the Situations we will consider
            for (Ticket t : node.getTickets()) {
                LOG.debug("Attempting to match TICKET {}", t.getId());
                try {
                    t.setTraps(getTicketTraps(t));
                    t.setSyslogs(getTicketSyslogs(t));
                } catch (IOException e) {
                    LOG.warn("Failed to retrieve event for TICKET {} : {}", t.getId(), e.getMessage());
                    continue;
                }
                boolean ticketIsUnmatched = true;
                //
                for (Situation s : node.getSituations()) {
                    if (t.matches(s)) {
                        matches.add(new Match(t, s));
                        ticketIsUnmatched = false;
                        break;
                    }
                    if (t.partiallymatches(s)) {
                        partialMatches.add(new Match(t, s));
                        ticketIsUnmatched = false;
                        break;
                    }
                }
                if (ticketIsUnmatched) {
                    unmatchedTickets.add(t);
                }
            }
        }

        System.out.printf("There were %d matches out of %d tickets:\n\n", matches.size(), nodes.stream().map(Node::getTickets).mapToInt(Set::size).sum());

        System.out.printf("There were %d partial matches:\n\n", partialMatches.size());
        partialMatches.forEach(Buckets::printPartialMatch);

        System.out.printf("There were %d tickets that were not matched:\n\n", unmatchedTickets.size());
        unmatchedTickets.forEach(Buckets::printUnmatchedTicket);
    }

    private List<ESEventDTO> getEventsForAlarms(List<Integer> eventIds) {
        try {
            return eventClient.getEventsByIds(eventIds);
        } catch (IOException e) {
            LOG.warn("Failed to retrieve events for Event IDs {} : {}", eventIds, e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<CpnSyslog> getTicketSyslogs(Ticket t) throws IOException {
        return esDataProvider.getSyslogsInTicket(t.getId()).stream().map(syslog -> new CpnSyslog(syslog)).collect(Collectors.toList());
    }

    private List<CpnTrap> getTicketTraps(Ticket t) throws IOException {
        return esDataProvider.getTrapsInTicket(t.getId()).stream().map(trap -> new CpnTrap(trap)).collect(Collectors.toList());
    }

    private static void printPartialMatch(Match m) {
        System.out.println("Partial Match:");
        System.out.println(m.t);
        System.out.println(m.s);
        System.out.println("-----");
    }

    private static void printUnmatchedTicket(Ticket t) {
        System.out.println("UnMatched Ticket:");
        System.out.println(t);
        System.out.println("-----");
    }

    private void getOnmsData(Node node, ZonedDateTime start, ZonedDateTime end) {
        // Query ES and collect the appropriate data
        // Situations and Syslogs and Traps for the node (and Date Range)
        node.addAllSituations(getSituations(node, start, end));
    }

    // Get reduced Situation, Alarm and Event data for the node over the given time range.
    private Set<Situation> getSituations(Node node, ZonedDateTime start, ZonedDateTime end) {
        // Keyed on Situation/Alarm ID to reduce multiple Alarm Documents to a Single Situation.
        Map<Integer, Situation> situations = new HashMap<>();

        List<AlarmDocumentDTO> situationDtos;
        try {
            situationDtos = eventClient.getSituationsForHostname(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), node.getOnmsNodeLabel());
        } catch (IOException e) {
            LOG.warn("Error retrieving situations for Node {} on range {} to {} : {}", node.getOnmsNodeLabel(), start, end, e.getMessage());
            return Collections.emptySet();
        }

        // Reduce multiple documents to single Situations collecting all relatedReduction Keys and relatedAlarm Ids
        for (AlarmDocumentDTO dto : situationDtos) {
            situations.computeIfAbsent(dto.getId(), k -> new Situation(dto));
            Situation s = situations.get(dto.getId());
            s.addRelatedAlarmIds(dto.getRelatedAlarmIds());
            s.addReductionKeys(dto.getRelatedAlarmReductionKeys());
        }
        LOG.debug("{} Situations DTOs reduced to {} Situations.", situationDtos.size(), situations.size());
        // Retrieve Alarms for each situation, reducing Alarm Documents as above.
        for (Situation s : situations.values()) {
            try {
                s.addRelatedAlarmDtos(eventClient.getAlarmsByIds(s.getRelatedAlarmIds().stream().collect(Collectors.toList()), 
                                                                 start.toInstant().toEpochMilli(), 
                                                                 end.toInstant().toEpochMilli()));
            } catch (IOException e) {
                LOG.warn("Error retrieving ALARMS for Situation {} : {} ", s, e.getMessage());
            }
        }
        // retrieve the related events and add them to the situations
        for (Situation s : situations.values()) {
            List<ESEventDTO> events = getEventsForAlarms(s.getRelatedAlarmDtos().stream()
                                                             .map(AlarmDocumentDTO::getLastEvent)
                                                             .filter(Objects::nonNull)
                                                             .map(EventDocumentDTO::getId)
                                                             .filter(Objects::nonNull)
                                                             .collect(Collectors.toList()));
            s.setEvents(events);
        }
        return situations.values().stream().collect(Collectors.toSet());
    }

    private Collection<Node> filter(Collection<Node> nodes) {
        
        // for now just use node named in System.getProperties()
        return nodes.stream().filter(n -> n.getOnmsNodeId() == 859).collect(Collectors.toSet());
            
        // TODO - Do filtering
        // TODO - report out data that is filtered.
        /*
         * Filtering: Filter services from tickets, 
         * filter tickets that only contain service events 
         * Filter tickets that contain a “bad” node Filter tickets with a single “alarm”
         * Filter tickets that affect many nodes (to be revisited) TODO: FIXME: smith
         */
    }

}
