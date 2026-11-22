package com.misaka10843.networkproxy;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Supplies the response stream while Minecraft handles pack size limits, hashing and caching. */
final class HttpsProxyDownloadConnection extends HttpURLConnection {
    private static final Timeout IO_TIMEOUT = Timeout.ofSeconds(30);
    private final HttpHost proxy;
    private final PasswordAuthentication credentials;
    private CloseableHttpClient client;
    private ClassicHttpResponse response;
    private InputStream content;

    HttpsProxyDownloadConnection(URL url, InetSocketAddress endpoint, PasswordAuthentication credentials) {
        super(url);
        this.proxy = new HttpHost("https", endpoint.getAddress(), endpoint.getHostString(), endpoint.getPort());
        this.credentials = credentials;
    }

    @Override
    public void connect() throws IOException {
        if (connected) {
            return;
        }
        if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
            throw new IOException("Resource pack URL must use HTTP or HTTPS");
        }

        try {
            var request = new HttpGet(url.toURI());
            getRequestProperties().forEach((name, values) -> {
                if (!"Proxy-Authorization".equalsIgnoreCase(name)) {
                    values.forEach(value -> request.addHeader(name, value));
                }
            });
            client = newClient();
            response = client.executeOpen(null, request, null);
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Resource pack download returned HTTP " + status);
            }
            var entity = response.getEntity();
            content = new FilterInputStream(entity == null ? InputStream.nullInputStream() : entity.getContent()) {
                @Override
                public void close() {
                    disconnect();
                }
            };
            connected = true;
        } catch (URISyntaxException | NoSuchAlgorithmException e) {
            disconnect();
            throw new IOException("Cannot initialize resource-pack download over HTTPS proxy", e);
        } catch (IOException | RuntimeException e) {
            disconnect();
            throw e;
        }
    }

    private CloseableHttpClient newClient() throws NoSuchAlgorithmException {
        var tls = ClientTlsStrategyBuilder.create().setSslContext(SSLContext.getDefault()).buildClassic();
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tls)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(IO_TIMEOUT).setSocketTimeout(IO_TIMEOUT).build())
                .build();
        var builder = HttpClients.custom()
                .setConnectionManager(manager)
                .setProxy(proxy)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(IO_TIMEOUT)
                        .setRedirectsEnabled(getInstanceFollowRedirects()).build())
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    public boolean isRedirectAllowed(HttpHost current, HttpHost next, HttpRequest request,
                                                     HttpContext context) {
                        // Match the JDK download path by keeping redirects on the same protocol.
                        return current.getSchemeName().equalsIgnoreCase(next.getSchemeName())
                                && super.isRedirectAllowed(current, next, request, context);
                    }
                })
                .disableAutomaticRetries()
                .disableContentCompression()
                .disableCookieManagement()
                .setTargetAuthenticationStrategy((type, challenges, context) -> List.of());
        if (credentials != null) {
            var provider = new BasicCredentialsProvider();
            provider.setCredentials(new AuthScope(proxy),
                    new UsernamePasswordCredentials(credentials.getUserName(), credentials.getPassword()));
            builder.setDefaultCredentialsProvider(provider);
        }
        return builder.build();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return content;
    }

    @Override
    public long getContentLengthLong() {
        return response == null || response.getEntity() == null ? -1 : response.getEntity().getContentLength();
    }

    @Override
    public void disconnect() {
        // Close the transport first when Minecraft stops reading an oversized pack.
        if (client != null) {
            client.close(CloseMode.IMMEDIATE);
            client = null;
        }
        if (response != null) {
            try {
                response.close();
            } catch (IOException ignored) {
                // The connection manager has already closed the socket.
            }
            response = null;
        }
        content = null;
    }

    @Override
    public boolean usingProxy() {
        return true;
    }
}
