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

import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.opennms.oce.tools.cpn.api.CpnEntityDao;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.es.ESClient;

import com.google.gson.Gson;

import io.searchbox.client.JestResult;
import io.searchbox.core.Get;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.params.Parameters;

public class ESDataProvider implements CpnEntityDao {

    public static final int BATCH_SIZE = 10000;

    private ESClient esClient;

    public ESDataProvider(ESClient esClient) {
        this.esClient = Objects.requireNonNull(esClient);
    }

    public TicketRecord getTicketRecord(int id) throws IOException {
        return getTicketRecord(Integer.valueOf(id).longValue());
    }

    public TicketRecord getTicketRecord(long id) throws IOException {
        Get get = new Get.Builder("tickets", Long.toString(id)).type("ticket").build();
        JestResult result = esClient.getJestClient().execute(get);
        return result.getSourceAsObject(TicketRecord.class);
    }

    public void getTicketRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, Consumer<List<TicketRecord>> callback) throws IOException {
        getTicketRecordsInRange(startTime, endTime, Collections.emptyList(), Collections.emptyList(), callback);
    }

    @Override
    public void getTicketRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<TicketRecord>> callback) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("rootEventTime")
                .gte(startTime.toEpochSecond())
                .lt(endTime.toEpochSecond())
                .format("epoch_second");
        boolQuery.must(rangeQueryBuilder);
        // Add includes
        for (QueryBuilder includeQuery : includeQueries) {
            boolQuery.must(includeQuery);
        }
        // Add excludes
        for (QueryBuilder excludeQuery : excludeQueries) {
            boolQuery.mustNot(excludeQuery);
        }
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(BATCH_SIZE);
        getTicketRecords(searchSourceBuilder.toString(), callback);
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

    public List<TrapRecord> getTrapsInTicket(String id) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(termQuery("ticketId",id)));
        String query = searchSourceBuilder.toString();

        final Search search = new Search.Builder(query)
                .addIndex("traps")
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<TrapRecord> traps = new ArrayList<>();
        scroll(search, TrapRecord.class, traps::addAll);
        return traps;
    }

    public List<EventRecord> getSyslogsInTicket(String id) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(termQuery("ticketId",id)));
        String query = searchSourceBuilder.toString();

        final Search search = new Search.Builder(query)
                .addIndex("syslogs")
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<EventRecord> syslogs = new ArrayList<>();
        scroll(search, EventRecord.class, syslogs::addAll);
        return syslogs;
    }

    public List<EventRecord> getServiceEventsInTicket(String id) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(termQuery("ticketId",id)));
        String query = searchSourceBuilder.toString();

        final Search search = new Search.Builder(query)
                .addIndex("services")
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<EventRecord> events = new ArrayList<>();
        scroll(search, EventRecord.class, events::addAll);
        return events;
    }

    public TrapRecord getTrapRecord(String id) throws IOException {
        Get get = new Get.Builder("traps", id).type("trap").build();
        JestResult result = esClient.getJestClient().execute(get);
        return result.getSourceAsObject(TrapRecord.class);
    }

    public void getTrapRecords(Consumer<List<TrapRecord>> callback) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        //boolQuery.must(QueryBuilders.existsQuery("longDescription"));
        boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        boolQuery.mustNot(QueryBuilders.matchQuery("trapTypeOid", "N/A"));
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(BATCH_SIZE);
        getTrapRecords(searchSourceBuilder.toString(), callback);
    }

    public void getTrapRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, Consumer<List<TrapRecord>> callback) throws IOException {
        getTrapRecordsInRange(startTime, endTime, Collections.emptyList(), Collections.emptyList(), callback);
    }

    @Override
    public void getTrapRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<TrapRecord>> callback) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        boolQuery.mustNot(QueryBuilders.matchQuery("trapTypeOid", "N/A"));
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("time")
                .gte(startTime.toEpochSecond())
                .lt(endTime.toEpochSecond())
                .format("epoch_second");
        boolQuery.must(rangeQueryBuilder);
        // Add includes
        for (QueryBuilder includeQuery : includeQueries) {
            boolQuery.must(includeQuery);
        }
        // Add excludes
        for (QueryBuilder excludeQuery : excludeQueries) {
            boolQuery.mustNot(excludeQuery);
        }
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(BATCH_SIZE);
        getTrapRecords(searchSourceBuilder.toString(), callback);
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

    public void getServiceEventsInRange(ZonedDateTime startTime, ZonedDateTime endTime, Consumer<List<EventRecord>> callback) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("time")
                .gte(startTime.toEpochSecond())
                .lt(endTime.toEpochSecond())
                .format("epoch_second");
        boolQuery.must(rangeQueryBuilder);
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(BATCH_SIZE);
        getServiceEvents(searchSourceBuilder.toString(), callback);
    }

    private void getServiceEvents(String query, Consumer<List<EventRecord>> callback) throws IOException {
        final Search search = new Search.Builder(query)
                .addIndex("services")
                .addType("service")
                .addSort(new Sort("time"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, EventRecord.class, callback);
    }

    public EventRecord getSyslogRecord(String id) throws IOException {
        Get get = new Get.Builder("syslogs", id).type("syslog").build();
        JestResult result = esClient.getJestClient().execute(get);
        return result.getSourceAsObject(EventRecord.class);
    }

    public void getSyslogRecords(Consumer<List<EventRecord>> callback) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(BATCH_SIZE);
        getSyslogRecords(searchSourceBuilder.toString(), callback);
    }

    public void getSyslogRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, Consumer<List<EventRecord>> callback) throws IOException {
        getSyslogRecordsInRange(startTime, endTime, callback);
    }

    @Override
    public void getSyslogRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<EventRecord>> callback, QueryBuilder... queries)  {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("time")
                .gte(startTime.toEpochSecond())
                .lt(endTime.toEpochSecond())
                .format("epoch_second");
        boolQuery.must(rangeQueryBuilder);
        for (QueryBuilder query : queries) {
            boolQuery.must(query);
        }
        // Add includes
        for (QueryBuilder includeQuery : includeQueries) {
            boolQuery.must(includeQuery);
        }
        // Add excludes
        for (QueryBuilder excludeQuery : excludeQueries) {
            boolQuery.mustNot(excludeQuery);
        }
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(BATCH_SIZE);
        getSyslogRecords(searchSourceBuilder.toString(), callback);
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

    public List<EventRecord> getEventsByTicketId(String ticketId) throws IOException {
        final List<EventRecord> allEventsInTicket = new LinkedList<>();
        allEventsInTicket.addAll(getSyslogsInTicket(ticketId));
        allEventsInTicket.addAll(getTrapsInTicket(ticketId));
        allEventsInTicket.addAll(getServiceEventsInTicket(ticketId));
        return allEventsInTicket;
    }

    @Override
    public long getNumSyslogEvents(ZonedDateTime startTime, ZonedDateTime endTime, String hostname, List<QueryBuilder> excludeQueries) {
        return getNumEventsForHostname(startTime, endTime, hostname, excludeQueries, "syslogs", "syslog");
    }

    @Override
    public long getNumTrapEvents(ZonedDateTime startTime, ZonedDateTime endTime, String hostname, List<QueryBuilder> excludeQueries) {
        return getNumEventsForHostname(startTime, endTime, hostname, excludeQueries, "traps", "trap");
    }

    public long getNumEventsForHostname(ZonedDateTime startTime, ZonedDateTime endTime, String hostname, List<QueryBuilder> excludeQueries, String index, String type) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0); // we don't need the results, only the count
        final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(matchPhraseQuery("location", hostname))
                .must(rangeQuery("time").gte(startTime.toEpochSecond()).lte(endTime.toEpochSecond()).includeLower(true).includeUpper(true).format("epoch_second"));
        // Add excludes
        for (QueryBuilder excludeQuery : excludeQueries) {
            boolQuery.mustNot(excludeQuery);
        }
        searchSourceBuilder.query(boolQuery);
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(index)
                .addType(type)
                .build();
        SearchResult result = null;
        try {
            result = esClient.getJestClient().execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!result.isSucceeded()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getTotal();
    }

    @Override
    public void getDistinctLocations(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> excludeQueries, Consumer<List<String>> callback) {

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.mustNot(QueryBuilders.matchQuery("ticketId", ""));
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("time")
                .gte(startTime.toEpochSecond())
                .lt(endTime.toEpochSecond())
                .format("epoch_second");
        boolQuery.must(rangeQueryBuilder);
        // Add excludes
        for (QueryBuilder excludeQuery : excludeQueries) {
            boolQuery.mustNot(excludeQuery);
        }
        searchSourceBuilder.query(boolQuery);

        AggregationBuilder termsAgg = AggregationBuilders.terms("uniq_location")
                .field("location.keyword")
                .size(100000); // FIXME: What if there are more?

        searchSourceBuilder.aggregation(termsAgg);
        searchSourceBuilder.size(0);

        // Search for syslogs
        Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex("syslogs")
                .addType("syslog")
                .build();
        SearchResult result = null;
        try {
            result = esClient.getJestClient().execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!result.isSucceeded()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        TermsAggregation terms = result.getAggregations().getTermsAggregation("uniq_location");
        List<String> locations = terms.getBuckets().stream().map(TermsAggregation.Entry::getKeyAsString).collect(Collectors.toList());
        callback.accept(locations);

        // Search for traps
        search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex("traps")
                .addType("trap")
                .build();
        try {
            result = esClient.getJestClient().execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!result.isSucceeded()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        terms = result.getAggregations().getTermsAggregation("uniq_location");
        locations = terms.getBuckets().stream().map(TermsAggregation.Entry::getKeyAsString).collect(Collectors.toList());
        callback.accept(locations);
    }

    private <T> void scroll(Search search, Class<T> clazz, Consumer<List<T>> callback) {
        try {
            JestResult result = esClient.getJestClient().execute(search);
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
                SearchScroll scroll = new SearchScroll.Builder(scrollId, "5m").build();
                result = esClient.getJestClient().execute(scroll);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ESClient getEsClient() {
        return esClient;
    }
}
