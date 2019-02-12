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

package org.opennms.oce.tools.onms.onms2oce;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.api.CpnEntityDao;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetView;
import org.opennms.oce.tools.cpn.view.CpnDatasetViewer;
import org.opennms.oce.tools.cpn.view.ESBackedCpnDatasetViewer;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESConfiguration;
import org.opennms.oce.tools.es.ESConfigurationDao;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.onms.client.api.OnmsEntityDao;
import org.opennms.oce.tools.svc.DefaultNodeAndFactsService;
import org.opennms.oce.tools.svc.NodeAndFactsService;
import org.opennms.oce.tools.ticketdiag.TicketDetails;
import org.opennms.oce.tools.ticketdiag.TicketDiagnostic;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Ignore
public class TestDataExtractor {

    private File esConfigFile = Paths.get(System.getProperty("user.home"), ".oce", "es-config.yaml").toFile();

    @Test
    public void extract() throws Exception {
        String ticketId = "4743230";
        File targetFolder = new File("/home/jesse/git/oce-tools/opennms/src/test/resources/tickets/" + ticketId);

        final ESConfiguration esConfiguration;
        try {
            final ESConfigurationDao esConfigurationDao = new ESConfigurationDao(esConfigFile);
            esConfiguration = esConfigurationDao.getConfig();
        } catch(Exception e) {
            throw new Exception(String.format("Failed to load Elasticsearch configuration from %s.", esConfigFile), e);
        }
        final ESClient esClient = new ESClient(esConfiguration.getFirstCluster());

        // CPN Extraction
        extractCpnTickeData(ticketId, targetFolder, esClient);

        // OpenNMS Extraction
        extractOnmsData(ticketId, targetFolder, esClient);
    }

    private void extractCpnTickeData(String ticketId, File targetFolder, ESClient esClient) {
        final CpnEntityDao cpnEntityDao = new ESDataProvider(esClient);

        final TicketRecord ticketRecord = cpnEntityDao.getTicketRecord(ticketId);
        writeJsonToFile(Collections.singletonList(ticketRecord), targetFolder, "ticket.json");

        final List<TrapRecord> traps = cpnEntityDao.getTrapsInTicket(ticketId);
        traps.sort(Comparator.comparing(EventRecord::getTime));
        writeJsonToFile(traps, targetFolder, "traps.json");

        final List<EventRecord> syslogs = cpnEntityDao.getSyslogsInTicket(ticketId);
        syslogs.sort(Comparator.comparing(EventRecord::getTime));
        writeJsonToFile(syslogs, targetFolder, "syslog.json");

        final List<EventRecord> services = cpnEntityDao.getServiceEventsInTicket(ticketId);
        services.sort(Comparator.comparing(EventRecord::getTime));
        writeJsonToFile(services, targetFolder, "service.json");
    }

    private void extractOnmsData(String ticketId, File targetFolder, ESClient esClient) {
        final OnmsEntityDao onmsEntityDao = new EventClient(esClient);
        final CpnEntityDao cpnEntityDao = new ESDataProvider(esClient);
        new DefaultNodeAndFactsService(onmsEntityDao, cpnEntityDao);

        final CpnDatasetView view = new CpnDatasetView.Builder().build();
        final CpnDatasetViewer viewer = new ESBackedCpnDatasetViewer(esClient, view);
        final NodeAndFactsService nodeAndFactsService = new DefaultNodeAndFactsService(onmsEntityDao, cpnEntityDao);
        final TicketRecord ticket = viewer.getTicketWithId(ticketId);
        final TicketDiagnostic diag = new TicketDiagnostic(ticket, viewer, nodeAndFactsService);
        final TicketDetails ticketDetails = diag.getTicketDetails();

        final List<ESEventDTO> onmsEvents = ticketDetails.getOnmsEvents();
        writeJsonToFile(onmsEvents, targetFolder, "opennms.events.json");

        final List<AlarmDocumentDTO> alarmStates = ticketDetails.getAlarmSummaries().stream()
                .flatMap(a -> a.getAlarmStates().stream())
                .collect(Collectors.toList());
        writeJsonToFile(alarmStates, targetFolder, "opennms.alarms.json");

        final List<AlarmDocumentDTO> situationStates = ticketDetails.getSituationsById().values().stream()
                .flatMap(s -> s.getSituationDtos().stream())
                .collect(Collectors.toList());
        writeJsonToFile(situationStates, targetFolder, "opennms.situations.json");
    }

    private static void writeJsonToFile(Object o, File targetFolder, String fileName) {
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final String json = gson.toJson(o);
        try {
            Files.write(json.getBytes(StandardCharsets.UTF_8), new File(targetFolder, fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
