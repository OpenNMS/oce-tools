/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.oce.tools.tsaudit;

import java.util.Objects;

public class NodeAndFacts {
    private final String cpnHostname;
    private String opennmsNodeLabel;
    private Integer opennmsNodeId;
    private Boolean clockSkewDetected;
    private Boolean didOpennmsReceiveSyslog;
    private Boolean didOpennmsReceiveTrap;

    public NodeAndFacts(String cpnHostname) {
        this.cpnHostname = Objects.requireNonNull(cpnHostname);
    }

    public String getCpnHostname() {
        return cpnHostname;
    }

    public String getOpennmsNodeLabel() {
        return opennmsNodeLabel;
    }

    public void setOpennmsNodeLabel(String opennmsNodeLabel) {
        this.opennmsNodeLabel = opennmsNodeLabel;
    }

    public Integer getOpennmsNodeId() {
        return opennmsNodeId;
    }

    public void setOpennmsNodeId(Integer opennmsNodeId) {
        this.opennmsNodeId = opennmsNodeId;
    }

    public Boolean getClockSkewDetected() {
        return clockSkewDetected;
    }

    public void setClockSkewDetected(Boolean clockSkewDetected) {
        this.clockSkewDetected = clockSkewDetected;
    }

    public Boolean getDidOpennmsReceiveSyslog() {
        return didOpennmsReceiveSyslog;
    }

    public void setDidOpennmsReceiveSyslog(Boolean didOpennmsReceiveSyslog) {
        this.didOpennmsReceiveSyslog = didOpennmsReceiveSyslog;
    }

    public Boolean getDidOpennmsReceiveTrap() {
        return didOpennmsReceiveTrap;
    }

    public void setDidOpennmsReceiveTrap(Boolean didOpennmsReceiveTrap) {
        this.didOpennmsReceiveTrap = didOpennmsReceiveTrap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeAndFacts nodeFacts = (NodeAndFacts) o;
        return Objects.equals(cpnHostname, nodeFacts.cpnHostname) &&
                Objects.equals(opennmsNodeLabel, nodeFacts.opennmsNodeLabel) &&
                Objects.equals(opennmsNodeId, nodeFacts.opennmsNodeId) &&
                Objects.equals(clockSkewDetected, nodeFacts.clockSkewDetected) &&
                Objects.equals(didOpennmsReceiveSyslog, nodeFacts.didOpennmsReceiveSyslog) &&
                Objects.equals(didOpennmsReceiveTrap, nodeFacts.didOpennmsReceiveTrap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpnHostname, opennmsNodeLabel, opennmsNodeId, clockSkewDetected, didOpennmsReceiveSyslog, didOpennmsReceiveTrap);
    }

    @Override
    public String toString() {
        return "NodeFacts{" +
                "cpnHostname='" + cpnHostname + '\'' +
                ", opennmsNodeLabel='" + opennmsNodeLabel + '\'' +
                ", opennmsNodeId=" + opennmsNodeId +
                ", clockSkewDetected=" + clockSkewDetected +
                ", didOpennmsReceiveSyslog=" + didOpennmsReceiveSyslog +
                ", didOpennmsReceiveTrap=" + didOpennmsReceiveTrap +
                '}';
    }
}
