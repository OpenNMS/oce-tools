package org.opennms.oce.tools.onms.bucket;

import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.tsaudit.TicketAndEvents;

public class Match {

    public final TicketAndEvents ticket;

    public final AlarmDocumentDTO situation;

    public Match(TicketAndEvents t, AlarmDocumentDTO s) {
        ticket = t;
        situation = s;
    }
}
