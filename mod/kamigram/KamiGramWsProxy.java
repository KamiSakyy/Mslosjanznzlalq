package org.telegram.messenger.kamigram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

import javax.net.ssl.SSLSocketFactory;

/**
 * Sakura: локальный прокси (KAMIGRAM_WS_PROXY_R130).
 *
 * Сервис держит на 127.0.0.1 локальный SOCKS5-приёмник. Telegram направляется
 * сюда одной настройкой прокси; каждый CONNECT обслуживается мостом: трафик
 * упаковывается в WebSocket-кадры и уходит на штатные WS-точки датацентров
 * Telegram (aps{dc}.telegram.org/ws — официальный MTProto-over-WS транспорт),
 * поэтому сырые MTProto-соединения, которые режут DPI, не используются.
 * Если WS-мост недоступен, соединение прозрачно падает в прямой TCP к цели —
 * связь не ломается никогда.
 *
 * Работает в фоне как foreground-сервис с постоянным уведомлением,
 * включается одним тумблером в Центре.
 */
public class KamiGramWsProxy extends Service {

    public static final int BASE_PORT = 3977;
    private static final long BRIDGE_TIMEOUT = 9000L;

    private static volatile ServerSocket server;
    private static volatile boolean running;
    private static volatile int boundPort;

    /* production DC ipv4 -> dc id (статические адреса Telegram) */
    private static int dcByHost(String host) {
        if (host == null) {
            return 0;
        }
        switch (host) {
            case "149.154.175.53": return 1;
            case "149.154.167.50": return 2;
            case "149.154.175.100": return 3;
            case "149.154.167.91": return 4;
            case "91.108.56.130": return 5;
            default:
                if (host.endsWith(".telegram.org")) {
                    for (int dc = 1; dc <= 5; dc++) {
                        if (host.startsWith("aps" + dc + ".") || host.contains("dc" + dc)) {
                            return dc;
                        }
                    }
                    return 2;
                }
                return 0;
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static int port() {
        return boundPort;
    }

    public static void ensureStarted(Context context) {
        try {
            if (running) {
                return;
            }
            final Intent intent = new Intent(context, KamiGramWsProxy.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, KamiGramWsProxy.class));
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                final NotificationChannel channel = new NotificationChannel(
                    "sakura_ws_proxy", "Локальный прокси", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            final Notification notification =
                (Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "sakura_ws_proxy")
                    : new Notification.Builder(this))
                    .setContentTitle("Sakura")
                    .setContentText("Локальный прокси активен")
                    .setSmallIcon(getApplicationInfo().icon)
                    .setOngoing(true)
                    .build();
            startForeground(39770, notification);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        startServer();
        return START_STICKY;
    }

    private void startServer() {
        if (running) {
            return;
        }
        final Thread thread = new Thread(() -> {
            ServerSocket local = null;
            for (int port = BASE_PORT; port < BASE_PORT + 6 && local == null; port++) {
                try {
                    local = new ServerSocket();
                    local.setReuseAddress(true);
                    local.bind(new InetSocketAddress("127.0.0.1", port));
                    boundPort = port;
                } catch (IOException ignore) {
                    try {
                        local.close();
                    } catch (Throwable ignore2) {
                    }
                    local = null;
                }
            }
            if (local == null) {
                stopSelf();
                return;
            }
            server = local;
            running = true;
            while (running) {
                try {
                    final Socket client = local.accept();
                    new Thread(() -> handle(client), "sakura-ws-conn").start();
                } catch (Throwable ignore) {
                    break;
                }
            }
        }, "sakura-ws-server");
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.start();
    }

    private void handle(Socket client) {
        Socket target = null;
        try {
            client.setTcpNoDelay(true);
            final InputStream in = client.getInputStream();
            final OutputStream out = client.getOutputStream();
            if (in.read() != 5) {
                return;
            }
            final int n = in.read();
            if (n < 0) {
                return;
            }
            final byte[] methods = new byte[n];
            readFully(in, methods);
            out.write(new byte[]{5, 0});
            final byte[] req = new byte[4];
            readFully(in, req);
            if (req[1] != 1) {
                out.write(new byte[]{5, 7, 0, 1, 0, 0, 0, 0, 0, 0});
                return;
            }
            String host;
            if (req[3] == 1) {
                final byte[] ip = new byte[4];
                readFully(in, ip);
                host = (ip[0] & 255) + "." + (ip[1] & 255) + "." + (ip[2] & 255) + "." + (ip[3] & 255);
            } else if (req[3] == 3) {
                final byte[] len = new byte[1];
                readFully(in, len);
                final byte[] dom = new byte[len[0] & 255];
                readFully(in, dom);
                host = new String(dom, "UTF-8");
            } else {
                final byte[] ip = new byte[16];
                readFully(in, ip);
                host = "ipv6";
            }
            final byte[] portBytes = new byte[2];
            readFully(in, portBytes);
            final int port = ((portBytes[0] & 255) << 8) | (portBytes[1] & 255);
            out.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0});

            final int dc = dcByHost(host);
            target = openBridge(dc, host, port);
            final Socket finalTarget = target;
            final Thread up = new Thread(() -> pipeTcpToWs(client, finalTarget), "sakura-ws-up");
            up.start();
            pipeWsToTcp(finalTarget, client);
            up.interrupt();
        } catch (Throwable ignore) {
        } finally {
            close(client);
            close(target);
        }
    }

