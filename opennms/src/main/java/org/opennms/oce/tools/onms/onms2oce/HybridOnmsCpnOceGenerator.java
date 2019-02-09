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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.opennms.oce.datasource.v1.schema.AlarmRef;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.Severity;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.cpn.model.EventSeverity;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetViewer;
import org.opennms.oce.tools.svc.NodeAndFactsService;
import org.opennms.oce.tools.ticketdiag.TicketDetails;
import org.opennms.oce.tools.ticketdiag.TicketDiagnostic;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to generate data that can be consumed by OCE.
 */
public class HybridOnmsCpnOceGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(HybridOnmsCpnOceGenerator.class);

    private final CpnDatasetViewer viewer;
    private final NodeAndFactsService nodeAndFactsService;
    private final boolean modelGenerationDisabled;
    private final File targetFolder;
    private String ticketId;

    private Situations situations;
    private MetaModel metaModel;
    private Inventory inventory;
    private Alarms alarms;

    public static class Builder {
        private CpnDatasetViewer viewer;
        private boolean modelGenerationDisabled = false;
        private File targetFolder;
        private String ticketId;
        private NodeAndFactsService nodeAndFactsService;

        public Builder withViewer(CpnDatasetViewer viewer) {
            this.viewer = viewer;
            return this;
        }

        public Builder withNodeAndFactsService(NodeAndFactsService nodeAndFactsService) {
            this.nodeAndFactsService = nodeAndFactsService;
            return this;
        }

        public Builder withModelGenerationDisabled(boolean modelGenerationDisabled) {
            this.modelGenerationDisabled = modelGenerationDisabled;
            return this;
        }

        public Builder withTargetFolder(File targetFolder) {
            this.targetFolder = targetFolder;
            return this;
        }

        public Builder withTicketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public HybridOnmsCpnOceGenerator build() {
            Objects.requireNonNull(viewer, "viewer is required");
            Objects.requireNonNull(nodeAndFactsService, "nodeAndFactsService is required.");
            return new HybridOnmsCpnOceGenerator(this);
        }

    }

    private HybridOnmsCpnOceGenerator(Builder builder) {
        this.viewer = builder.viewer;
        this.nodeAndFactsService = builder.nodeAndFactsService;
        this.modelGenerationDisabled = builder.modelGenerationDisabled;
        this.targetFolder = builder.targetFolder;
        this.ticketId = builder.ticketId;
    }

    public void generate() {
        LOG.info("Generating the situations and alarms..");
        situations = new Situations();
        alarms = new Alarms();

        final List<TicketRecord> filteredTickets = new LinkedList<>();
        if (ticketId == null) {
            viewer.getTicketRecordsWithRootEventTimeInRange(filteredTickets::addAll);
        } else {
            TicketRecord t = viewer.getTicketWithId(ticketId);
            if (t == null) {
                throw new IllegalStateException("No ticket found with id: " + ticketId);
            }
            filteredTickets.add(t);
        }

        int n = 0;
        final Map<TicketRecord, TicketDetails> ticketDetailsByTicket = new LinkedHashMap<>();
        for (TicketRecord t : filteredTickets) {
            final TicketDiagnostic ticketDiag = new TicketDiagnostic(t, viewer, nodeAndFactsService);
            final TicketDetails ticketDetails = ticketDiag.getTicketDetails();
            if (ticketDetails == null) {
                System.out.println("Skipping ticket: " + t.getTicketId());
            } else {
                ticketDetailsByTicket.put(t, ticketDetails);
            }
            n++;
            System.out.printf("%d of %d (matched %d)\n", n, filteredTickets.size(), ticketDetailsByTicket.size());
        }

        // Generate the list of situations and alarms
        for (Map.Entry<TicketRecord,TicketDetails> entry : ticketDetailsByTicket.entrySet()) {
            final TicketRecord t = entry.getKey();
            final TicketDetails details = entry.getValue();

            final Situation situation = new Situation();
            situation.setId(t.getTicketId());
            situation.setCreationTime(t.getCreationTime().getTime());
            situation.setSeverity(toSeverity(t.getSeverity()));
            situation.setSummary(t.getDescription());
            situation.setDescription(t.getDescription());

            for (OnmsAlarmSummary alarmSummary : details.getAlarmSummaries()) {
                situation.getAlarmRef().add(toAlarmRef(alarmSummary));
                alarms.getAlarm().add(alarmSummary.toAlarm());
            }

            situations.getSituation().add(situation);
        }
    }

    public void writeResultsToDisk() {
        Objects.requireNonNull(targetFolder, "target folder must be set");
        LOG.info("Marshalling results to disk...");
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(MetaModel.class, Inventory.class, Alarms.class, Situations.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            if (metaModel != null && inventory != null) {
                LOG.info("Writing {} model object definitions (meta-model)...", metaModel.getModelObjectDef().size());
                marshaller.marshal(metaModel, new File(targetFolder, "cpn.metamodel.xml"));
                LOG.info("Writing {} model object entries (inventory)...", inventory.getModelObjectEntry().size());
                marshaller.marshal(inventory, new File(targetFolder, "cpn.inventory.xml"));
            }
            LOG.info("Writing {} alarms...", alarms.getAlarm().size());
            marshaller.marshal(alarms, new File(targetFolder, "cpn.alarms.xml"));
            LOG.info("Writing {} situations...", situations.getSituation().size());
            marshaller.marshal(situations, new File(targetFolder, "cpn.situations.xml"));
        } catch (Exception e) {
            LOG.error("Oops", e);
        }
        LOG.info("Done.");
    }

    public Situations getSituations() {
        return situations;
    }

    public MetaModel getMetaModel() {
        return metaModel;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Alarms getAlarms() {
        return alarms;
    }

    private static AlarmRef toAlarmRef(OnmsAlarmSummary alarmSummary) {
        final AlarmRef cause = new AlarmRef();
        cause.setId(Integer.toString(alarmSummary.getId()));
        return cause;
    }

    private static Severity toSeverity(EventSeverity severity) {
        switch(severity) {
            case Critical:
                return Severity.CRITICAL;
            case Major:
                return Severity.MAJOR;
            case Minor:
                return Severity.MINOR;
            case Warning:
                return Severity.WARNING;
            case Information:
                return Severity.NORMAL;
            case Cleared:
                return Severity.CLEARED;
        }
        return Severity.INDETERMINATE;
    }

}
