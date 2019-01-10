package org.opennms.oce.tools.onms.bucket;

import java.util.List;

import org.opennms.oce.tools.cpn.model.TrapRecord;

public class CpnTrap {

    private String trapTypeOid;

    private String location;

    private long time;

    public CpnTrap(TrapRecord r) {
        trapTypeOid = r.getTrapTypeOid();
        location = r.getLocation();
        time = r.getTime().getTime();
    }

    public boolean matches(OnmsTrap t) {
        // FIXME from matching code
        return true;
    }

    public boolean matchesAny(List<OnmsTrap> traps) {
        return traps.stream().anyMatch(t -> matches(t));
    }

    @Override
    public String toString() {
        return "trap[" + time + "|" + location + "|" + trapTypeOid + "]";
    }
}
