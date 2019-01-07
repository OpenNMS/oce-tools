package org.opennms.oce.tools.onms.bucket;

import java.util.Set;

public class Ticket {

    private String id;
    Set<CpnSyslog> syslogs;
    Set<CpnTrap> traps;

    public Ticket(String id) {
        this.id = id;
    }

    public boolean matches(Situation s) {
        // return all syslogs match all s.syslogs && all traps match all s.traps
        return syslogs.size() == s.getSyslogs().size() &&
                traps.size() == s.getTraps().size() &&
                syslogs.stream().allMatch(syslog -> syslog.matchesAny(s.getSyslogs())) &&
                traps.stream().allMatch(trap -> trap.matchesAny(s.getTraps()));
    }

    public boolean partiallymatches(Situation s) {
        // return ANY syslogs match ANY s.syslogs || ANY traps match ANY s.traps
        return syslogs.size() == s.getSyslogs().size() &&
                traps.size() == s.getTraps().size() &&
                syslogs.stream().anyMatch(syslog -> syslog.matchesAny(s.getSyslogs())) &&
                traps.stream().anyMatch(trap -> trap.matchesAny(s.getTraps()));
    }

    public Set<CpnSyslog> getSyslogs() {
        return syslogs;
    }

    public void setSyslogs(Set<CpnSyslog> syslogs) {
        this.syslogs = syslogs;
    }

    public Set<CpnTrap> getTraps() {
        return traps;
    }

    public void setTraps(Set<CpnTrap> traps) {
        this.traps = traps;
    }

    @Override
    public String toString() {
        return "Ticket[" + id + syslogs + traps + "]";
    }
}
