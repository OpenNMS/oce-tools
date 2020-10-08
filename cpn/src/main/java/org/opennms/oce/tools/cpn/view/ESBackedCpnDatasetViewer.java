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

package org.opennms.oce.tools.cpn.view;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.es.ESClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import io.searchbox.client.JestResult;
import io.searchbox.core.ClearScroll;
import io.searchbox.core.Get;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.params.Parameters;

public class ESBackedCpnDatasetViewer implements CpnDatasetViewer {

    private static final Logger LOG = LoggerFactory.getLogger(ESBackedCpnDatasetViewer.class);

    private final ESClient esClient;
    private final CpnDatasetView view;

    public ESBackedCpnDatasetViewer(ESClient esClient, CpnDatasetView view) {
        this.esClient = Objects.requireNonNull(esClient);
        this.view = Objects.requireNonNull(view);
    }

    @Override
    public TicketRecord getTicketWithId(String ticketId) {
        Get get = new Get.Builder("tickets", ticketId).type("ticket").build();
        JestResult result = null;
        try {
            result = esClient.getJestClient().execute(get);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.getSourceAsObject(TicketRecord.class);
    }

    @Override
    public void getTicketRecordsWithRootEventTimeInRange(Consumer<List<TicketRecord>> callback) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        if (!view.isIncludeTicketsWithASingleAlarm()) {
            boolQuery.mustNot(QueryBuilders.matchQuery("alarmCount", "1"));
        }
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("rootEventTime")
                .gte(view.getStartTime().toEpochSecond())
                .lt(view.getEndTime().toEpochSecond())
                .format("epoch_second");
        boolQuery.must(rangeQueryBuilder);
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(view.getBatchSize());
        getTicketRecords(searchSourceBuilder.toString(), callback);
    }

    @Override
    public void getEventsInTicket(TicketRecord ticket, Consumer<List<EventRecord>> callback) {
        getEventsInTicket(ticket.getTicketId(), callback);
    }

    @Override
    public void getEventsInTicket(String ticketId, Consumer<List<EventRecord>> callback) {
        String query;
        if (view.getEventTypes().contains(CpnDatasetView.EventType.SERVICE)) {
            query  = createSearchForEvents(ticketId, bq -> {});
            getServiceEvents(query, callback);
        }
        if (view.getEventTypes().contains(CpnDatasetView.EventType.TRAP)) {
            query = createSearchForEvents(ticketId, bq -> bq.mustNot(QueryBuilders.matchQuery("trapTypeOid", "N/A")));
            getTrapRecords(query, (traps) -> {
                callback.accept(traps.stream().map(t -> (EventRecord)t).collect(Collectors.toList()));
            });
        }
        if (view.getEventTypes().contains(CpnDatasetView.EventType.SYSLOG)) {
            query  = createSearchForEvents(ticketId, bq -> {});
            getSyslogRecords(query, callback);
        }
    }

    /*
    public void getEventsInTimeRange(Consumer<List<EventRecord>> callback) {
        String query;
        if (view.getEventTypes().contains(CpnDatasetView.EventType.SERVICE)) {
            query  = createSearchForEvents(bq -> {});
            getServiceEvents(query, callback);
        }
        if (view.getEventTypes().contains(CpnDatasetView.EventType.TRAP)) {
            query = createSearchForEvents(bq -> bq.mustNot(QueryBuilders.matchQuery("trapTypeOid", "N/A")));
            getTrapRecords(query, (traps) -> {
                callback.accept(traps.stream().map(t -> (EventRecord)t).collect(Collectors.toList()));
            });
        }
        if (view.getEventTypes().contains(CpnDatasetView.EventType.SYSLOG)) {
            query  = createSearchForEvents(bq -> {});
            getSyslogRecords(query, callback);
        }
    }
    */

    private String createSearchForEvents(String matchTicketId, Consumer<BoolQueryBuilder> bqb) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        if (matchTicketId != null) {
            boolQuery.must(QueryBuilders.matchQuery("ticketId", matchTicketId));
        } else {
            boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        }
        boolean didFilterByTime = false;
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("time")
                .format("epoch_second");
        if (view.getStartTime() != null) {
            rangeQueryBuilder.gte(view.getStartTime().toEpochSecond());
            didFilterByTime = true;
        }
        if (view.getEndTime() != null) {
            rangeQueryBuilder.lt(view.getEndTime().toEpochSecond());
            didFilterByTime = true;
        }
        if (didFilterByTime) {
            boolQuery.must(rangeQueryBuilder);
        }
        bqb.accept(boolQuery);
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(view.getBatchSize());
        return searchSourceBuilder.toString();
    }

    private void getSyslogRecords(String query, Consumer<List<EventRecord>> callback) {
        final Search search = new Search.Builder(query)
                .addIndex("syslogs")
                .addType("syslog")
                .addSort(new Sort("time"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, EventRecord.class, callback);
    }

    private void getTrapRecords(String query, Consumer<List<TrapRecord>> callback) {
        final Search search = new Search.Builder(query)
                .addIndex("traps")
                .addType("trap")
                .addSort(new Sort("time"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, TrapRecord.class, callback);
    }

    private void getServiceEvents(String query, Consumer<List<EventRecord>> callback) {
        final Search search = new Search.Builder(query)
                .addIndex("services")
                .addType("service")
                .addSort(new Sort("time"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, EventRecord.class, callback);
    }

    private void getTicketRecords(String query, Consumer<List<TicketRecord>> callback) {
        final Search search = new Search.Builder(query)
                .addIndex("tickets")
                .addType("ticket")
                .addSort(new Sort("creationTime"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, TicketRecord.class, callback);
    }

    private <T> void scroll(Search search, Class<T> clazz, Consumer<List<T>> callback) {
        JestResult result = null;
        try {
            result = esClient.getJestClient().execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Set<String> scrollIds = new LinkedHashSet<>();
        try {
            while(true) {
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
                scrollIds.add(scrollId);
                SearchScroll scroll = new SearchScroll.Builder(scrollId, "5m").build();
                try {
                    result = esClient.getJestClient().execute(scroll);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            if (!scrollIds.isEmpty()) {
                ClearScroll clearScroll = new ClearScroll.Builder()
                        .addScrollIds(scrollIds).build();
                try {
                    JestResult clearResult = esClient.getJestClient().execute(clearScroll);
                    if (!clearResult.isSucceeded()) {
                        LOG.info("Failed to clear one or more scrolls: {}", clearResult.getErrorMessage());
                    }
                } catch (IOException e) {
                    LOG.warn("Error while clearing scrolls ids: {}", scrollIds, e);
                }
            }
        }

    }

    public ESClient getEsClient() {
        return esClient;
    }

}
