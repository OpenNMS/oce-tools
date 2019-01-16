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

package org.opennms.oce.tools.onms.view;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESClusterConfiguration;
import org.opennms.oce.tools.es.ESConfigurationDao;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.alarmdto.EventDocumentDTO;
import org.opennms.oce.tools.onms.client.EventClient;

import com.google.gson.Gson;

import io.searchbox.client.JestResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.params.Parameters;

public class ESBackedOnmsDatasetViewer implements OnmsDatasetViewer {

    private final ESClient esClient;
    private final OnmsDatasetView view;

    private final EventClient eventClient;

    public ESBackedOnmsDatasetViewer(ESClient esClient, OnmsDatasetView view) {
        this.esClient = Objects.requireNonNull(esClient);
        this.view = Objects.requireNonNull(view);
        // FIXME - clean up below
        File esConfigFile = Paths.get(System.getProperty("user.home"), ".oce", "es-config.yaml").toFile();
        ESConfigurationDao dao = new ESConfigurationDao(esConfigFile);
        ESClusterConfiguration clusterConfiguration = dao.getConfig().getFirstCluster();

        esClient = new ESClient(clusterConfiguration);
        eventClient = new EventClient(esClient);
    }

    @Override
    public AlarmDocumentDTO getSituationWithId(Integer situationId) {
        try {
            Optional<AlarmDocumentDTO> situation = eventClient.getSituation(situationId);
            if (situation.isPresent()) {
                return situation.get();
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void getSituationsForTimeRange(Consumer<List<AlarmDocumentDTO>> callback) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("@update_time")
                .gte(view.getStartTime().toEpochSecond())
                .lt(view.getEndTime().toEpochSecond()).format("epoch_second");
        boolQuery.must(rangeQueryBuilder).must(termQuery("situation", "true"));
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(view.getBatchSize());
        getAlarmRecords(searchSourceBuilder.toString(), callback);
    }

    @Override
    public void getAlarmsForTimeRange(Consumer<List<AlarmDocumentDTO>> callback) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("@update_time")
                .gte(view.getStartTime().toEpochSecond())
                .lt(view.getEndTime().toEpochSecond()).format("epoch_second");
        boolQuery.must(rangeQueryBuilder).must(termQuery("situation", "false"));
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(view.getBatchSize());
        getAlarmRecords(searchSourceBuilder.toString(), callback);
    }

    @Override
    public void getEventsForTimeRange(Consumer<List<EventDocumentDTO>> callback) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        final RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("@timestamp")
                .gte(view.getStartTime().toEpochSecond())
                .lt(view.getEndTime().toEpochSecond()).format("epoch_second");
        boolQuery.must(rangeQueryBuilder).mustNot(termQuery("eventseverity", 2));
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(view.getBatchSize());
        final Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(esClient.getClusterConfiguration().getOpennmsEventIndex())
                .addType("eventdata")
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, EventDocumentDTO.class, callback);
        }

    @Override
    public List<AlarmDocumentDTO> getAlarmsInSituation(AlarmDocumentDTO situation, Consumer<List<AlarmDocumentDTO>> callback) {
        Set<Integer> alarmIds = situation.getRelatedAlarmIds();
        try {
            return eventClient.getAlarms(alarmIds);
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private void getAlarmRecords(String query, Consumer<List<AlarmDocumentDTO>> callback) {
        final Search search = new Search.Builder(query)
                .addIndex(esClient.getClusterConfiguration().getOpennmsAlarmIndex())
                .addType("alarm")
                .setParameter(Parameters.SCROLL, "5m")
                .build();
        scroll(search, AlarmDocumentDTO.class, callback);
    }

    private <T> void scroll(Search search, Class<T> clazz, Consumer<List<T>> callback) {
        JestResult result = null;
        try {
            result = esClient.getJestClient().execute(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            try {
                result = esClient.getJestClient().execute(scroll);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ESClient getEsClient() {
        return esClient;
    }

}
