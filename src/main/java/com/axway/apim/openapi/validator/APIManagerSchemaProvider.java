package com.axway.apim.openapi.validator;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import com.axway.apim.openapi.validator.Utils.TraceLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class APIManagerSchemaProvider {

    private final APIManagerHttpClient apiManager;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * If true, downloads the originally imported backend API specification.
     * If false, downloads the frontend API specification.
     */
    private boolean useOriginalAPISpec = false;

    public APIManagerSchemaProvider(String apiManagerUrl, String username, String password) throws Exception {
        this.apiManager = new APIManagerHttpClient(apiManagerUrl, username, password);
    }

    public String getSchema(String apiId) throws Exception {
        Utils.traceMessage("Loading API specification for API-ID: " + apiId, TraceLevel.INFO);

        if (!useOriginalAPISpec) {
            return getFrontendAPISpec(apiId);
        }

        String backendApiID = getBackendAPIID(apiId);
        Utils.traceMessage(
            "Loading original backend API specification using backend API-ID: " + backendApiID,
            TraceLevel.INFO
        );
        return getBackendAPISpec(backendApiID);
    }

    /**
     * Downloads the frontend API specification.
     * Axway API Manager 2025 may return Swagger 2 or OpenAPI 3 automatically.
     */
    private String getFrontendAPISpec(String apiId) throws Exception {
        CloseableHttpResponse httpResponse = null;

        try {
            Utils.traceMessage("Downloading frontend API specification", TraceLevel.DEBUG);

            httpResponse = apiManager.get("/discovery/swagger/api/id/" + apiId);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String response = EntityUtils.toString(httpResponse.getEntity());

            if (statusCode != 200) {
                Utils.traceMessage(
                    "Error getting frontend API specification. Status-Code: "
                        + statusCode + ", Response: '" + response + "'",
                    TraceLevel.ERROR
                );
                throw new IllegalArgumentException("Error getting frontend API specification from API Manager.");
            }

            logDetectedSpecVersion(response);
            return response;

        } finally {
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    /**
     * Downloads the originally imported backend API specification.
     */
    private String getBackendAPISpec(String backendApiId) throws Exception {
        CloseableHttpResponse httpResponse = null;

        try {
            Utils.traceMessage("Downloading backend API specification", TraceLevel.DEBUG);

            httpResponse = apiManager.get("/apirepo/" + backendApiId + "/download?original=true");

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String response = EntityUtils.toString(httpResponse.getEntity());

            if (statusCode != 200) {
                Utils.traceMessage(
                    "Error getting backend API specification. Status-Code: "
                        + statusCode + ", Response: '" + response + "'",
                    TraceLevel.ERROR
                );
                throw new IllegalArgumentException("Error getting backend API specification from API Manager.");
            }

            logDetectedSpecVersion(response);
            return response;

        } finally {
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    /**
     * Resolves backend API-ID from frontend API-ID.
     */
    private String getBackendAPIID(String apiId) throws Exception {
        CloseableHttpResponse httpResponse = null;

        try {
            Utils.traceMessage("Resolving backend API-ID for frontend API-ID: " + apiId, TraceLevel.DEBUG);

            httpResponse = apiManager.get("/proxies/" + apiId);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String response = EntityUtils.toString(httpResponse.getEntity());

            if (statusCode != 200) {
                if (statusCode == 403 && response != null && response.contains("Forbidden")) {
                    Utils.traceMessage(
                        "Please check if the given API-ID is correct. API Manager returned Forbidden.",
                        TraceLevel.INFO
                    );
                }

                Utils.traceMessage(
                    "Error loading backend API-ID. Status-Code: "
                        + statusCode + ", Response: '" + response + "'",
                    TraceLevel.ERROR
                );
                throw new IllegalArgumentException("Error loading backend API-ID for API: " + apiId);
            }

            JsonNode node = mapper.readTree(response);
            return node.get("apiId").asText();

        } finally {
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    /**
     * Detects and logs whether the specification is Swagger 2 or OpenAPI 3.
     * This does NOT affect behavior yet — it is informational for now.
     */
    private void logDetectedSpecVersion(String specContent) {
        try {
            JsonNode root = mapper.readTree(specContent);

            if (root.has("openapi")) {
                Utils.traceMessage("Detected OpenAPI 3 specification", TraceLevel.INFO);
            } else if (root.has("swagger")) {
                Utils.traceMessage("Detected Swagger 2 specification", TraceLevel.INFO);
            } else {
                Utils.traceMessage("Unknown API specification format", TraceLevel.ERROR);
            }

        } catch (Exception e) {
            Utils.traceMessage(
                "Unable to detect API specification version: " + e.getMessage(),
                TraceLevel.ERROR
            );
        }
    }

    public boolean isUseOriginalAPISpec() {
        return useOriginalAPISpec;
    }

    public void setUseOriginalAPISpec(boolean useOriginalAPISpec) {
        this.useOriginalAPISpec = useOriginalAPISpec;
    }
}
