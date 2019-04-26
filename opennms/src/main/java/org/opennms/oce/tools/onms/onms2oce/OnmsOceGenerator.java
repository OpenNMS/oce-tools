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

package org.opennms.oce.tools.onms.onms2oce;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.opennms.alec.datasource.v1.schema.Alarm;
import org.opennms.alec.datasource.v1.schema.AlarmRef;
import org.opennms.alec.datasource.v1.schema.Alarms;
import org.opennms.alec.datasource.v1.schema.Event;
import org.opennms.alec.datasource.v1.schema.Inventory;
import org.opennms.alec.datasource.v1.schema.MetaModel;
import org.opennms.alec.datasource.v1.schema.Severity;
import org.opennms.alec.datasource.v1.schema.Situation;
import org.opennms.alec.datasource.v1.schema.Situations;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.api.CpnEntityDao;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.api.OnmsEntityDao;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * Used to generate data that can be consumed by OCE.
 */
public class OnmsOceGenerator  {

    private static final Logger LOG = LoggerFactory.getLogger(OnmsOceGenerator.class);

    private final NodeAndFactsGenerator nodeAndFactsGenerator;

    private final List<OnmsAlarmSummary> allAlarms = new LinkedList<>();
    private final boolean modelGenerationDisabled;
    private final File targetFolder;

    private MetaModel metaModel;
    private Inventory inventory;
    private Situations situations;
    private Alarms alarms;

    private ZonedDateTime start;
    private ZonedDateTime end;
    private OnmsEntityDao onmsEntityDao;
    private CpnEntityDao cpnEntityDao;

    private OnmsOceGenerator(Builder builder) {
        this.modelGenerationDisabled = builder.modelGenerationDisabled;
        this.targetFolder = builder.targetFolder;
        this.onmsEntityDao = builder.onmsEntityDao;
        this.cpnEntityDao = builder.cpnEntityDao;
        this.start = builder.start;
        this.end = builder.end;

        nodeAndFactsGenerator = NodeAndFactsGenerator.newBuilder()
                .setEnd(this.end)
                .setOnmsEntityDao(onmsEntityDao)
                .setCpnEntityDao(cpnEntityDao)
                .setHostnameSubstringsToFilter(Collections.emptyList())
                .setStart(this.start)
                .build();
    }

    public static class Builder {
        private boolean modelGenerationDisabled = false;
        private File targetFolder;
        private OnmsEntityDao onmsEntityDao;
        private CpnEntityDao cpnEntityDao;
        private ZonedDateTime start;
        private ZonedDateTime end;
        public Builder withModelGenerationDisabled(boolean modelGenerationDisabled) {
            this.modelGenerationDisabled = modelGenerationDisabled;
            return this;
        }
        public Builder withOnmsEntityDao(OnmsEntityDao onmsEntityDao) {
            this.onmsEntityDao = onmsEntityDao;
            return this;
        }

        public Builder withCpnEntityDao(CpnEntityDao cpnEntityDao) {
            this.cpnEntityDao = cpnEntityDao;
            return this;
        }
        public Builder withTargetFolder(File targetFolder) {
            this.targetFolder = targetFolder;
            return this;
        }
        public Builder withStart(ZonedDateTime start) {
            this.start = start;
            return this;
        }
        public Builder withEnd(ZonedDateTime end) {
            this.end = end;
            return this;
        }
        public OnmsOceGenerator build() {
            return new OnmsOceGenerator(this);
        }
    }

    public void run() throws IOException {
        final List<NodeAndFacts> nodesAndFacts = nodeAndFactsGenerator.getNodesAndFacts();
        if (nodesAndFacts.isEmpty()) {
            System.out.println("No nodes found.");
            return;
        }

        situations = new Situations();
        alarms = new Alarms();

        final List<NodeAndFacts> nodesToProcess = nodesAndFacts.stream()
                .filter(NodeAndFacts::shouldProcess)
                .collect(Collectors.toList());
        
        // Itterate over nodeAndFacts to generate and accumulate the data
        for (NodeAndFacts nodeAndFacts : nodesToProcess) {
            final NodeAndEvents nodeAndEvents = nodeAndFactsGenerator.retrieveAndPairEvents(nodeAndFacts);
            final List<SituationAndEvents> situationsAndEvents = nodeAndFactsGenerator.getSituationsAndPairEvents(nodeAndEvents);
            generate(nodeAndFacts, situationsAndEvents);
        }    
        // after data has been accumulated
        processAlarmsAndDeriveInventory();
    }

