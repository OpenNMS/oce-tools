package org.opennms.oce.tools.onms.bucket;

import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.opennms.oce.tools.tsaudit.TicketAndEvents;

public class Match {

    public final TicketAndEvents ticket;

    public final SituationAndEvents situation;

    public Match(TicketAndEvents t, SituationAndEvents s) {
        ticket = t;
        situation = s;
    }
}
