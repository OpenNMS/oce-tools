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

package org.opennms.oce.tools.cpn;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvCpnDatasetLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CsvCpnDatasetLoader.class);

    public static CpnDataset loadDataset(File sourceFolder, ZoneId timeZone) throws IOException {
        if (!sourceFolder.isDirectory()) {
            throw new IOException(sourceFolder + " is not a directory!");
        }
        List<File> ticketCsvFiles = new ArrayList<>();
        List<File> serviceCsvFiles = new ArrayList<>();
        List<File> syslogCsvFiles = new ArrayList<>();
        List<File> trapCsvFiles = new ArrayList<>();
        for (String fileName : sourceFolder.list((dir, name) -> name.toLowerCase().endsWith(".csv"))) {
            final String fileNameLower = fileName.toLowerCase();
            final File targetFile = new File(sourceFolder, fileName);
            if (fileNameLower.contains("ticket")) {
                ticketCsvFiles.add(targetFile);
            } else if (fileNameLower.contains("service")) {
                serviceCsvFiles.add(targetFile);
            } else if (fileNameLower.contains("syslog")) {
                syslogCsvFiles.add(targetFile);
            } else if (fileNameLower.contains("trap")) {
                trapCsvFiles.add(targetFile);
            }
        }
        return loadDataset(ticketCsvFiles, serviceCsvFiles, syslogCsvFiles, trapCsvFiles, timeZone);
    }

    public static CpnDataset loadDataset(List<File> ticketCsvFiles, List<File> serviceCsvFiles, List<File> syslogCsvFiles, List<File> trapCsvFiles, ZoneId timeZone) throws IOException {
        final DateHandler dateHandler = new DateHandler(timeZone);
        final List<File> allFiles = new ArrayList<>();
        allFiles.addAll(ticketCsvFiles);
        allFiles.addAll(serviceCsvFiles);
        allFiles.addAll(syslogCsvFiles);
        allFiles.addAll(trapCsvFiles);
        LOG.debug("Loading data from files: {}", allFiles);

        final List<TicketRecord> tickets = new ArrayList<>();
        for (File csvFile : ticketCsvFiles) {
            tickets.addAll(getTickets(csvFile, dateHandler));
        }

        final List<EventRecord> serviceEvents = new ArrayList<>();
        for (File csvFile : serviceCsvFiles) {
            serviceEvents.addAll(getEvents(csvFile, "service", dateHandler));
        }

        final List<EventRecord> syslogEvents = new ArrayList<>();
        for (File csvFile : syslogCsvFiles) {
            syslogEvents.addAll(getEvents(csvFile, "syslog", dateHandler));
        }

        final List<TrapRecord> traps = new ArrayList<>();
        for (File csvFile : trapCsvFiles) {
            try {
                traps.addAll(getTraps(csvFile, dateHandler));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse traps in: " + csvFile, e);
            }
        }
        LOG.debug("Done loading files.");

        return new CsvBackedCpnDataset(tickets, serviceEvents, syslogEvents, traps);
    }

    private static List<TicketRecord> getTickets(File csvFile, DateHandler dateHandler) throws IOException {
        final Reader in = new FileReader(csvFile);
        final Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);
        boolean first = true;
        final List<TicketRecord> tickets = new ArrayList<>();
        for (CSVRecord record : records) {
            if (first) {
                // Skip the header
                first = false;
                continue;
            }
            tickets.add(new TicketRecord(record, dateHandler));
        }
        return tickets;
    }

    private static List<EventRecord> getEvents(File csvFile, String source, DateHandler dateHandler) throws IOException {
        final Reader in = new FileReader(csvFile);
        final Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);
        boolean first = true;
        final List<EventRecord> events = new ArrayList<>();
        for (CSVRecord record : records) {
            if (first) {
                // Skip the header
                first = false;
                continue;
            }
            events.add(new EventRecord(source, record, dateHandler));
        }
        return events;
    }

    private static List<TrapRecord> getTraps(File csvFile, DateHandler dateHandler) throws IOException {
        final Reader in = new FileReader(csvFile);
        final Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);
        boolean first = true;
        final List<TrapRecord> traps = new ArrayList<>();
        for (CSVRecord record : records) {
            if (first) {
                // Skip the header
                first = false;
                continue;
            }
            traps.add(new TrapRecord(record, dateHandler));
        }
        return traps;
    }

}
