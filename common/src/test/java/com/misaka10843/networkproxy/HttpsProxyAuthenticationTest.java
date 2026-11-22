package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class HttpsProxyAuthenticationTest {
    private static final String USERNAME = "fixture-user";
    private static final String PASSWORD = "fixture-password";
    private static final String EXPECTED_AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.US_ASCII));

    private HttpsProxyAuthenticationTest() {
    }

    public static void main(String[] args) throws Exception {
        var previousContext = SSLContext.getDefault();
        var context = createTlsContext();
        SSLContext.setDefault(context);
        try {
            testMultiplayerConnect(context);
            testDownload(context, false);
            testDownload(context, true);
        } finally {
            SSLContext.setDefault(previousContext);
        }
        System.out.println("HTTPS proxy authentication regression test passed");
    }

    private static void testMultiplayerConnect(SSLContext context) throws Exception {
        withProxy(context, proxy -> {
            var request = readRequest(proxy);
            require(request.line().equals("CONNECT game.invalid:25566 HTTP/1.1"), "Unexpected multiplayer CONNECT");
            requireProxyAuthorization(request);
            send(proxy, "HTTP/1.1 200 Connection Established\r\n\r\n");
            require(proxy.getInputStream().read() == 42, "Application byte must travel inside the TLS tunnel");
        }, config -> {
            config.resolveHostnamesThroughProxy = true;
            var group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            try {
                var bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                        .handler(new ChannelInboundHandlerAdapter());
                var transport = ProxyConnection.connectThroughProxy(bootstrap,
                        InetSocketAddress.createUnresolved("game.invalid", 25566), config);
                var tunnel = ProxyConnection.awaitTunnel(transport, ProxyConnection.BOOTSTRAP_PROXY_HANDLER);
                require(tunnel.await(6, TimeUnit.SECONDS), "The multiplayer tunnel timed out");
                if (!tunnel.isSuccess()) {
                    throw new AssertionError("The HTTPS proxy rejected multiplayer CONNECT", tunnel.cause());
                }
                try {
                    var buffer = tunnel.channel().alloc().buffer(1).writeByte(42);
                    tunnel.channel().writeAndFlush(buffer).get(6, TimeUnit.SECONDS);
                } finally {
                    tunnel.channel().close().syncUninterruptibly();
                }
            } finally {
                group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            }
        });
    }

    private static void testDownload(SSLContext context, boolean tlsOrigin) throws Exception {
        String url = tlsOrigin ? "https://origin.invalid:24451/pack" : "http://origin.invalid/pack";
        String requestLine = tlsOrigin ? "CONNECT origin.invalid:24451 HTTP/1.1" : "GET " + url + " HTTP/1.1";
        withProxy(context, proxy -> {
            var initial = readRequest(proxy);
            require(initial.line().equals(requestLine), "Unexpected initial resource-pack request");
            require(!initial.headers().containsKey("Proxy-Authorization"), "Expected an authentication challenge first");
            send(proxy, "HTTP/1.1 407 Proxy Authentication Required\r\n"
                    + "Proxy-Authenticate: Basic realm=\"test-proxy\"\r\nContent-Length: 0\r\n\r\n");

            var authenticated = readRequest(proxy);
            require(authenticated.line().equals(requestLine), "Unexpected authenticated resource-pack request");
            requireProxyAuthorization(authenticated);
            if (tlsOrigin) {
                send(proxy, "HTTP/1.1 200 Connection Established\r\n\r\n");
                try (var origin = (SSLSocket) context.getSocketFactory()
                        .createSocket(proxy, "origin.invalid", 24451, false)) {
                    origin.setUseClientMode(false);
                    origin.startHandshake();
                    var originRequest = readRequest(origin);
                    require(originRequest.line().equals("GET /pack HTTP/1.1"), "Unexpected request in origin TLS session");
                    require(!originRequest.headers().containsKey("Proxy-Authorization")
                                    && !originRequest.headers().containsKey("Authorization"),
                            "Proxy credentials must not reach the resource-pack origin");
                    sendPack(origin);
                }
            } else {
                sendPack(proxy);
            }
        }, config -> NetworkProxyClient.withDownloadProxy(config, Proxy.NO_PROXY, selected -> {
            try {
                var connection = NetworkProxyClient.openDownloadThroughHttpsProxy(URI.create(url).toURL());
                require(connection != null, "HTTPS mode should select the secure download connection");
                try (var content = connection.getInputStream()) {
                    require(new String(content.readAllBytes(), StandardCharsets.US_ASCII).equals("pack"),
                            "Authenticated resource-pack download did not return the expected body");
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    private static void withProxy(SSLContext context, ProxyScript script, ClientScript client) throws Exception {
        InetAddress loopback = InetAddress.getByName("localhost");
        require(loopback.isLoopbackAddress(), "The test proxy must bind a loopback address");
        try (var server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 1, loopback);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setSoTimeout(6000);
            var config = new ProxyConfig();
            config.type = ProxyConfig.ProxyType.HTTPS;
            config.enabled = true;
            config.useForDownloads = true;
            config.host = "localhost";
            config.port = server.getLocalPort();
            config.username = USERNAME;
            config.password = PASSWORD;
            var exchange = executor.submit(() -> {
                try (var proxy = (SSLSocket) server.accept()) {
                    proxy.setSoTimeout(6000);
                    proxy.startHandshake();
                    script.run(proxy);
                }
                return null;
            });
            client.run(config);
            exchange.get(8, TimeUnit.SECONDS);
        }
    }

    private static SSLContext createTlsContext() throws Exception {
        Path directory = Files.createTempDirectory("networkproxy-tls-test");
        Path file = directory.resolve("fixture.p12");
        String password = "fixture-keystore";
        try {
            String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                    "-genkeypair", "-alias", "fixture", "-dname", "CN=localhost",
                    "-ext", "SAN=dns:localhost,dns:origin.invalid", "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "2", "-storetype", "PKCS12", "-keystore", file.toString(), "-storepass", password)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("keytool timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("keytool failed: " + new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }

            var keys = KeyStore.getInstance(file.toFile(), password.toCharArray());
            var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keys, password.toCharArray());
            var trust = KeyStore.getInstance("PKCS12");
            trust.load(null, null);
            trust.setCertificateEntry("fixture", keys.getCertificate("fixture"));
            var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trust);
            var context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
            return context;
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static Request readRequest(SSLSocket socket) throws IOException {
        var input = new DataInputStream(socket.getInputStream());
        var lines = new StringBuilder();
        while (!lines.toString().endsWith("\r\n\r\n")) {
            if (lines.length() == 8192) {
                throw new IOException("Fixture received excessive request headers");
            }
            lines.append((char) input.readUnsignedByte());
        }
        String[] parts = lines.toString().split("\r\n");
        var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 1; i < parts.length; i++) {
            int separator = parts[i].indexOf(':');
            require(separator > 0, "Malformed request header");
            headers.put(parts[i].substring(0, separator), parts[i].substring(separator + 1).strip());
        }
        return new Request(parts[0], headers);
    }

    private static void requireProxyAuthorization(Request request) {
        require(EXPECTED_AUTHORIZATION.equals(request.headers().get("Proxy-Authorization")),
                "The configured credentials should be delivered to the proxy inside TLS");
        require(!request.headers().containsKey("Authorization"), "Proxy credentials must not be sent as origin authorization");
    }

    private static void sendPack(SSLSocket socket) throws IOException {
        send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\npack");
    }

    private static void send(SSLSocket socket, String response) throws IOException {
        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Request(String line, Map<String, String> headers) {
    }

    @FunctionalInterface
    private interface ProxyScript {
        void run(SSLSocket socket) throws IOException;
    }

    @FunctionalInterface
    private interface ClientScript {
        void run(ProxyConfig config) throws Exception;
    }
}
