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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.opennms.netmgt.model.OnmsNode;
import org.opennms.web.rest.model.v2.AlarmCollectionDTO;
import org.opennms.web.rest.model.v2.EventCollectionDTO;
import org.opennms.web.rest.model.v2.EventDTO;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.gson.Gson;

import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class OpennmsRestClient {

    private final HttpUrl baseUrl;

    private final OkHttpClient client;

    private final Gson gson = new Gson();

    private final ObjectMapper objectMapper = createDefaultObjectMapper();


    public OpennmsRestClient(String url, String username, String password) {
        this(url, username, password, true);
    }

    public OpennmsRestClient(String url, String username, String password, boolean log) {
        this.baseUrl = HttpUrl.parse(url);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.authenticator((route, response) -> response.request().newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build());
        builder.connectTimeout(10, TimeUnit.SECONDS);
        builder.writeTimeout(10, TimeUnit.SECONDS);
        builder.readTimeout(30, TimeUnit.SECONDS);
        if (log) {
            builder.addInterceptor(logging);
        }
        client = builder.build();
    }


    private static class ServerInfo {
        private String displayVersion;
        private String version;
        private String packageName;
        private String packageDescription;
    }

    String getServerVersion() throws Exception {
        final HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("rest")
                .addPathSegment("info")
                .build();
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        final Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception("Retrieving info failed with: " + response.message());
        }
        final ServerInfo info = gson.fromJson(response.body().string(), ServerInfo.class);
        return info.version;
    }

    public Optional<EventDTO> getLastEventWithUeiOn(int nodeId, String uei) throws Exception {
        final HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("v2")
                .addPathSegment("events")
                .addQueryParameter("limit", "1")
                .addQueryParameter("orderBy", "event.eventCreateTime")
                .addQueryParameter("order", "desc")
                .addQueryParameter("_s", String.format("node.id==%d;event.uei==%s", nodeId, uei))
                .build();
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        final Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception(String.format("GET failed with: %s", response.message()));
        }

        if (response.body() == null || response.body().contentLength() == 0) {
            return Optional.empty();
        }
        final EventCollectionDTO eventCollectionDTO = objectMapper.readValue(response.body().charStream(), EventCollectionDTO.class);
        return Optional.of(eventCollectionDTO.getObjects().get(0));
    }

    public Optional<OnmsNode> getNodeWithId(int nodeId) throws Exception {
        final HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("v2")
                .addPathSegment("nodes")
                .addPathSegment(Integer.toString(nodeId))
                .build();
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        final Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception(String.format("GET failed with: %s", response.message()));
        }

        if (response.body() == null || response.body().contentLength() == 0) {
            return Optional.empty();
        }

        return Optional.of(objectMapper.readValue(response.body().charStream(), OnmsNode.class));
    }

    public AlarmCollectionDTO getAlarmsOnNode(int nodeId) throws Exception {
        final String fiqlExpression = "node.id==" + nodeId;
        return getAlarmsFor(fiqlExpression);
    }

    public AlarmCollectionDTO getAlarmsOnNodeWithUei(int nodeId, String uei) throws Exception {
        final String fiqlExpression = String.format("node.id==%d;uei==%s", nodeId, uei);
        return getAlarmsFor(fiqlExpression);
    }

    private AlarmCollectionDTO getAlarmsFor(String fiqlExpression) throws Exception {
        final HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("v2")
                .addPathSegment("alarms")
                .addQueryParameter("limit", Integer.toString(0))
                .addQueryParameter("_s", fiqlExpression)
                .build();
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        final Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception(String.format("GET failed with: %s", response.message()));
        }

        if (response.body() == null || response.body().contentLength() == 0) {
            return new AlarmCollectionDTO();
        }

        return objectMapper.readValue(response.body().charStream(), AlarmCollectionDTO.class);
    }

    void clearAlarm(long alarmId) throws Exception {
        performActionOnAlarm(alarmId, "clear", "true");
    }

    void acknowledgeAlarm(long alarmId) throws Exception {
        performActionOnAlarm(alarmId, "ack", "true");
    }

    private void performActionOnAlarm(long alarmId, String actionName, String actionValue) throws Exception {
        final HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("v2")
                .addPathSegment("alarms")
                .addPathSegment(Long.toString(alarmId))
                .build();
        final RequestBody body = new FormBody.Builder()
                .add(actionName, actionValue)
                .build();
        final Request request = new Request.Builder()
                .url(url)
                .put(body)
                .build();
        final Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception(String.format("%s failed with: %s", actionName, response.message()));
        }
    }

    private static ObjectMapper createDefaultObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        final AnnotationIntrospector introspectorPair = AnnotationIntrospector.pair(
                new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector(TypeFactory.defaultInstance()));
        mapper.setAnnotationIntrospector(introspectorPair);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        return mapper;
    }
}
