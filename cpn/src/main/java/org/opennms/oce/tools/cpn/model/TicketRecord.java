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

import io.searchbox.annotations.JestId;

public class TicketRecord {

    private final EventSeverity severity;
    @JestId
    private final String ticketId;
    private final Date lastModificationTime;
    private final Date rootEventTime;
    private final String description;
    private final String location;
    private final Boolean acknowledged;
    private final Date creationTime;
    private final int eventCount;
    private final String affectedDevicesCount;
    private final String duplicationCount;
    private final String reductionCount;
    private final String alarmCount;

    public TicketRecord(CSVRecord record, DateHandler dateHandler) {
        this.severity = EventSeverity.valueOf(record.get("Severity"));
        this.ticketId = record.get("Ticket ID");
        this.lastModificationTime = dateHandler.parse(record.get("Last Modification Time"));
        this.rootEventTime = dateHandler.parse(record.get("Root Event Time"));
        this.description = record.get("Description");
        this.location = record.get("Location");
        String acked = record.get("Acknowledged");
        if (Strings.isNotEmpty(acked)) {
            acked = acked.toLowerCase();
            if ("yes".equalsIgnoreCase(acked)) {
                acknowledged = true;
            } else {
                acknowledged = false;
            }
        } else {
            acknowledged = null;
        }
        this.creationTime = dateHandler.parse(record.get("Creation Time"));
        this.eventCount = Integer.parseInt(record.get("Event Count"));
        this.affectedDevicesCount = record.get("Affected Devices Count");
        this.duplicationCount = record.get("Duplication Count");
        this.reductionCount = record.get("Reduction Count");
        this.alarmCount = record.get("Alarm Count");
    }

    public EventSeverity getSeverity() {
        return severity;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Date getLastModificationTime() {
        return lastModificationTime;
    }

    public Date getRootEventTime() {
        return rootEventTime;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Boolean getAcknowledged() {
        return acknowledged;
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public int getEventCount() {
        return eventCount;
    }

    public String getAffectedDevicesCount() {
        return affectedDevicesCount;
    }

    public String getDuplicationCount() {
        return duplicationCount;
    }

    public String getReductionCount() {
        return reductionCount;
    }

    public String getAlarmCount() {
        return alarmCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketRecord that = (TicketRecord) o;
        return Objects.equals(severity, that.severity) &&
                Objects.equals(ticketId, that.ticketId) &&
                Objects.equals(lastModificationTime, that.lastModificationTime) &&
                Objects.equals(rootEventTime, that.rootEventTime) &&
                Objects.equals(description, that.description) &&
                Objects.equals(location, that.location) &&
                Objects.equals(acknowledged, that.acknowledged) &&
                Objects.equals(creationTime, that.creationTime) &&
                Objects.equals(eventCount, that.eventCount) &&
                Objects.equals(affectedDevicesCount, that.affectedDevicesCount) &&
                Objects.equals(duplicationCount, that.duplicationCount) &&
                Objects.equals(reductionCount, that.reductionCount) &&
                Objects.equals(alarmCount, that.alarmCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, ticketId, lastModificationTime, rootEventTime, description, location, acknowledged, creationTime, eventCount, affectedDevicesCount, duplicationCount, reductionCount, alarmCount);
    }

    @Override
    public String toString() {
        return "TicketRecord{" +
                "severity=" + severity +
                ", ticketId='" + ticketId + '\'' +
                ", lastModificationTime=" + lastModificationTime +
                ", rootEventTime=" + rootEventTime +
                ", description='" + description + '\'' +
                ", location='" + location + '\'' +
                ", acknowledged=" + acknowledged +
                ", creationTime=" + creationTime +
                ", eventCount=" + eventCount +
                ", affectedDevicesCount='" + affectedDevicesCount + '\'' +
                ", duplicationCount='" + duplicationCount + '\'' +
                ", reductionCount='" + reductionCount + '\'' +
                ", alarmCount='" + alarmCount + '\'' +
                '}';
    }
}
