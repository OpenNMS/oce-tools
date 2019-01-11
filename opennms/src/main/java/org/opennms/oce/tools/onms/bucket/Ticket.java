package org.opennms.oce.tools.onms.bucket;

import java.util.Date;
import java.util.List;

import org.opennms.oce.tools.cpn.model.TicketRecord;

public class Ticket {

    private final String id;
    private final Date creationTime;
    private final int eventCount;

    List<CpnSyslog> syslogs;

    List<CpnTrap> traps;

    public Ticket(TicketRecord record) {
        id = record.getTicketId();
        creationTime = record.getCreationTime();
        eventCount = record.getEventCount();
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public int getEventCount() {
        return eventCount;
    }

    public String getId() {
        return id;
    }

    public List<CpnSyslog> getSyslogs() {
        return syslogs;
    }

    public List<CpnTrap> getTraps() {
        return traps;
    }

    // TRUE if ALL syslogs match ALL s.syslogs && ALL traps match ALL s.traps
    public boolean matches(Situation s) {
        return syslogs.size() == s.getSyslogs().size() &&
                traps.size() == s.getTraps().size() &&
                syslogs.stream().allMatch(syslog -> syslog.matchesAny(s.getSyslogs())) &&
                traps.stream().allMatch(trap -> trap.matchesAny(s.getTraps()));
    }

    // TRUE if ANY syslogs match ANY s.syslogs || ANY traps match ANY s.traps
    public boolean partiallyMatches(Situation s) {
        boolean x = true;
        boolean aSyslogMatched = syslogs.stream().anyMatch(syslog -> s.hasSyslogMessage(syslog.getMessage()) && syslog.matchesAny(s.getSyslogs(syslog.getMessage())));
        boolean aTrapMatched = traps.stream().anyMatch(trap -> trap.matchesAny(s.getTraps()));
        return aSyslogMatched || aTrapMatched;
    }

    public void setSyslogs(List<CpnSyslog> syslogs) {
        this.syslogs = syslogs;
    }

    public void setTraps(List<CpnTrap> traps) {
        this.traps = traps;
    }

    @Override
    public String toString() {
        return String.format("Ticket[%s| syslogs: %d traps %d ]", id, syslogs.size(), traps.size());
    }

}
