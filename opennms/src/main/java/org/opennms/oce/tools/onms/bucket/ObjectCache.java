package org.opennms.oce.tools.onms.bucket;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.opennms.oce.tools.onms.client.ESEventDTO;

// Cache Docs retrieved frim ES
public class ObjectCache {

    private final Map<Integer, Alarm> alarmsById = new HashMap<>();

    private final Map<Integer, ESEventDTO> eventsById = new HashMap<>();

    public boolean hasAlarm(Integer id) {
        return alarmsById.containsKey(id);
    }

    public Alarm getAlarm(Integer id) {
        return alarmsById.get(id);
    }

    public boolean hasEvent(Integer id) {
        return eventsById.containsKey(id);
    }

    public ESEventDTO getEventDto(Integer id) {
        return eventsById.get(id);
    }

    public Collection<Alarm> cacheAlarms(Collection<Alarm> alarms) {
        alarms.forEach(a -> cacheAlarm(a));
        return alarms;
    }

    public Alarm cacheAlarm(Alarm alarm) {
        alarmsById.put(alarm.getId(), alarm);
        return alarm;
    }

    public Collection<ESEventDTO> cacheEvents(Collection<ESEventDTO> events) {
        events.forEach(e -> cacheEvent(e));
        return events;
    }

    private ESEventDTO cacheEvent(ESEventDTO event) {
        eventsById.put(event.getId(), event);
        return event;
    }
}
