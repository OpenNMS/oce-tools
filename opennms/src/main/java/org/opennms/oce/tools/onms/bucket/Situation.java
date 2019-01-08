package org.opennms.oce.tools.onms.bucket;

import java.util.List;

import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class Situation {

    private Integer id;

    private List<OnmsSyslog> syslogs;

    private List<OnmsTrap> traps;

    public Situation(AlarmDocumentDTO dto) {
        id = dto.getId();
    }

    public Integer getId() {
        return id;
    }

    public List<OnmsSyslog> getSyslogs() {
        return syslogs;
    }

    public void setSyslogs(List<OnmsSyslog> onmsSyslogs) {
        this.syslogs = onmsSyslogs;
    }

    public List<OnmsTrap> getTraps() {
        return traps;
    }

    public void setTraps(List<OnmsTrap> onmsTtraps) {
        this.traps = onmsTtraps;
    }

    public void setEvents(List<ESEventDTO> events) {
        // FIXME --- converft each event to syslog or trap and add to stack
    }
}
