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

import java.time.ZonedDateTime;
import java.util.Objects;

public class NodeAndFacts {

    public static enum ClockSkewStatus {
        INDETERMINATE,
        DETECTED,
        NOT_DETECTED
    }

    private final String cpnHostname;
    private String opennmsNodeLabel;
    private Integer opennmsNodeId;
    private ClockSkewStatus clockSkewStatus;
    private Long clockSkew;
    private Long numOpennmsSyslogs;
    private Long numOpennmsTraps;
    private Long numCpnSyslogs;
    private Long numCpnTraps;
    private ZonedDateTime start;
    private ZonedDateTime end;

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

    public boolean hasOpennmsNode() {
        return opennmsNodeLabel != null && opennmsNodeId != null;
    }

    public Long getNumOpennmsSyslogs() {
        return numOpennmsSyslogs;
    }

    public void setNumOpennmsSyslogs(Long numOpennmsSyslogs) {
        this.numOpennmsSyslogs = numOpennmsSyslogs;
    }

    public Long getNumOpennmsTraps() {
        return numOpennmsTraps;
    }

    public void setNumOpennmsTraps(Long numOpennmsTraps) {
        this.numOpennmsTraps = numOpennmsTraps;
    }

    public Long getNumCpnSyslogs() {
        return numCpnSyslogs;
    }

    public void setNumCpnSyslogs(Long numCpnSyslogs) {
        this.numCpnSyslogs = numCpnSyslogs;
    }

    public Long getNumCpnTraps() {
        return numCpnTraps;
    }

    public void setNumCpnTraps(Long numCpnTraps) {
        this.numCpnTraps = numCpnTraps;
    }

    public ClockSkewStatus getClockSkewStatus() {
        return clockSkewStatus;
    }

    public void setClockSkewStatus(ClockSkewStatus clockSkewStatus) {
        this.clockSkewStatus = clockSkewStatus;
    }

    public Long getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Long clockSkew) {
        this.clockSkew = clockSkew;
    }


    public ZonedDateTime getStart() {
        return start;
    }

    public void setStart(ZonedDateTime start) {
        this.start = start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public void setEnd(ZonedDateTime end) {
        this.end = end;
    }

    public boolean shouldProcess() {
        if (!hasOpennmsNode()) {
            return false;
        }
        // if CPN has some traps, then OpenNMS must have them too
        if (getNumCpnTraps() != null && getNumOpennmsTraps() != null && getNumCpnTraps() > 0 && getNumOpennmsTraps() <= 0) {
            return false;
        }
        // if CPN has some syslogs, then OpenNMS must have them too
        if (getNumCpnSyslogs() != null && getNumOpennmsSyslogs() != null && getNumCpnSyslogs() > 0 && getNumOpennmsSyslogs() <= 0) {
            return false;
        }

        // don't process nodes where clock skew was detected
        if (ClockSkewStatus.DETECTED.equals(getClockSkewStatus())) {
            return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeAndFacts that = (NodeAndFacts) o;
        return Objects.equals(cpnHostname, that.cpnHostname) &&
                Objects.equals(opennmsNodeLabel, that.opennmsNodeLabel) &&
                Objects.equals(opennmsNodeId, that.opennmsNodeId) &&
                clockSkewStatus == that.clockSkewStatus &&
                Objects.equals(clockSkew, that.clockSkew) &&
                Objects.equals(numOpennmsSyslogs, that.numOpennmsSyslogs) &&
                Objects.equals(numOpennmsTraps, that.numOpennmsTraps) &&
                Objects.equals(numCpnSyslogs, that.numCpnSyslogs) &&
                Objects.equals(numCpnTraps, that.numCpnTraps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpnHostname, opennmsNodeLabel, opennmsNodeId, clockSkewStatus, clockSkew, numOpennmsSyslogs, numOpennmsTraps, numCpnSyslogs, numCpnTraps);
    }

    @Override
    public String toString() {
        return "NodeAndFacts{" +
                "cpnHostname='" + cpnHostname + '\'' +
                ", opennmsNodeLabel='" + opennmsNodeLabel + '\'' +
                ", opennmsNodeId=" + opennmsNodeId +
                ", clockSkewStatus=" + clockSkewStatus +
                ", clockSkew=" + clockSkew +
                ", numOpennmsSyslogs=" + numOpennmsSyslogs +
                ", numOpennmsTraps=" + numOpennmsTraps +
                ", numCpnSyslogs=" + numCpnSyslogs +
                ", numCpnTraps=" + numCpnTraps +
                '}';
    }
}
