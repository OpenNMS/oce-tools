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

package org.opennms.oce.tools.onms.view;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class OnmsDatasetView {

    private final int batchSize;
    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;
    private final Set<EventType> eventTypes;

    public enum EventType {
        TRAP,
        SYSLOG
    }

    public static class Builder {
        private int batchSize = 10000;
        private ZonedDateTime startTime = null;
        private ZonedDateTime endTime = null;

        private Set<EventType> eventTypes = Sets.newHashSet(EventType.TRAP, EventType.SYSLOG);

        public Builder withBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder withStartTime(ZonedDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder withEndTime(ZonedDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder withEventTypes(Set<EventType> eventTypes) {
            this.eventTypes = Objects.requireNonNull(eventTypes);
            return this;
        }

        public OnmsDatasetView build() {
            return new OnmsDatasetView(this);
        }
    }

    private OnmsDatasetView(Builder builder) {
        this.batchSize = builder.batchSize;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.eventTypes = ImmutableSet.copyOf(builder.eventTypes);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public Set<EventType> getEventTypes() {
        return eventTypes;
    }

}
