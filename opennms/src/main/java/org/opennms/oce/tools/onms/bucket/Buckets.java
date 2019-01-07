package org.opennms.oce.tools.onms.bucket;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;

//
// Bucket of tickets/situations
// Input: List of nodes:
// List of tickets (attached to Nodes)
// List of situations (attached to Nodes)
// Output: buckets with meta-data
public class Buckets {

    private List<Match> matches = new ArrayList<>();
    private List<Match> partialMatches = new ArrayList<>();
    private List<Ticket> unmatchedTickets = new ArrayList<>();

    private Map<String, Node> nodesByName;
    private final ESDataProvider esDataProvider;

    public Buckets(ESDataProvider esDataProvider) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
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
        parseNodes(filteredNodes);
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

    private void parseNodes(Collection<Node> nodes) {
        // TODO - sort chrono Tickets and Situations
        // TODO - only search a small sliding window of time
        // TODO - remove matched situations from SituationSet
        for (Node node : nodes) {
            Set<Situation> situations = node.getSituations();
            for (Ticket t : node.getTickets()) {
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

        System.out.printf("There were %d matches out of %d tickets", matches.size(), nodes.stream().map(Node::getTickets).count());

        System.out.printf("There were %d partial matches", partialMatches.size());
        partialMatches.forEach(Buckets::printPartialMatch);

        System.out.printf("There were %d tickets that were not matched", unmatchedTickets.size());
        unmatchedTickets.forEach(Buckets::printUnmatchedTicket);
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
        // FIXME - Do filtering
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