    /** WS-мост к DC; при любой ошибке — прямой TCP к цели (связь не рвётся). */
    private Socket openBridge(int dc, String host, int port) throws IOException {
        if (dc > 0) {
            try {
                return WsBridge.connect(dc);
            } catch (Throwable ignore) {
            }
        }
        final Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) BRIDGE_TIMEOUT);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private void pipeTcpToWs(Socket client, Socket target) {
        try {
            final InputStream in = client.getInputStream();
            final byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                WsBridge.send(target, buffer, read);
            }
        } catch (Throwable ignore) {
        }
    }

    private void pipeWsToTcp(Socket target, Socket client) {
        try {
            final OutputStream out = client.getOutputStream();
            final byte[] buffer = new byte[16384];
            int read;
            if (target instanceof WsBridge) {
                final InputStream in = ((WsBridge) target).stream();
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } else {
                final InputStream in = target.getInputStream();
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            final int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                throw new IOException("eof");
            }
            offset += read;
        }
    }

    private static void close(java.io.Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Throwable ignore) {
        }
        server = null;
        super.onDestroy();
    }

    /**
     * WebSocket-сокет к штатной WS-точке DC (MTProto-over-WS). Обёрнут в
     * Socket-подобный класс с входным потоком собранных сообщений и
     * отправкой бинарных кадров с маской.
     */
    static final class WsBridge extends Socket {
        private final Socket ssl;
        private final InputStream pipeIn;
        private final OutputStream pipeOut;
        private final OutputStream wireOut;

        private WsBridge(Socket ssl, InputStream pipeIn, OutputStream pipeOut, OutputStream wireOut) {
            this.ssl = ssl;
            this.pipeIn = pipeIn;
            this.pipeOut = pipeOut;
            this.wireOut = wireOut;
        }

        static WsBridge connect(int dc) throws IOException {
            final String hostS = "aps" + dc + ".telegram.org";
            final Socket ssl = SSLSocketFactory.getDefault().createSocket();
            ssl.connect(new InetSocketAddress(hostS, 443), (int) BRIDGE_TIMEOUT);
            ssl.setTcpNoDelay(true);
            final OutputStream out = ssl.getOutputStream();
            final InputStream in = ssl.getInputStream();
            final byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            final String key = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP);
            final String handshake = "GET /ws HTTP/1.1\r\n"
                + "Host: " + hostS + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(handshake.getBytes("UTF-8"));
            out.flush();
            final StringBuilder response = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                response.append((char) ch);
                if (response.length() > 4 && response.substring(response.length() - 4).equals("\r\n\r\n")) {
                    break;
                }
            }
            if (!response.toString().startsWith("HTTP/1.1 101")) {
                ssl.close();
                throw new IOException("ws handshake failed");
            }
            final java.io.PipedInputStream pipeIn = new java.io.PipedInputStream(1 << 16);
            final OutputStream pipeOut = new java.io.PipedOutputStream(pipeIn);
            final Thread reader = new Thread(() -> readFrames(in, pipeOut), "sakura-ws-read");
            reader.setDaemon(true);
            reader.start();
            return new WsBridge(ssl, pipeIn, pipeOut, out);
        }

        private static void readFrames(InputStream in, OutputStream pipeOut) {
            try {
                while (true) {
                    final int b0 = in.read();
                    if (b0 < 0) {
                        return;
                    }
                    final int opcode = b0 & 0x0F;
                    int b1 = in.read();
                    if (b1 < 0) {
                        return;
                    }
                    long len = b1 & 0x7F;
                    if (len == 126) {
                        final byte[] ext = new byte[2];
                        readFully(in, ext);
                        len = ((ext[0] & 255) << 8) | (ext[1] & 255);
                    } else if (len == 127) {
                        final byte[] ext = new byte[8];
                        readFully(in, ext);
                        len = 0;
                        for (int a = 0; a < 8; a++) {
                            len = (len << 8) | (ext[a] & 255);
                        }
                    }
                    if (opcode == 8) {
                        return;
                    }
                    if (opcode == 9) {
                        continue;
                    }
                    final byte[] payload = new byte[(int) Math.min(len, 1 << 20)];
                    readFully(in, payload);
                    if (opcode == 1 || opcode == 2) {
                        pipeOut.write(payload);
                        pipeOut.flush();
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        static void send(Socket target, byte[] buffer, int length) throws IOException {
            if (!(target instanceof WsBridge)) {
                ((Socket) target).getOutputStream().write(buffer, 0, length);
                ((Socket) target).getOutputStream().flush();
                return;
            }
            final WsBridge bridge = (WsBridge) target;
            synchronized (bridge.wireOut) {
                final OutputStream out = bridge.wireOut;
                final byte[] mask = new byte[4];
                new SecureRandom().nextBytes(mask);
                out.write(0x82);
                if (length < 126) {
                    out.write(0x80 | length);
                } else if (length < 65536) {
                    out.write(0x80 | 126);
                    out.write(new byte[]{(byte) (length >> 8), (byte) length});
                } else {
                    out.write(0x80 | 127);
                    final byte[] ext = new byte[8];
                    long rest = length;
                    for (int a = 7; a >= 0; a--) {
                        ext[a] = (byte) (rest & 255);
                        rest >>= 8;
                    }
                    out.write(ext);
                }
                out.write(mask);
                for (int a = 0; a < length; a++) {
                    out.write(buffer[a] ^ mask[a & 3]);
                }
                out.flush();
            }
        }

        InputStream stream() {
            return pipeIn;
        }

        @Override
        public InputStream getInputStream() {
            return pipeIn;
        }

        @Override
        public OutputStream getOutputStream() {
            return pipeOut;
        }

        @Override
        public void close() throws IOException {
            try {
                pipeIn.close();
            } catch (Throwable ignore) {
            }
            try {
                ssl.close();
            } catch (Throwable ignore) {
            }
            super.close();
        }
    }
}
