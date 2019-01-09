package org.opennms.oce.tools.onms.bucket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Situation {

    private static final Logger LOG = LoggerFactory.getLogger(Situation.class);

    private Integer id;

    private final List<OnmsSyslog> syslogs = new ArrayList<>();

    private final List<OnmsTrap> traps = new ArrayList<>();

    private final Set<Integer> relatedAlarmIds = new HashSet<>();

    private final Set<String> relatedAlarmReductionKeys = new HashSet<>();

    private final Set<AlarmDocumentDTO> relatedAlarmDtos = new HashSet<>();

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
        syslogs.addAll(onmsSyslogs);
    }

    public List<OnmsTrap> getTraps() {
        return traps;
    }

    public void setTraps(List<OnmsTrap> onmsTtraps) {
        traps.addAll(onmsTtraps);
    }

    public void setEvents(Collection<ESEventDTO> events) {
        // FIXME --- convert each event to syslog or trap and add to stack
        for (ESEventDTO dto : events) {
            if (dto.getSyslogMessage() != null) {
                LOG.debug("Situation [{}] - Event [{}] : THIS IS A SYSLOG: {}", id, dto.getId(), dto.getSyslogMessage());
                syslogs.add(new OnmsSyslog(dto));
            } else {
                LOG.debug("Situation [{}] - Event [{}] : THIS IS A TRAP: {}", id, dto.getId(), dto.getTrapTypeOid());
                traps.add(new OnmsTrap(dto));
            }
        }
    }

    public void addAlarmId(Integer alarmId) {
        relatedAlarmIds.add(alarmId);
    }

    public Set<Integer> getRelatedAlarmIds() {
        return relatedAlarmIds;
    }

    public void addReductionKeys(List<String> relatedAlarmReductionKeys) {
        this.relatedAlarmReductionKeys.addAll(relatedAlarmReductionKeys);
    }

    public Set<String> getRelatedReductionKeys() {
        return relatedAlarmReductionKeys;
    }

    @Override
    public String toString() {
        return "Situation[" + id + "|\n" + syslogs + "\n" + traps + "]";
    }

    public void addRelatedAlarmIds(Set<Integer> relatedAlarmIds) {
        this.relatedAlarmIds.addAll(relatedAlarmIds);
    }

    public void addRelatedAlarmDtos(List<AlarmDocumentDTO> relatedAlarmDtos) {
        this.relatedAlarmDtos.addAll(relatedAlarmDtos);
        for (AlarmDocumentDTO dto : relatedAlarmDtos) {

        }
    }

    public Set<AlarmDocumentDTO> getRelatedAlarmDtos() {
        return relatedAlarmDtos;
    }
}
