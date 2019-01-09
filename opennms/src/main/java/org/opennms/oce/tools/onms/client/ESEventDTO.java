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

package org.opennms.oce.tools.onms.client;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;

public class ESEventDTO {

    @SerializedName("nodelabel")
    private String nodeLabel;

    @SerializedName("nodeid")
    private Integer nodeId;

    @SerializedName("p_syslogmessage")
    private String syslogMessage;

    @SerializedName("@timestamp")
    private Date timestamp;

    @SerializedName("id")
    private Integer id;

    @SerializedName("logmsg")
    private String logMessage;
    
    @SerializedName("p_oids")
    private List<Map<String, String>> p_oids;

    @SerializedName("alarmreductionkey")
    private String alarmReductionKey;

    @SerializedName("alarmclearkey")
    private String alarmClearKey;

    public String getNodeLabel() {
        return nodeLabel;
    }

    public void setNodeLabel(String nodeLabel) {
        this.nodeLabel = nodeLabel;
    }

    public Integer getNodeId() {
        return nodeId;
    }

    public void setNodeId(Integer nodeId) {
        this.nodeId = nodeId;
    }

    public String getSyslogMessage() {
        return syslogMessage;
    }

    public void setSyslogMessage(String syslogMessage) {
        this.syslogMessage = syslogMessage;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<Map<String, String>> getP_oids() {
        return p_oids;
    }

    public void setP_oids(List<Map<String, String>> p_oids) {
        this.p_oids = p_oids;
    }

    public Optional<String> getTrapTypeOid() {
        if (p_oids != null && !p_oids.isEmpty()) {
            String trapTypeOid = ".1.3.6.1.6.3.1.1.4.3.0";
            for (Map<String, String> oidKv : p_oids) {
                if (oidKv.get("oid").equals(trapTypeOid)) {
                    return Optional.of(oidKv.get("value"));
                }
            }
        }

        return Optional.empty();
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public String getAlarmReductionKey() {
        return alarmReductionKey;
    }

    public void setAlarmReductionKey(String alarmReductionKey) {
        this.alarmReductionKey = alarmReductionKey;
    }

    public String getAlarmClearKey() {
        return alarmClearKey;
    }

    public void setAlarmClearKey(String alarmClearKey) {
        this.alarmClearKey = alarmClearKey;
    }

    @Override
    public String toString() {
        return "ESEventDTO{" +
                "nodeLabel='" + nodeLabel + '\'' +
                ", nodeId=" + nodeId +
                ", syslogMessage='" + syslogMessage + '\'' +
                ", timestamp=" + timestamp +
                ", id=" + id +
                ", logMessage='" + logMessage + '\'' +
                ", p_oids=" + p_oids +
                ", alarmReductionKey='" + alarmReductionKey + '\'' +
                ", alarmClearKey='" + alarmClearKey + '\'' +
                '}';
    }
}
