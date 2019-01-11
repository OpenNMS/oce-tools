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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class SituationAndEvents {
    private final List<AlarmDocumentDTO> situationDtos;
    private final Lifespan lifespan;
    private final List<ESEventDTO> eventsInSituation;
    private final List<OnmsAlarmSummary> alarmSummaries;
    private final AlarmDocumentDTO firstSituationDto;

    public SituationAndEvents(List<AlarmDocumentDTO> situationDtos, Lifespan lifespan, List<ESEventDTO> eventsInSituation, List<OnmsAlarmSummary> alarmSummaries) {
        this.situationDtos = Objects.requireNonNull(situationDtos);
        this.lifespan = Objects.requireNonNull(lifespan);
        this.eventsInSituation = Objects.requireNonNull(eventsInSituation);
        this.alarmSummaries = Objects.requireNonNull(alarmSummaries);

        this.firstSituationDto = situationDtos.stream().min(Comparator.comparing(AlarmDocumentDTO::getUpdateTime))
                .orElseThrow(() -> new RuntimeException("Need at least one Situation DTO"));
    }

    public List<AlarmDocumentDTO> getSituationDtos() {
        return situationDtos;
    }

    public Lifespan getLifespan() {
        return lifespan;
    }

    public List<ESEventDTO> getEventsInSituation() {
        return eventsInSituation;
    }

    public List<OnmsAlarmSummary> getAlarmSummaries() {
        return alarmSummaries;
    }

    public String getReductionKey() {
        return firstSituationDto.getReductionKey();
    }

    public Integer getId() {
        return firstSituationDto.getId();
    }

    public String getLogMessage() {
        return firstSituationDto.getLogMessage();
    }
}
