package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.HttpProxyHandler;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
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
            testEnforcedPeerGuard();
            testDownload(context, false);
            testDownload(context, true);
            testSocksDownloads(context);
        } finally {
            SSLContext.setDefault(previousContext);
        }
        System.out.println("HTTPS and SOCKS5 proxy download regressions passed");
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
            config.enabled = true;
            config.enforceProxy = true;
            var route = ProxyRoute.select(config, "game.invalid", 25566, false);
            var group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            try {
                var bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                        .handler(new ChannelInboundHandlerAdapter());
                var transport = ProxyConnection.connectThroughRoute(bootstrap,
                        InetSocketAddress.createUnresolved("game.invalid", 25566), route);
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

    private static void testEnforcedPeerGuard() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (var target = new ServerSocket(0, 1, loopback); var proxy = new ServerSocket(0, 1, loopback)) {
            target.setSoTimeout(250);
            proxy.setSoTimeout(250);
            var config = new ProxyConfig();
            config.enabled = true;
            config.enforceProxy = true;
            config.type = ProxyConfig.ProxyType.HTTP;
            config.host = loopback.getHostAddress();
            config.port = proxy.getLocalPort();
            var destination = new InetSocketAddress(loopback, target.getLocalPort());
            var route = ProxyRoute.select(config, config.host, destination.getPort(), false);

            var group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            try {
                var bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel channel) {
                                channel.pipeline().addLast(new HttpProxyHandler(destination));
                            }
                        });
                var transport = ProxyConnection.connectThroughRoute(bootstrap, destination, route);
                require(transport.await(6, TimeUnit.SECONDS) && !transport.isSuccess(),
                        "Conflicting proxy handlers must fail before TCP connect");
                require(transport.cause().getMessage().contains("Cannot enforce"), "The required peer guard must reject the conflict");
                requireNoConnection(target);
                requireNoConnection(proxy);

                proxy.close();
                var unavailable = new Bootstrap().group(group).channel(NioSocketChannel.class)
                        .handler(new ChannelInboundHandlerAdapter());
                var failed = ProxyConnection.awaitTunnel(ProxyConnection.connectThroughRoute(unavailable, destination, route),
                        ProxyConnection.BOOTSTRAP_PROXY_HANDLER);
                require(failed.await(6, TimeUnit.SECONDS) && !failed.isSuccess(),
                        "An unavailable configured proxy must fail the tunnel");
                requireNoConnection(target);
            } finally {
                group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            }
        }
    }

    private static void requireNoConnection(ServerSocket listener) throws IOException {
        try (var socket = listener.accept()) {
            throw new AssertionError("Unexpected TCP connection to " + listener.getLocalSocketAddress());
        } catch (SocketTimeoutException expected) {
            // The listener saw no connection.
        }
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
                var connection = NetworkProxyClient.openProxiedDownload(URI.create(url).toURL());
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

    private static void testSocksDownloads(SSLContext context) throws Exception {
        var previousAuthenticator = Authenticator.getDefault();
        NetworkProxyClient.installDownloadAuthenticator();
        try (var proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            proxy.setSoTimeout(6000);
            var config = new ProxyConfig();
            config.enabled = true;
            config.type = ProxyConfig.ProxyType.SOCKS5;
            config.host = proxy.getInetAddress().getHostAddress();
            config.port = proxy.getLocalPort();
            config.username = USERNAME;
            config.password = PASSWORD;
            var exchange = executor.submit(() -> {
                try (var socket = proxy.accept()) {
                    socksConnect(socket, "first.invalid", 80, 0);
                    requireOriginRequest(readRequest(socket), "/pack");
                    send(socket, "HTTP/1.1 302 Found\r\nLocation: http://redirect.invalid:18080/final\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n");
                }
                try (var socket = proxy.accept()) {
                    socksConnect(socket, "redirect.invalid", 18080, 0);
                    requireOriginRequest(readRequest(socket), "/final");
                    sendPack(socket);
                }
                try (var socket = proxy.accept()) {
                    socksConnect(socket, "origin.invalid", 443, 0);
                    try (var origin = (SSLSocket) context.getSocketFactory().createSocket(socket, "origin.invalid", 443, false)) {
                        origin.setUseClientMode(false);
                        origin.startHandshake();
                        requireOriginRequest(readRequest(origin), "/pack");
                        sendPack(origin);
                    }
                }
                try (var socket = proxy.accept()) {
                    socksConnect(socket, "refused.invalid", 80, 4);
                }
                return null;
            });
            require(downloadSocksPack(config, "http://first.invalid/pack").equals("pack"),
                    "An HTTP pack redirect must preserve proxy-side DNS");
            require(downloadSocksPack(config, "https://origin.invalid/pack").equals("pack"),
                    "An HTTPS pack must use origin TLS through the SOCKS tunnel");
            try {
                downloadSocksPack(config, "http://refused.invalid/pack");
                throw new AssertionError("A pack must fail when the SOCKS proxy rejects the destination");
            } catch (UncheckedIOException expected) {
                require(expected.getCause() instanceof SocketException,
                        "The download must report the SOCKS failure: " + expected.getCause());
            }
            exchange.get(8, TimeUnit.SECONDS);

            try (var direct = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                proxy.close();
                direct.setSoTimeout(250);
                try {
                    downloadSocksPack(config, "http://localhost:" + direct.getLocalPort() + "/pack");
                    throw new AssertionError("A pack must fail when its SOCKS proxy is unavailable");
                } catch (UncheckedIOException expected) {
                    require(expected.getCause() instanceof SocketException,
                            "The download must report the unavailable proxy: " + expected.getCause());
                }
                requireNoConnection(direct);
            }
        } finally {
            Authenticator.setDefault(previousAuthenticator);
        }
    }

    private static String downloadSocksPack(ProxyConfig config, String url) {
        return NetworkProxyClient.withDownloadProxy(config, Proxy.NO_PROXY, proxy -> {
            try {
                var connection = (java.net.HttpURLConnection) NetworkProxyClient.openProxiedDownload(URI.create(url).toURL());
                require(connection != null, "SOCKS5 with remote DNS must use the proxied download connection");
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Proxy-Authorization", "must-not-reach-the-origin");
                try (var content = connection.getInputStream()) {
                    return new String(content.readAllBytes(), StandardCharsets.US_ASCII);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void socksConnect(Socket socket, String host, int port, int status) throws IOException {
        socket.setSoTimeout(6000);
        var input = new DataInputStream(socket.getInputStream());
        var output = socket.getOutputStream();
        require(input.readUnsignedByte() == 5, "Expected SOCKS5 negotiation");
        var methods = input.readNBytes(input.readUnsignedByte());
        require(Arrays.equals(methods, new byte[] {0, 2}), "Expected password authentication support");
        output.write(new byte[] {5, 2});
        require(input.readUnsignedByte() == 1, "Expected SOCKS5 password authentication");
        require(readSocksString(input).equals(USERNAME), "The SOCKS proxy should get its configured username");
        require(readSocksString(input).equals(PASSWORD), "The SOCKS proxy should get its configured password");
        output.write(new byte[] {1, 0});
        require(Arrays.equals(input.readNBytes(4), new byte[] {5, 1, 0, 3}), "Expected a SOCKS5 domain CONNECT");
        require(readSocksString(input).equals(host) && input.readUnsignedShort() == port,
                "SOCKS5 must receive the original pack hostname and port");
        output.write(new byte[] {5, (byte) status, 0, 1, 127, 0, 0, 1, 0, 0});
    }

    private static String readSocksString(DataInputStream input) throws IOException {
        return new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.US_ASCII);
    }

    private static void requireOriginRequest(Request request, String path) {
        require(request.line().equals("GET " + path + " HTTP/1.1"), "Unexpected resource-pack request");
        require(!request.headers().containsKey("Authorization") && !request.headers().containsKey("Proxy-Authorization"),
                "The origin must not receive SOCKS5 credentials or a proxy authorization header");
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

    private static Request readRequest(Socket socket) throws IOException {
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

    private static void sendPack(Socket socket) throws IOException {
        send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\npack");
    }

    private static void send(Socket socket, String response) throws IOException {
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
