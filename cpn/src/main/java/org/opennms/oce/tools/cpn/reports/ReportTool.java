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

package org.opennms.oce.tools.cpn.reports;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.auth.DefaultBasicAuthSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.cisco.nm.ana.ns.CommandExecuterInstrumentor;
import com.cisco.nm.ana.ns.CommandExecuterService;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.platform.Platform;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSink;
import okio.Okio;

public class ReportTool {

    private static final Logger LOG = LoggerFactory.getLogger(ReportTool.class);

    private static final Pattern PARSE_REPORT_ID_FROM_OID_PATTERN = Pattern.compile(".*\\(Id=(\\d+)\\).*");

    // Baked in for now
    private final String reportCategory = "Events Reports";
    private final List<String> reportTypes = Lists.newArrayList("Detailed Traps", "Detailed Service Events", "Detailed Syslogs", "Detailed Tickets");

    private final String url;
    private final String username;
    private final String password;
    private final boolean strictSsl;
    private final boolean logging;
    private final File execute;
    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final File destinationFolder;

    private OkHttpClient okHttpClient;

    public static class Builder {
        private String url;
        private String username;
        private String password;
        private boolean logging = true;
        private boolean strictSsl = false;
        private File execute;
        private ZonedDateTime start;
        private ZonedDateTime end;
        private File destinationFolder;

        public Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder withStrictSsl(boolean strictSsl) {
            this.strictSsl = strictSsl;
            return this;
        }

        public Builder withLogging(boolean logging) {
            this.logging = logging;
            return this;
        }

        public Builder withExecute(File execute) {
            this.execute = execute;
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

        public Builder withDestinationFolder(File destinationFolder) {
            this.destinationFolder = destinationFolder;
            return this;
        }

        public ReportTool build() {
            return new ReportTool(this);
        }
    }

    private ReportTool(Builder builder) {
        this.url = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.strictSsl = builder.strictSsl;
        this.logging = builder.logging;
        this.execute = builder.execute;
        this.start = builder.start;
        this.end = builder.end;
        this.destinationFolder = builder.destinationFolder;

        final OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        if (!strictSsl) {
            LOG.debug("Disabling strict SSL checking.");
            AnyServerX509TrustManager trustManager = new AnyServerX509TrustManager();
            SSLContext sslContext = Platform.get().getSSLContext();
            try {
                sslContext.init(null, new TrustManager[] { trustManager }, null);
            } catch (KeyManagementException e) {
                throw new RuntimeException(e);
            }
            clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            clientBuilder.hostnameVerifier((hostname, session) -> true);
        }
        if (username != null && password != null) {
            clientBuilder.authenticator((route, response) -> response.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build());
        }
        clientBuilder.connectTimeout(15, TimeUnit.SECONDS);
        clientBuilder.writeTimeout(15, TimeUnit.SECONDS);
        clientBuilder.readTimeout(60, TimeUnit.SECONDS);
        if (logging) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            clientBuilder.addInterceptor(logging);
        }
        okHttpClient = clientBuilder.build();

        if (HttpUrl.parse(url) == null) {
            throw new RuntimeException("Invalid URL: " + url);
        }
    }

    public void execute() throws IOException {
        final CommandExecuterInstrumentor executor = getExecuter();
        if (execute != null) {
            // Quick bypass that allow this tool to be used to generate arbitrary BQL requests
            final String xmlToExec = Files.toString(execute, StandardCharsets.UTF_8);
            LOG.info("Executing: {}", xmlToExec);
            final String response = execCmd(executor, xmlToExec);
            LOG.info("Response: {}", response);
            return;
        }

        // Gather all the available reports for given types & category
        final List<Report> reports = new LinkedList<>();
        for (String reportType : reportTypes) {
            reports.addAll(listReports(executor, reportCategory, reportType));
        }

        // Filter the reports that fall into the given time range
        reports.removeIf(r -> (start != null && r.getCreationTime().isBefore(start)) ||
                (end != null && r.getCreationTime().isAfter(end)));

        // Find matches in the same time window - creation time should be within 60 seconds of each other, only one by day
        final List<Report> clusteredReports = clusterReports(reports, reportTypes);

        // Download the reports i.e. a get to https://ana-cluster-ana:6081/ana/services/reports/viewReport?id=7770%26format=CSV
        for (Report report : clusteredReports) {
            downloadReport(report);
        }
    }

