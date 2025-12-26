package com.axway.apim.openapi.validator;

import java.net.URI;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

/**
 * HTTP Client for communicating with Axway API Manager.
 *
 * NOTE: SSL validation is disabled intentionally because
 * API Gateway environments often use internal/self-signed certificates.
 */
public class APIManagerHttpClient {

    private static final String DEFAULT_API_VERSION = "/api/portal/v1.4";

    private final URI apiManagerUrl;
    private final String username;
    private final String password;

    private CloseableHttpClient httpClient;
    private HttpClientContext clientContext;

    public APIManagerHttpClient(String apiManagerUrl, String username, String password) throws Exception {
        this.apiManagerUrl = new URI(apiManagerUrl);
        this.username = username;
        this.password = password;
        initClient();
    }

    private void initClient() throws Exception {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
        );

        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(
                new HttpHost(
                        apiManagerUrl.getHost(),
                        apiManagerUrl.getPort(),
                        apiManagerUrl.getScheme()
                ),
                basicAuth
        );

        clientContext = HttpClientContext.create();
        clientContext.setAuthCache(authCache);

        SSLContextBuilder sslContextBuilder = SSLContextBuilder.create()
                .loadTrustMaterial(null, new TrustAllStrategy());

        SSLConnectionSocketFactory sslSocketFactory =
                new SSLConnectionSocketFactory(
                        sslContextBuilder.build(),
                        NoopHostnameVerifier.INSTANCE
                );

        this.httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setSSLSocketFactory(sslSocketFactory)
                .build();
    }

    public CloseableHttpResponse get(String requestPath) throws Exception {
        URI uri = new URIBuilder(apiManagerUrl + DEFAULT_API_VERSION + requestPath).build();
        HttpGet httpGet = new HttpGet(uri);
        return httpClient.execute(httpGet, clientContext);
    }
}