    private void generate(NodeAndFacts nodeAndFacts, List<SituationAndEvents> situationsAndEvents) {
        LOG.info("Generating the situations..");
        LOG.info("There are {} situations", situationsAndEvents.size());

        for (SituationAndEvents s : situationsAndEvents) {
            final Situation situation = new Situation();
            situation.setId(s.getId().toString());
            situation.setCreationTime(s.getLifespan().getStartMs());
            situation.setSeverity(toSeverity(s.getSeverityId()));
            situation.setSummary(s.getLogMessage());
            situation.setDescription(s.getLogMessage());

            final List<OnmsAlarmSummary> alarmsInSituation = s.getAlarmSummaries();
            allAlarms.addAll(alarmsInSituation);

            final List<ESEventDTO> eventsInSituation = s.getEventsInSituation();

            if (eventsInSituation.size() < 1) {
                LOG.info("No events for Situation: {}. Ignoring.", s);
                continue;
            }

            situation.getAlarmRef().addAll(getCausalityTree(s, alarmsInSituation));
            situations.getSituation().add(situation);
        }
    }

    private void processAlarmsAndDeriveInventory() {
        if (!modelGenerationDisabled) {
            LOG.info("Generating inventory and meta-model...");
            final OnmsOceModelGenerator generator = new OnmsOceModelGenerator(allAlarms);
            generator.generate();

            metaModel = generator.getMetaModel();
            inventory = generator.getInventory();
        } else {
            metaModel = null;
            inventory = null;
        }

        LOG.info("Generating the list of alarms...");

        // Process each alarm
        allAlarms.forEach((a) -> {
            final Alarm alarm = new Alarm();
            alarm.setId(Integer.toString(a.getId()));
            alarm.setSummary(a.getLogMessage());
            alarm.setDescription(a.getLogMessage());
            alarm.setLastSeverity(alarm.getLastSeverity());
            alarm.setFirstEventTime(a.getLifespan().getStartMs());
            alarm.setLastEventTime(a.getLifespan().getEndMs());
            alarms.getAlarm().add(alarm);

            List<ESEventDTO> eventsInAlarms = a.getEvents();
            eventsInAlarms.sort(Comparator.comparing(ESEventDTO::getTimestamp));

            for (ESEventDTO e : eventsInAlarms) {
                final Event event = new Event();
                event.setId(e.getId().toString());
                event.setSummary(e.getLogMessage());
                event.setDescription(e.getLogMessage());
                event.setSeverity(toSeverity(e.getSeverity()));
                event.setSource(getSourceForEvent(e));
                event.setTime(e.getTimestamp().getTime());
                alarm.getEvent().add(event);
            }
        });
    }

    private String getSourceForEvent(ESEventDTO e) {
        return Strings.isNullOrEmpty(e.getSyslogMessage()) ? "trap" : "syslog";
    }

    public void writeResultsToDisk(String prefix) {
        Objects.requireNonNull(targetFolder, "target folder must be set");
        LOG.info("Marshalling results to disk...");
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(MetaModel.class, Inventory.class, Alarms.class, Situations.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            if (metaModel != null && inventory != null) {
                LOG.info("Writing {} model object definitions (meta-model)...", metaModel.getModelObjectDef().size());
                marshaller.marshal(metaModel, new File(targetFolder, prefix + ".metamodel.xml"));
                LOG.info("Writing {} model object entries (inventory)...", inventory.getModelObjectEntry().size());
                marshaller.marshal(inventory, new File(targetFolder, prefix + ".inventory.xml"));
            }
            LOG.info("Writing {} alarms...", alarms.getAlarm().size());
            marshaller.marshal(alarms, new File(targetFolder, prefix + ".alarms.xml"));
            LOG.info("Writing {} situations...", situations.getSituation().size());
            marshaller.marshal(situations, new File(targetFolder, prefix + ".situations.xml"));
        } catch (Exception e) {
            LOG.error("Oops", e);
        }
        LOG.info("Done.");
    }

    private static List<AlarmRef> getCausalityTree(SituationAndEvents s, List<OnmsAlarmSummary> alarmsInSituation) {
        final Map<Integer, List<OnmsAlarmSummary>> eventsInSituationByAlarmId = alarmsInSituation.stream().collect(Collectors.groupingBy(OnmsAlarmSummary::getId));
        return eventsInSituationByAlarmId.keySet().stream().map(alarmId -> {
            final AlarmRef cause = new AlarmRef();
            cause.setId(alarmId.toString());
            return cause;
        }).collect(Collectors.toList());
    }

    private static Severity toSeverity(int severity) {
        switch (severity) {
        case 7:
            return Severity.CRITICAL;
        case 6:
            return Severity.MAJOR;
        case 5:
            return Severity.MINOR;
        case 4:
            return Severity.WARNING;
        case 3:
            return Severity.NORMAL;
        case 2:
            return Severity.CLEARED;
        }
        return Severity.INDETERMINATE;
    }

}
