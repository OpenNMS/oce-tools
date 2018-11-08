/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.tools.cpn.model;

import java.util.Date;
import java.util.Objects;

import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.util.Strings;
import org.opennms.oce.tools.cpn.DateHandler;
import org.opennms.oce.tools.cpn.events.EventRecordLite;

import io.searchbox.annotations.JestId;

public class EventRecord implements EventRecordLite {
    private final String source;
    private final EventSeverity severity;
    @JestId
    private final String eventId;
    private Date time;
    private final String description;
    private String detailedDescription;
    private final String location;
    private final String alarmId;
    private final String ticketId;
    private final String causingEventId;
    private final int duplicationCount;
    private final int reductionCount;

    public EventRecord(String source, CSVRecord record, DateHandler dateHandler) {
        this.source = source;
        this.severity = EventSeverity.valueOf(record.get("Severity"));
        this.eventId = record.get("Event ID");
        this.time = dateHandler.parse(record.get("Time"));
        this.description = record.get("Description");
        try {
            this.detailedDescription = record.get("Detailed Description");
        } catch (IllegalArgumentException e) {
            // ignore this since we know it's unavailable on traps
            this.detailedDescription = null;
        }
        this.location = record.get("Location");
        this.alarmId = record.get("Alarm ID");
        this.ticketId = record.get("Ticket ID");
        this.causingEventId = record.get("Causing Event ID");
        this.duplicationCount = Integer.parseInt(record.get("Duplication Count"));
        this.reductionCount = Strings.isBlank(record.get("Reduction Count")) ? 0 : Integer.parseInt(record.get("Reduction Count"));
    }

    public String getSource() {
        return source;
    }

    public EventSeverity getSeverity() {
        return severity;
    }

    public String getEventId() {
        return eventId;
    }

    public Date getTime() {
        return time;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    @Override
    public String getLocation() {
        return location;
    }

    public String getAlarmId() {
        return alarmId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getCausingEventId() {
        return causingEventId;
    }

    public int getDuplicationCount() {
        return duplicationCount;
    }

    public int getReductionCount() {
        return reductionCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventRecord that = (EventRecord) o;
        return duplicationCount == that.duplicationCount &&
                reductionCount == that.reductionCount &&
                Objects.equals(severity, that.severity) &&
                Objects.equals(eventId, that.eventId) &&
                Objects.equals(time, that.time) &&
                Objects.equals(description, that.description) &&
                Objects.equals(detailedDescription, that.detailedDescription) &&
                Objects.equals(location, that.location) &&
                Objects.equals(alarmId, that.alarmId) &&
                Objects.equals(ticketId, that.ticketId) &&
                Objects.equals(causingEventId, that.causingEventId);
    }

    @Override
    public int hashCode() {

        return Objects.hash(severity, eventId, time, description, detailedDescription, location, alarmId, ticketId, causingEventId, duplicationCount, reductionCount);
    }

    @Override
    public String toString() {
        return "EventRecord{" +
                "source='" + source + '\'' +
                ", severity='" + severity + '\'' +
                ", eventId='" + eventId + '\'' +
                ", time=" + time +
                ", description='" + description + '\'' +
                ", detailedDescription='" + detailedDescription + '\'' +
                ", location='" + location + '\'' +
                ", alarmId='" + alarmId + '\'' +
                ", ticketId='" + ticketId + '\'' +
                ", causingEventId='" + causingEventId + '\'' +
                ", duplicationCount=" + duplicationCount +
                ", reductionCount=" + reductionCount +
                '}';
    }
}
