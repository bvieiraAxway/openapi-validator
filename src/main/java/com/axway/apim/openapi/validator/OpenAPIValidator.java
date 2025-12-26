package com.axway.apim.openapi.validator;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import com.axway.apim.openapi.validator.Utils.TraceLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenAPIValidator {

    private final APIManagerHttpClient apiManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private boolean useOriginalAPISpec = false;

    public OpenAPIValidator(String apiManagerUrl, String username, String password) throws Exception {
        this.apiManager = new APIManagerHttpClient(apiManagerUrl, username, password);
    }

    public String getSchema(String apiId) throws Exception {
        Utils.traceMessage("Loading API specification for API-ID: " + apiId, TraceLevel.INFO);

        if (!useOriginalAPISpec) {
            return getFrontendAPISpec(apiId);
        }

        String backendApiID = getBackendAPIID(apiId);
        Utils.traceMessage(
                "Loading original imported API specification using backend API-ID: " + backendApiID,
                TraceLevel.INFO
        );
        return getBackendAPISpec(backendApiID);
    }

    private String getFrontendAPISpec(String apiId) throws Exception {

        // 1️⃣ Try Swagger / OpenAPI 2.0
        String spec = tryLoadSpec(apiId, "2.0");
        if (spec != null) {
            return spec;
        }

        // 2️⃣ Fallback to OpenAPI 3.0
        Utils.traceMessage(
                "Swagger 2.0 specification not available, trying OpenAPI 3.0.",
                TraceLevel.INFO
        );

        spec = tryLoadSpec(apiId, "3.0");
        if (spec != null) {
            return spec;
        }

        throw new IllegalArgumentException(
                "Unable to load API specification (Swagger 2.0 or OpenAPI 3.0) from API Manager."
        );
    }

    private String tryLoadSpec(String apiId, String version) throws Exception {
        String endpoint = "/discovery/swagger/api/id/" + apiId + "?swaggerVersion=" + version;

        try (CloseableHttpResponse response = apiManager.get(endpoint)) {

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());

            Utils.traceMessage(
                    "Received response for swaggerVersion=" + version +
                    " status=" + statusCode,
                    TraceLevel.DEBUG
            );

            if (statusCode == 200 && body != null && !body.isEmpty()) {
                return body;
            }

            return null;
        }
    }

    private String getBackendAPISpec(String backendApiId) throws Exception {
        String endpoint = "/apirepo/" + backendApiId + "/download?original=true";

        try (CloseableHttpResponse response = apiManager.get(endpoint)) {

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());

            if (statusCode != 200) {
                Utils.traceMessage(
                        "Error getting original API specification. Status: " + statusCode +
                        ", Response: " + body,
                        TraceLevel.ERROR
                );
                throw new IllegalArgumentException("Error loading original API specification.");
            }

            return body;
        }
    }

    private String getBackendAPIID(String apiId) throws Exception {
        String endpoint = "/proxies/" + apiId;

        try (CloseableHttpResponse response = apiManager.get(endpoint)) {

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());

            if (statusCode != 200) {
                if (statusCode == 403 && body != null && body.contains("Forbidden")) {
                    Utils.traceMessage(
                            "Forbidden response received. Check if API-ID is correct: " + apiId,
                            TraceLevel.INFO
                    );
                }

                Utils.traceMessage(
                        "Error loading backend API-ID. Status: " + statusCode +
                        ", Response: " + body,
                        TraceLevel.ERROR
                );
                throw new IllegalArgumentException("Error loading backend API-ID for API: " + apiId);
            }

            JsonNode node = mapper.readTree(body);
            return node.get("apiId").asText();
        }
    }

    public boolean isUseOriginalAPISpec() {
        return useOriginalAPISpec;
    }

    public void setUseOriginalAPISpec(boolean useOriginalAPISpec) {
        this.useOriginalAPISpec = useOriginalAPISpec;
    }
}