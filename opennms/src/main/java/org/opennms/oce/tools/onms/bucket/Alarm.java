package org.opennms.oce.tools.onms.bucket;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;

public class Alarm {

    private final int id;

    private Long start;

    private Long end;

    private Integer lastEventId;

    private final Set<Integer> eventIds = new HashSet<>();

    private boolean isDeleted;

    public Alarm(AlarmDocumentDTO dto) {
        id = dto.getId();
        start = dto.getFirstEventTime() == null ? 0 : dto.getFirstEventTime();
        end = dto.getLastEventTime() == null ? 0 : dto.getLastEventTime();
        isDeleted = dto.getDeletedTime() != null;
        if (dto.getLastEvent() != null && dto.getLastEvent().getId() != null) {
            lastEventId = dto.getLastEvent().getId();
            eventIds.add(lastEventId);
        }
    }

    public int getId() {
        return id;
    }

    public Long getStart() {
        return start;
    }

    public Long getEnd() {
        return end;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public Integer getLastEventId() {
        return lastEventId;
    }

    public List<Integer> getEventIds() {
        return eventIds.stream().collect(Collectors.toList());
    }

    public void setEventIds(List<Integer> eventIds) {
        this.eventIds.addAll(eventIds);
    }

    public void update(AlarmDocumentDTO dto) {
        if (dto.getFirstEventTime() != null && dto.getFirstEventTime() < start) {
            start = dto.getFirstEventTime();
        }
        if (dto.getLastEventTime() != null && dto.getLastEventTime() > end) {
            end = dto.getLastEventTime();
            // set this as the last event
            if (dto.getLastEvent() != null) {
                lastEventId = dto.getLastEvent().getId();
            }
        }
        if (dto.getDeletedTime() != null) {
        isDeleted = true;
        end = dto.getDeletedTime();
        }
        if (dto.getLastEvent() != null && dto.getLastEvent().getId() != null) {
            eventIds.add(dto.getLastEvent().getId());
        }
    }

    @Override
    public String toString() {
        return String.format("Alarm[%d] events: %s ]", id, eventIds.stream().map(Object::toString).collect(Collectors.joining(", ")));
    }
}
