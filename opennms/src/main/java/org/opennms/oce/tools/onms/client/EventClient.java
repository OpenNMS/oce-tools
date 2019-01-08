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

package org.opennms.oce.tools.onms.client;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESClusterConfiguration;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.params.Parameters;

public class EventClient {
    public static final int BATCH_SIZE = 100;

    private final ESClusterConfiguration esClusterConfiguration;
    private final JestClient client;

    public EventClient(ESClient client) {
        this.esClusterConfiguration = client.getClusterConfiguration();
        this.client = client.getJestClient();
    }

    public Optional<AlarmDocumentDTO> findAlarmForEventWithId(Integer id) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1);
        searchSourceBuilder.query(matchQuery("last-event.id", Integer.toString(id)));
        String query = searchSourceBuilder.toString();
        final Search search = new Search.Builder(query)
                .addIndex("opennms-alarms-*")
                .addType(AlarmDocumentDTO.TYPE)
                // Grab the earliest
                .addSort(new Sort("@update-time", Sort.Sorting.ASC))
                .build();
        final SearchResult result;
        try {
            result = client.execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!result.isSucceeded()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        final List<SearchResult.Hit<AlarmDocumentDTO, Void>> hits = result.getHits(AlarmDocumentDTO.class);
        return hits.stream().map(h -> h.source).findFirst();
    }

    public Optional<AlarmDocumentDTO> findSituationForAlarmWithId(Integer id) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1);
        searchSourceBuilder.query(matchQuery("related-alarm-ids", Integer.toString(id)));
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex("opennms-alarms-*")
                .addType(AlarmDocumentDTO.TYPE)
                // Grab the earliest
                .addSort(new Sort("@update-time", Sort.Sorting.ASC))
                .build();
        final SearchResult result;
        try {
            result = client.execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!result.isSucceeded()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        final List<SearchResult.Hit<AlarmDocumentDTO, Void>> hits = result.getHits(AlarmDocumentDTO.class);
        return hits.stream().map(h -> h.source).findFirst();
    }

    public Optional<ESEventDTO> findBestSyslogMessageMatching(long timestamp, String hostname, String substringInMessage) throws IOException {
        final long delta = TimeUnit.SECONDS.toMillis(1);
        final long startMs = timestamp - delta;
        final long endMs = timestamp + delta;
        final List<ESEventDTO> matches = getSyslogEvents(startMs, endMs, hostname, substringInMessage);
        // Find the one, that closest to the expected time
        return matches.stream().min(Comparator.comparingLong(e -> Math.abs(timestamp - e.getTimestamp().getTime())));
    }

    public Optional<ESEventDTO> findBestTrapMatching(long timestamp, String hostname, String trapTypeOid) throws IOException {
        final long delta = TimeUnit.MINUTES.toMillis(1);
        final long startMs = timestamp - delta;
        final long endMs = timestamp + delta;
        final List<ESEventDTO> matches = getTrapEvents(startMs, endMs, hostname, trapTypeOid);
        // Find the one, that closest to the expected time
        return matches.stream().min(Comparator.comparingLong(e -> Math.abs(timestamp - e.getTimestamp().getTime())));
    }

    public List<ESEventDTO> getTrapEvents(long startMs, long endMs, String hostname, String trapTypeOid) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQuery()
                .must(prefixQuery("nodelabel", hostname))
                .must(nestedQuery("p_oids", termQuery("p_oids.value",trapTypeOid), ScoreMode.None))
                .must(rangeQuery("@timestamp").gte(startMs).lte(endMs).includeLower(true).includeUpper(true).format("epoch_millis")));
        String query = searchSourceBuilder.toString();
        final Search search = new Search.Builder(query)
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, matchedEvents::addAll);
        return matchedEvents;
    }
    
    public List<ESEventDTO> getTrapEvents(long startMs, long endMs) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.boolQuery()
                .must(nestedQuery("p_oids", boolQuery().must(existsQuery("p_oids")), ScoreMode.None))
                .must(rangeQuery("@timestamp").gte(startMs).lte(endMs).includeLower(true).includeUpper(true).format("epoch_millis")));
        String query = searchSourceBuilder.toString();
        final Search search = new Search.Builder(query)
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, matchedEvents::addAll);
        return matchedEvents;
    }

    public List<ESEventDTO> getSyslogEvents(long startMs, long endMs, String hostname, String group) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.must(matchQuery("eventsource", "syslogd"));
        searchSourceBuilder.query(boolQuery);
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("@timestamp")
                .gte(startMs)
                .lte(endMs)
                .includeLower(true)
                .includeUpper(true)
                .format("epoch_millis");
        searchSourceBuilder.query(rangeQueryBuilder);
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, events -> {
            for (ESEventDTO event : events) {
                if (event.getNodeLabel() == null || !event.getNodeLabel().contains(hostname)) {
                    continue;
                }
                if (event.getSyslogMessage() != null && event.getSyslogMessage().contains(group)) {
                    matchedEvents.add(event);
                }
            }
        });
        return matchedEvents;
    }

    public List<ESEventDTO> getSyslogEvents(long startMs, long endMs) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        boolQuery.must(matchQuery("eventsource", "syslogd"));
        searchSourceBuilder.query(boolQuery);
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("@timestamp")
                .gte(startMs)
                .lte(endMs)
                .includeLower(true)
                .includeUpper(true)
                .format("epoch_millis");
        searchSourceBuilder.query(rangeQueryBuilder);
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, matchedEvents::addAll);
        return matchedEvents;
    }

    public Optional<ESEventDTO> findFirstEventForNodeLabelPrefix(long startMs, long endMs, String nodeLabelPrefix) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.sort("@timestamp", SortOrder.ASC);
        searchSourceBuilder.size(1); // limit to 1
        searchSourceBuilder.query(boolQuery()
                .must(prefixQuery("nodelabel", nodeLabelPrefix))
                .must(rangeQuery("@timestamp").gte(startMs).lte(endMs).includeLower(true).includeUpper(true).format("epoch_millis")));
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, matchedEvents::addAll);
        return matchedEvents.stream().findFirst();
    }

    public List<ESEventDTO> getSituationsForHostname(long startMs, long endMs, String hostname) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.sort("@timestamp", SortOrder.ASC);
        searchSourceBuilder.query(QueryBuilders.boolQuery()
                .must(prefixQuery("situation", "true"))
                .must(prefixQuery("nodelabel", hostname))
                .must(rangeQuery("@timestamp").gte(startMs).lte(endMs).includeLower(true).includeUpper(true).format("epoch_millis")));
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, matchedEvents::addAll);
        return matchedEvents;
    }

    public List<ESEventDTO> getEventsForSituation(int situationId) throws IOException {
        Optional<AlarmDocumentDTO> situation = findAlarmForEventWithId(situationId);
        if (!situation.isPresent()) {
            return Collections.emptyList();
        }
        List<String> relatedReductionKeys = situation.get().getRelatedAlarmReductionKeys();

        if (relatedReductionKeys == null || relatedReductionKeys.isEmpty()) {
            return Collections.emptyList();
        }

        String[] alarmReductionKeys = new String[relatedReductionKeys.size()];
        relatedReductionKeys.toArray(alarmReductionKeys);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final TermsQueryBuilder termQuery = new TermsQueryBuilder("alarmreductionkey", alarmReductionKeys);
        searchSourceBuilder.query(termQuery);
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .setParameter(Parameters.SCROLL, "5m")
                .build();

        final List<ESEventDTO> matchedEvents = new ArrayList<>();
        scroll(search, ESEventDTO.class, matchedEvents::addAll);
        return matchedEvents;
    }

    public long getNumSyslogEvents(long startMs, long endMs, int nodeId) throws IOException {
        return getNumEventsForMatching(startMs, endMs, nodeId, termQuery("eventsource", "syslogd"));
    }

    public long getNumTrapEvents(long startMs, long endMs, int nodeId) throws IOException {
        return getNumEventsForMatching(startMs, endMs, nodeId, nestedQuery("p_oids", boolQuery().must(existsQuery("p_oids")), ScoreMode.None));
    }

    public long getNumEventsForMatching(long startMs, long endMs, int nodeId, QueryBuilder queryBuilder) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.sort("@timestamp", SortOrder.ASC);
        searchSourceBuilder.size(0); // we don't need the results, only the count
        searchSourceBuilder.query(boolQuery()
                .must(termQuery("nodeid", nodeId))
                .must(queryBuilder)
                .must(rangeQuery("@timestamp").gte(startMs).lte(endMs).includeLower(true).includeUpper(true).format("epoch_millis")));
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClusterConfiguration.getOpennmsEventIndex())
                .addSort(new Sort("@timestamp"))
                .build();

        SearchResult result = client.execute(search);
        if (!result.isSucceeded()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getTotal();
    }

    private <T> void scroll(Search search, Class<T> clazz, Consumer<List<T>> callback) throws IOException {
        JestResult result = client.execute(search);
        while(true) {
            if (!result.isSucceeded()) {
                throw new RuntimeException(result.getErrorMessage());
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Date.class, new DateTimeTypeConverter()).create();

            // Cast the result to a search result for easy access to the hits
            SearchResult searchResult = new SearchResult(gson);
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
            result = client.execute(scroll);
        }
    }

}
