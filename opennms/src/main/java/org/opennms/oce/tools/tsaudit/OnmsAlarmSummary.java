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

import java.util.List;

import org.opennms.oce.tools.onms.client.ESEventDTO;

public class OnmsAlarmSummary {
    private final int id;
    private final String reductionKey;
    private final Lifespan lifespan;
    private final String logMessage;
    private final List<ESEventDTO> events;

    public OnmsAlarmSummary(int id, String reductionKey, Lifespan lifespan, String logMessage, List<ESEventDTO> events) {
        this.id = id;
        this.reductionKey = reductionKey;
        this.lifespan = lifespan;
        this.logMessage = logMessage;
        this.events = events;
    }

    public int getId() {
        return id;
    }

    public String getReductionKey() {
        return reductionKey;
    }

    public Lifespan getLifespan() {
        return lifespan;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public List<ESEventDTO> getEvents() {
        return events;
    }
}