    private List<Report> listReports(CommandExecuterInstrumentor executor, String category, String type) {
        final String xmlCmd = getListReportsXml(category, type);
        final String xmlResponse = execCmd(executor, xmlCmd);
        return getReportsFromXml(xmlResponse);
    }

    public static List<Report> getReportsFromXml(String xml) {
        List<Report> reports = new LinkedList<>();
        InputSource source = new InputSource(new StringReader(xml));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(source);
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            XPathExpression expr = xpath.compile("//IReport");
            NodeList list= (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                Element el = (Element) node;

                Element idEL = (Element)el.getElementsByTagName("ID").item(0);
                Element typeEl = (Element)el.getElementsByTagName("Type").item(0);
                Element nameEl = (Element)el.getElementsByTagName("Name").item(0);
                Element creationTimeEl = (Element)el.getElementsByTagName("CreationTime").item(0);

                // {[ReportList(ListTargetOid={[ReportRoot][ReportCategory(Category=Events Reports)][ReportType(Type=Detailed Tickets)]})][Report(Id=7459)]}
                String idOid = idEL.getTextContent();
                Matcher m = PARSE_REPORT_ID_FROM_OID_PATTERN.matcher(idOid);
                if (!m.matches()) {
                    throw new RuntimeException("Cannot extract id from: " + idOid);
                }
                Long id = Long.valueOf(m.group(1));

                // Mon Oct 22 11:10:00 CDT 2018
                String dateStr = creationTimeEl.getTextContent();
                ZonedDateTime creationTime = ReportDateParser.parse(dateStr);

                Report report = new Report();
                report.setId(id);
                report.setType(typeEl.getTextContent());
                report.setName(nameEl.getTextContent());
                report.setCreationTime(creationTime);
                reports.add(report);
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return reports;
    }

    /**
     * Used to find a group (cluster) of reports for every day that contains one report of every different type
     * and for which all of the reports were created in a small window of time.
     *
     * @param reports all reports
     * @param reportTypes types to gather
     * @return clustered reports
     */
    protected static List<Report> clusterReports(List<Report> reports, List<String> reportTypes) {
        final List<Report> clusteredReports = new LinkedList<>();

        final Map<LocalDate, List<Report>> reportsByDay = reports.stream()
                .collect(Collectors.groupingBy(r -> r.getCreationTime().toLocalDate()
                        .with(TemporalAdjusters.ofDateAdjuster(d -> d))));

        final long maxDeltaInMs = TimeUnit.MINUTES.toMillis(1);
        for (LocalDate day : reportsByDay.keySet()) {
            final List<Report> reportsForDay = reportsByDay.get(day)
                    .stream()
                    // Sort the reports in descending order - so we find the last cluster
                    .sorted(Comparator.comparing(Report::getCreationTime).reversed())
                    .collect(Collectors.toList());

            final Map<String, Report> filteredReportsByType = new HashMap<>();

            boolean foundCluster = false;
            for (Report report : reportsForDay) {
                long reportTs = report.getCreationTime().toEpochSecond();

                // Kick out any records that are > delta
                filteredReportsByType.values().removeIf(r -> Math.abs(reportTs - r.getCreationTime().toEpochSecond()) > maxDeltaInMs);

                // Add the current element
                filteredReportsByType.put(report.getType(), report);

                if (filteredReportsByType.keySet().size() == reportTypes.size()) {
                    // We've got a cluster!
                    clusteredReports.addAll(filteredReportsByType.values());
                    foundCluster = true;
                    break;
                }
            }

            if (!foundCluster) {
                LOG.warn("Failed to find cluster of reports with types: {} on: {}. Available reports are: {}", reportTypes, day, reportsForDay);
            }
        }

        clusteredReports.sort(Comparator.comparing(Report::getCreationTime));
        return clusteredReports;
    }

    private void downloadReport(Report report) throws IOException {
        // https://ana-cluster-ana:6081/ana/services/reports/viewReport?id=7770%26format=CSV
        // Output file name = Nov_9_2018_Detailed_Traps.csv
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM_dd_yyyy");
        String dateFormatted = formatter.format(report.getCreationTime());
        File destinationFile = new File(destinationFolder, dateFormatted + "_" + report.getName() + ".csv");

        HttpUrl httpUrl = HttpUrl.parse(url)
                .newBuilder()
                .addPathSegment("services")
                .addPathSegment("reports")
                .addPathSegment("viewReport")
                .addQueryParameter("id", Long.toString(report.getId()))
                .addQueryParameter("format", "CSV")
                .build();

        LOG.info("Downloading {} to {}.", httpUrl, destinationFile);
        downloadFile(okHttpClient, httpUrl, destinationFile);
    }

    public static void downloadFile(OkHttpClient client, HttpUrl httpUrl, File file) throws IOException {
        Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            LOG.warn("Downloading file at URL {} failed. Response: {}", httpUrl, response);
            return;
        }
        ResponseBody body = response.body();
        if (body != null) {
            BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(body.source());
            sink.close();
        } else {
            LOG.error("No response body on GET request to: {}. Response: {}", httpUrl, response);
        }
    }

