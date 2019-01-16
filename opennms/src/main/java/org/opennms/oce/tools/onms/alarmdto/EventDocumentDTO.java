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

package org.opennms.oce.tools.onms.alarmdto;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

public class EventDocumentDTO {

    @SerializedName("description")
    private String description;

    @SerializedName("id")
    private Integer id;

    @SerializedName("log_message")
    private String logMessage;

    @SerializedName("uei")
    private String uei;

    @SerializedName("eventseverity")
    private Integer severity;

    @SerializedName("eventcreationtime")
    private Date time;

    @SerializedName("alarmreductionkey")
    private String alarmReductionKey;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public String getUei() {
        return uei;
    }

    public void setUei(String uei) {
        this.uei = uei;
    }

    public Integer getSeverity() {
        return severity;
    }

    public void setSeverity(Integer severity) {
        this.severity = severity;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public String getAlarmReductionKey() {
        return alarmReductionKey;
    }

    public void setAlarmReductionKey(String alarmReductionKey) {
        this.alarmReductionKey = alarmReductionKey;
    }

    @Override
    public String toString() {
        return "EventDocumentDTO{" +
                "description='" + description + '\'' +
                ", id=" + id +
                ", severity=" + severity +
                ", time=" + time +
                ", logMessage='" + logMessage + '\'' +
                ", uei='" + uei + '\'' +
                '}';
    }
}
