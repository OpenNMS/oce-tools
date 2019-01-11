package org.opennms.oce.tools.tsaudit;

public class Match {

    public final TicketAndEvents ticket;

    public final SituationAndEvents situation;

    public Match(TicketAndEvents t, SituationAndEvents s) {
        ticket = t;
        situation = s;
    }
}
