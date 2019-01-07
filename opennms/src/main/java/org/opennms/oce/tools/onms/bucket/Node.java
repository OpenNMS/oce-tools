package org.opennms.oce.tools.onms.bucket;

import java.util.HashSet;
import java.util.Set;

public class Node {

    int onmsNodeId;

    // hostname in location field
    String onmsNodeLabel;

    // CPN DATA
    private final Set<Ticket> tickets = new HashSet<>();

    // ONMS DATA
    private final Set<Situation> situations = new HashSet<>();

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

    public void addSituation(Situation situations) {
        this.situations.add(situations);
    }

    public void addTicket(Ticket tickets) {
        this.tickets.add(tickets);
    }

}
