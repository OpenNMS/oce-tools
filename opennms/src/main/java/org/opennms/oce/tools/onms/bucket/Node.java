package org.opennms.oce.tools.onms.bucket;

import java.util.HashSet;
import java.util.Set;

public class Node {

    int onmsNodeId;

    String onmsNodeLabel;
    // hostname in location field
    String cpnHostname;

    public Node(int id, String label, String cpnHostname) {
        this.onmsNodeId = id;
        this.onmsNodeLabel = label;
        this.cpnHostname = cpnHostname;
    }

    // CPN DATA
    private final Set<Ticket> tickets = new HashSet<>();

    // ONMS DATA
    private final Set<Situation> situations = new HashSet<>();

    public String getCpnHostname() {
        return cpnHostname;
    }

    public int getOnmsNodeId() {
        return onmsNodeId;
    }

    public String getOnmsNodeLabel() {
        return onmsNodeLabel;
    }

    public Set<Situation> getSituations() {
        return situations;
    }

    public Set<Ticket> getTickets() {
        return tickets;
    }

    public void setOnmsData(OnmsData onmsData) {
        // TODO Auto-generated method stub
        // Add ONMS data to this node -
        // FIXME onmsData.getSituations.stream().filter(s -> s.getNode());
    }

    public void setOnmsNodeId(int onmsNodeId) {
        this.onmsNodeId = onmsNodeId;
    }

    public void setOnmsNodeLabel(String onmsNodeLabel) {
        this.onmsNodeLabel = onmsNodeLabel;
    }

    public void addAllSituations(Set<Situation> situations) {
        this.situations.addAll(situations);
    }

    public void addAllTickets(Set<Ticket> tickets) {
        this.tickets.addAll(tickets);
    }

    public void addSituation(Situation situation) {
        situations.add(situation);
    }

    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
    }

}
