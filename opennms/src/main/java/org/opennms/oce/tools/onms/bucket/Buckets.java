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
        node.setOpennmsNodeId(975);

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

        esDataProvider.getTicketRecordsInRange(start, end, tickets -> {
            for (TicketRecord ticket : tickets) {
                addTicketToNode(ticket);
            }
        });

        // Filter CPN data as required and retrieve corresponding ONMS data.
        Collection<Node> filteredNodes = filter(nodesByName.values());
        for (Node n : filteredNodes) {
            n.setOnmsData(getOnmsData(n));
        }
        parseNodes(filteredNodes, start, end);
    }

    private void addTicketToNode(TicketRecord record) {
        Node node = nodesByName.get(getSanitizedLocation(record.getLocation()));
        if (node != null) {
            node.addTicket(new Ticket(record));
        } else {
            System.out.println("Failed to find node for ticket: " + record);
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
            List<Situation> situations = getSituations(node, start, end);
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
                for (Situation s : situations) {
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

    private List<Situation> getSituations(Node node, ZonedDateTime start, ZonedDateTime end) {
        List<Situation> situations = new ArrayList<>();
        List<AlarmDocumentDTO> dtos;
        try {
            dtos = eventClient.getSituationsForHostname(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), node.getOnmsNodeLabel());
        } catch (IOException e) {
            LOG.warn("Error retrieving situations for Node {} on range {} to {} : {}", node.getOnmsNodeLabel(), start, end, e.getMessage());
            return Collections.emptyList();
        }
        for (AlarmDocumentDTO dto : dtos) {
            Situation s = new Situation(dto);
            List<ESEventDTO> events = getSyslogsForSituation(dto.getRelatedAlarmReductionKeys());
            s.setEvents(events);
            situations.add(s);
        }
        return situations;
    }

    private List<ESEventDTO> getSyslogsForSituation(List<String> relatedReductionKeys) {
        try {
            return eventClient.getEventsForReductionKeys(relatedReductionKeys);
        } catch (IOException e) {
            LOG.warn("Failed to retrieve events for Situation {} : {}", relatedReductionKeys, e.getMessage());
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

    private OnmsData getOnmsData(Node node) {
        // FIXME Query ES and collect the appropriate data
        // Situations and Syslogs and Traps for the node (and Date Range)
        return null;
    }

    private Collection<Node> filter(Collection<Node> nodes) {
        // TODO - Do filtering
        // TODO - report out data that is filtered.
        /*
         * Filtering: Filter services from tickets, 
         * filter tickets that only contain service events 
         * Filter tickets that contain a “bad” node Filter tickets with a single “alarm”
         * Filter tickets that affect many nodes (to be revisited) TODO: FIXME: smith
         */
        return nodes;
    }

}
