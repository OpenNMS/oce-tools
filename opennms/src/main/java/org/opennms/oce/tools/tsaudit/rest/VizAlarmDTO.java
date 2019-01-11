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

package org.opennms.oce.tools.tsaudit.rest;

import java.util.List;
import java.util.Objects;

public class VizAlarmDTO {
    private final String id;
    private final String source;
    private final long startTime;
    private final long endTime;
    private final String label;
    private final List<String> eventIds;

    public VizAlarmDTO(String id, String source, long startTime, long endTime, String label, List<String> eventIds) {
        this.id = Objects.requireNonNull(id);
        this.source = Objects.requireNonNull(source);
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
        this.eventIds = eventIds;
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getEventIds() {
        return eventIds;
    }
}