    private String execCmd(CommandExecuterInstrumentor executor, String bqlCmd) {
        try {
            assertIsValidXml(bqlCmd);
            // Avoid failing with: Failed to parse input cid Invalid command syntax.
            final String cmd = bqlCmd.trim();
            /*
            From the docs:
            String execute (String CID, W3CEndpointReference EPR, String callbackToken) —
              Executes the provided CID and returns a string representation of the result.
              The register command returns a notification to the provided EPR along with the registrationId.
              The callback token parameter is used by the client to set additional data with the command.
              This token is attached by the server to the result and to any future notification.
             */
            return executor.execute(cmd, null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CommandExecuterInstrumentor getExecuter() {
        final CommandExecuterService service = new CommandExecuterService();
        final CommandExecuterInstrumentor commandExecuter = service.getCommandExecuterAPI();

        final Client cxfClient = ClientProxy.getClient(commandExecuter);
        cxfClient.getRequestContext().put(Message.ENDPOINT_ADDRESS, HttpUrl.parse(url).newBuilder()
                .addPathSegment("ws")
                .addPathSegment("executer")
                .build()
                .toString());
        final HTTPConduit http = (HTTPConduit) cxfClient.getConduit();
        if (!strictSsl) {
            LOG.debug("Disabling strict SSL checking.");
            // Accept all certificates
            final TrustManager[] simpleTrustManager = new TrustManager[] { new AnyServerX509TrustManager() };
            final TLSClientParameters tlsParams = new TLSClientParameters();
            tlsParams.setTrustManagers(simpleTrustManager);
            tlsParams.setDisableCNCheck(true);
            http.setTlsClientParameters(tlsParams);
        }

        if (username != null && password != null) {
            http.setAuthSupplier(new DefaultBasicAuthSupplier());
            http.getAuthorization().setUserName(username);
            http.getAuthorization().setPassword(password);
        }

        if (logging) {
            // Log incoming and outgoing requests
            LoggingInInterceptor loggingInInterceptor = new LoggingInInterceptor();
            loggingInInterceptor.setPrettyLogging(true);
            cxfClient.getInInterceptors().add(loggingInInterceptor);

            LoggingOutInterceptor loggingOutInterceptor = new LoggingOutInterceptor();
            loggingOutInterceptor.setPrettyLogging(true);
            cxfClient.getOutInterceptors().add(loggingOutInterceptor);
        }

        return commandExecuter;
    }

    private static String getListReportsXml(String category, String type) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<command name=\"Get\">\n" +
                "    <param name=\"oid\">\n" +
                "        <value>{[ReportList(ListTargetOid={[ReportRoot][ReportCategory(Category=" + category + ")][ReportType(Type=" + type + ")]})]}</value>\n" +
                "    </param>\n" +
                "    <param name=\"rs\">\n" +
                "        <value><key name=\"\">\n" +
                "            <entry name=\"depth\">0</entry>\n" +
                "            <entry name=\"register\">false</entry>\n" +
                "            <entry name=\"cachedResultAcceptable\">false</entry>\n" +
                "            <key name=\"requiredProperties\">\n" +
                "                <key name=\"com.sheer.imo.IReport\">\n" +
                "                    <entry name=\"*\" />\n" +
                "                </key>\n" +
                "                <key name=\"com.sheer.imo.IReportList\">\n" +
                "                    <entry name=\"*\" />\n" +
                "                </key>\n" +
                "            </key>\n" +
                "        </key></value>\n" +
                "    </param>\n" +
                "</command>";
    }

    public static void assertIsValidXml(String xml) {
        Objects.requireNonNull(xml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            builder.parse(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
