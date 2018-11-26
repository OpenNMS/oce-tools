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

package org.opennms.oce.tools.main;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kohsuke.args4j.Option;
import org.opennms.netmgt.syslogd.ByteBufferParser;
import org.opennms.netmgt.syslogd.RadixTreeSyslogParser;
import org.opennms.netmgt.syslogd.SyslogMessage;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;

import com.google.gson.Gson;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.params.Parameters;

public class CpnOnmsSyslogAudit extends AbstractCommand {
    public static final String NAME = "cpn-opennms-syslog-audit";

    @Option(name = "--from", aliases = {"-f"}, usage = "From date i.e. Oct 28 2018")
    private String from;

    @Option(name = "--to", aliases = {"-t"}, usage = "To date i.e. Oct 29 2018")
    private String to;

    @Option(name = "--count", aliases = {"-c"}, usage = "Set to print only the count")
    private boolean countOnly = false;

    private final ByteBufferParser<SyslogMessage> parser = RadixTreeSyslogParser.getRadixParser();

    private long failedCount;

    public CpnOnmsSyslogAudit() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        scroll(context.getEsClient().getJestClient(), buildSearch(), EventRecord.class, this::consumeSyslogBatch);

        if (countOnly) {
            System.out.println(failedCount);
        } else {
            if (failedCount > 0) {
                System.out.println(failedCount + " messages failed to parse correctly");
            } else {
                System.out.println("All messages parsed correctly");
            }
        }
    }

    private void consumeSyslogBatch(List<EventRecord> syslogs) {
        syslogs.stream()
                .filter(event -> !EventUtils.isClear(event))
                .forEach(syslog -> {
                    String detailedDescription = syslog.getDetailedDescription();
                    ByteBuffer bytesToParse = ByteBuffer.wrap(detailedDescription.getBytes());
                    CompletableFuture<SyslogMessage> parseFuture = parser.parse(bytesToParse);
                    Optional<SyslogMessage> parsedSyslog = Optional.empty();

                    try {
                        parsedSyslog = Optional.of(parseFuture.get());
                    } catch (InterruptedException | ExecutionException ignore) {
                    }

                    String parsedMessage = parsedSyslog.orElseThrow(RuntimeException::new).getMessage();

                    if (!parsedMessage.startsWith("%")) {
                        failedCount++;

                        if (!countOnly) {
                            System.out.println("Input syslog message: " + detailedDescription + "\n");
                            System.out.println("Resulting parsed SyslogMessage: ");
                            System.out.println(parsedSyslog + "\n");
                        }
                    }
                });
    }

    private Search buildSearch() {
        CommandUtils.DateRange range = CommandUtils.parseDateRange(from, to);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("time")
                .gte(range.getStart().toEpochSecond())
                .lt(range.getEnd().toEpochSecond())
                .format("epoch_second");
        searchSourceBuilder.query(rangeQueryBuilder);
        searchSourceBuilder.size(1000);

        return new Search.Builder(searchSourceBuilder.toString())
                .addIndex("syslogs")
                .addType("syslog")
                .addSort(new Sort("time"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();
    }

    private <T> void scroll(JestClient jestClient, Search search, Class<T> clazz, Consumer<List<T>> callback) throws IOException {
        JestResult result = jestClient.execute(search);

        while (true) {
            if (!result.isSucceeded()) {
                throw new RuntimeException(result.getErrorMessage());
            }

            // Cast the result to a search result for easy access to the hits
            SearchResult searchResult = new SearchResult(new Gson());
            searchResult.setJsonObject(result.getJsonObject());
            searchResult.setPathToResult(result.getPathToResult());
            List<SearchResult.Hit<T, Void>> hits = searchResult.getHits(clazz);
            if (hits.size() < 1) {
                break;
            }

            // Issue the callback
            callback.accept(hits.stream().map(h -> h.source).collect(Collectors.toList()));

            // Scroll
            String scrollId = result.getJsonObject().getAsJsonPrimitive("_scroll_id").getAsString();
            SearchScroll scroll = new SearchScroll.Builder(scrollId, "5m").build();
            result = jestClient.execute(scroll);
        }
    }
}
