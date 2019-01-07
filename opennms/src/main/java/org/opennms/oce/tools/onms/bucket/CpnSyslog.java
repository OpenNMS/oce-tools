package org.opennms.oce.tools.onms.bucket;

import java.util.Set;

public class CpnSyslog {

    private String detailedDescription;

    private long time;

    public boolean matches(OnmsSyslog s) {
        // FIXME
        return false;
    }

    public boolean matchesAny(Set<OnmsSyslog> syslogs) {
        return syslogs.stream().anyMatch(s -> matches(s));
    }

    @Override
    public String toString() {
        return "Syslog[" + time + "|" + detailedDescription + "]";
    }
}
