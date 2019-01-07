package org.opennms.oce.tools.onms.bucket;

import java.util.Set;

public class CpnTrap {

    private String trapTypeOid;

    private String location;

    private long time;

    public boolean matches(OnmsTrap t) {
        // FIXME
        return false;
    }

    public boolean matchesAny(Set<OnmsTrap> traps) {
        return traps.stream().anyMatch(t -> matches(t));
    }

    @Override
    public String toString() {
        return "trap[" + time + "|" + location + "|" + trapTypeOid + "]";
    }
}
