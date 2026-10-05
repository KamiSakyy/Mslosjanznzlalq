#include "obf.h"
#include "tsproxy.h"

#include "crypto.h"

#include <mbedtls/ctr_drbg.h>
#include <mbedtls/entropy.h>
#include <mbedtls/ssl.h>

#include <arpa/inet.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <deque>
#include <fcntl.h>
#include <map>
#include <mutex>
#include <poll.h>
#include <random>
#include <set>
#include <signal.h>
#include <sys/resource.h>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {

const int kConnectMs = 1500;
const int kHandshakeMs = 1800;
const int kFirstBudgetMs = 3200;
const int kSessionBudgetMs = 12000;
// Fast in-band failure detection for a pending Telegram exchange; small one-way frames
// are filtered below so routine acknowledgements do not reset an otherwise healthy session.
const int64_t kTunnelResponseStallMs = 3000;
const unsigned kTunnelStallMinFrames = 2;
const size_t kTunnelStallMinBytes = 512;
// Свежим считается фронт, который ответил меньше тридцати секунд назад (реальный
// обмен в туннеле или ручная проверка).
const int64_t kAliveFreshMs = 30000;
const int kProbeBudgetMs = 2500;

std::vector<std::string> encoded_domains() {
    return {
            XS("virkgj.com"),
            XS("vmmzovy.com"),
            XS("mkuosckvso.com"),
            XS("zaewayzmplad.com"),
            XS("twdmbzcm.com"),
            XS("awzwsldi.com"),
            XS("clngqrflngqin.com"),
            XS("tjacxbqtj.com"),
            XS("bxaxtxmrw.com"),
            XS("dmohrsgmohcrwb.com"),
            XS("vwbmtmoi.com"),
            XS("khgrre.com"),
            XS("ulihssf.com"),
            XS("tmhqsdqmfpmk.com"),
            XS("xwuwoqbm.com"),
    };
}

std::vector<std::string> plain_domains() {
    return {XS("pnrhub.online")};
}

std::string dc_ip(int dc) {
    switch (dc) {
        case 1: return XS("149.154.175.50");
        case 2: return XS("149.154.167.51");
        case 3: return XS("149.154.175.100");
        case 4: return XS("149.154.167.91");
        case 5: return XS("149.154.171.5");
        case 203: return XS("91.105.192.100");
        default: return "";
    }
}

uint32_t le32_at(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) | (static_cast<uint32_t>(data[1]) << 8)
            | (static_cast<uint32_t>(data[2]) << 16) | (static_cast<uint32_t>(data[3]) << 24);
}

int64_t now_ms() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void set_nonblock(int fd, bool enabled) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return;
    }
    if (enabled) {
        flags |= O_NONBLOCK;
    } else {
        flags &= ~O_NONBLOCK;
    }
    fcntl(fd, F_SETFL, flags);
}

void set_timeout(int fd, int seconds) {
    timeval tv{};
    tv.tv_sec = seconds;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
}

void tune_socket(int fd) {
    int yes = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &yes, sizeof(yes));
    setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &yes, sizeof(yes));
#ifdef TCP_KEEPIDLE
    int idle = 30;
    setsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &idle, sizeof(idle));
    setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &idle, sizeof(idle));
    int count = 3;
    setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &count, sizeof(count));
#endif
    int buf = 128 * 1024;
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &buf, sizeof(buf));
    setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &buf, sizeof(buf));
}

bool send_all(int fd, const uint8_t* data, size_t len, int wait_ms) {
    size_t off = 0;
    while (off < len) {
        ssize_t n = ::send(fd, data + off, len - off, MSG_NOSIGNAL);
        if (n > 0) {
            off += static_cast<size_t>(n);
            continue;
        }
        if (n < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) {
            pollfd pfd{};
            pfd.fd = fd;
            pfd.events = POLLOUT;
            if (poll(&pfd, 1, wait_ms) <= 0) {
                return false;
            }
            continue;
        }
        return false;
    }
    return true;
}

bool recv_exact(int fd, uint8_t* data, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t n = ::recv(fd, data + off, len - off, 0);
        if (n == 0) {
            return false;
        }
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return false;
        }
        off += static_cast<size_t>(n);
    }
    return true;
}

int tcp_connect(const char* host, int port, int timeout_ms) {
    addrinfo hints{};
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_family = AF_UNSPEC;
    char port_text[8];
    std::snprintf(port_text, sizeof(port_text), "%d", port);
    addrinfo* result = nullptr;
    if (getaddrinfo(host, port_text, &hints, &result) != 0) {
        return -1;
    }
    std::vector<addrinfo*> addrs;
    for (addrinfo* item = result; item != nullptr; item = item->ai_next) {
        addrs.push_back(item);
    }
    std::stable_sort(addrs.begin(), addrs.end(), [](addrinfo* a, addrinfo* b) {
        return a->ai_family == AF_INET && b->ai_family != AF_INET;
    });
    int fd = -1;
    int each = timeout_ms;
    if (!addrs.empty() && addrs.size() > 1) {
        each = timeout_ms / static_cast<int>(addrs.size());
        if (each < 800) {
            each = 800;
        }
    }
    for (addrinfo* item : addrs) {
        int sock = socket(item->ai_family, SOCK_STREAM, 0);
        if (sock < 0) {
            continue;
        }
        set_nonblock(sock, true);
        int rc = ::connect(sock, item->ai_addr, item->ai_addrlen);
        if (rc != 0 && errno != EINPROGRESS) {
            ::close(sock);
            continue;
        }
        if (rc != 0) {
            pollfd pfd{};
            pfd.fd = sock;
            pfd.events = POLLOUT;
            int pr = poll(&pfd, 1, each);
            int err = 0;
            socklen_t err_len = sizeof(err);
            getsockopt(sock, SOL_SOCKET, SO_ERROR, &err, &err_len);
            if (pr <= 0 || err != 0) {
                ::close(sock);
                continue;
            }
        }
        set_nonblock(sock, false);
        tune_socket(sock);
        fd = sock;
        break;
    }
    freeaddrinfo(result);
    return fd;
}

int bio_send(void* ctx, const unsigned char* buf, size_t len) {
    int fd = *static_cast<int*>(ctx);
    ssize_t n = ::send(fd, buf, len, MSG_NOSIGNAL);
    if (n > 0) {
        return static_cast<int>(n);
    }
    if (n == 0) {
        return MBEDTLS_ERR_SSL_WANT_WRITE;
    }
    if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
        return MBEDTLS_ERR_SSL_WANT_WRITE;
    }
    return -1;
}

int bio_recv(void* ctx, unsigned char* buf, size_t len) {
    int fd = *static_cast<int*>(ctx);
    ssize_t n = ::recv(fd, buf, len, 0);
    if (n > 0) {
        return static_cast<int>(n);
    }
    if (n == 0) {
        return MBEDTLS_ERR_SSL_CONN_EOF;
    }
    if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
        return MBEDTLS_ERR_SSL_WANT_READ;
    }
    return -1;
}

struct TlsSocket {
    int fd = -1;
    mbedtls_ssl_context ssl;
    mbedtls_ssl_config conf;
    mbedtls_ctr_drbg_context drbg;
    mbedtls_entropy_context entropy;
    bool ready = false;

    TlsSocket() {
        mbedtls_ssl_init(&ssl);
        mbedtls_ssl_config_init(&conf);
        mbedtls_ctr_drbg_init(&drbg);
        mbedtls_entropy_init(&entropy);
        ready = true;
    }

    ~TlsSocket() { close_now(); }

    void close_now() {
        if (ready) {
            mbedtls_ssl_free(&ssl);
            mbedtls_ssl_config_free(&conf);
            mbedtls_ctr_drbg_free(&drbg);
            mbedtls_entropy_free(&entropy);
            ready = false;
        }
        if (fd >= 0) {
            ::close(fd);
            fd = -1;
        }
    }
};

bool tls_handshake(TlsSocket* tls, const char* sni, int timeout_ms) {
    // Персонализация DRBG берётся из обфусцированной строки: в библиотеке нет
    // ни одного читаемого слова, по которому её узнают сканеры.
    std::string pers = XS("tsuyu");
    if (mbedtls_ctr_drbg_seed(&tls->drbg, mbedtls_entropy_func, &tls->entropy,
                              reinterpret_cast<const unsigned char*>(pers.data()), pers.size()) != 0) {
        return false;
    }
    if (mbedtls_ssl_config_defaults(&tls->conf, MBEDTLS_SSL_IS_CLIENT, MBEDTLS_SSL_TRANSPORT_STREAM,
                                    MBEDTLS_SSL_PRESET_DEFAULT) != 0) {
        return false;
    }
    mbedtls_ssl_conf_authmode(&tls->conf, MBEDTLS_SSL_VERIFY_NONE);
    mbedtls_ssl_conf_rng(&tls->conf, mbedtls_ctr_drbg_random, &tls->drbg);
    if (mbedtls_ssl_setup(&tls->ssl, &tls->conf) != 0) {
        return false;
    }
    if (mbedtls_ssl_set_hostname(&tls->ssl, sni) != 0) {
        return false;
    }
    mbedtls_ssl_set_bio(&tls->ssl, &tls->fd, bio_send, bio_recv, nullptr);
    set_timeout(tls->fd, timeout_ms / 1000 > 0 ? timeout_ms / 1000 : 1);
    int64_t deadline = now_ms() + timeout_ms;
    while (true) {
        int ret = mbedtls_ssl_handshake(&tls->ssl);
        if (ret == 0) {
            return true;
        }
        if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE) {
            return false;
        }
        if (now_ms() > deadline) {
            return false;
        }
    }
}

bool ssl_write_all(mbedtls_ssl_context* ssl, const uint8_t* data, size_t len, int64_t deadline) {
    size_t off = 0;
    while (off < len) {
        if (now_ms() > deadline) {
            return false;
        }
        int ret = mbedtls_ssl_write(ssl, data + off, len - off);
        if (ret > 0) {
            off += static_cast<size_t>(ret);
            continue;
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            continue;
        }
        return false;
    }
    return true;
}

bool ssl_read_some(mbedtls_ssl_context* ssl, std::vector<uint8_t>* out, int64_t deadline) {
    uint8_t buf[4096];
    while (now_ms() <= deadline) {
        int ret = mbedtls_ssl_read(ssl, buf, sizeof(buf));
        if (ret > 0) {
            out->insert(out->end(), buf, buf + ret);
            return true;
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            continue;
        }
        return false;
    }
    return false;
}

struct Link {
    std::unique_ptr<TlsSocket> tls;
    std::vector<uint8_t> rx;
    size_t rx_pos = 0;
    bool dead = false;

    int fd() const { return tls ? tls->fd : -1; }

    void compact() {
        if (rx_pos == 0) {
            return;
        }
        if (rx_pos >= rx.size()) {
            rx.clear();
            rx_pos = 0;
            return;
        }
        if (rx_pos > 8192) {
            rx.erase(rx.begin(), rx.begin() + static_cast<std::ptrdiff_t>(rx_pos));
            rx_pos = 0;
        }
    }

    bool write_frame(uint8_t opcode, const uint8_t* data, size_t len) {
        if (!tls || dead) {
            return false;
        }
        std::vector<uint8_t> frame;
        frame.reserve(len + 14);
        frame.push_back(static_cast<uint8_t>(0x80 | opcode));
        uint8_t mask[4];
        random_bytes(mask, 4);
        if (len < 126) {
            frame.push_back(static_cast<uint8_t>(0x80 | len));
        } else if (len <= 0xFFFF) {
            frame.push_back(0x80 | 126);
            frame.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
            frame.push_back(static_cast<uint8_t>(len & 0xFF));
        } else {
            frame.push_back(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.push_back(static_cast<uint8_t>((len >> shift) & 0xFF));
            }
        }
        frame.insert(frame.end(), mask, mask + 4);
        size_t start = frame.size();
        frame.insert(frame.end(), data, data + len);
        for (size_t i = 0; i < len; ++i) {
            frame[start + i] ^= mask[i & 3];
        }
        size_t off = 0;
        int spins = 0;
        while (off < frame.size()) {
            int ret = mbedtls_ssl_write(&tls->ssl, frame.data() + off, frame.size() - off);
            if (ret > 0) {
                off += static_cast<size_t>(ret);
                spins = 0;
                continue;
            }
            if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
                if (++spins > 40) {
                    dead = true;
                    return false;
                }
                pollfd pfd{};
                pfd.fd = tls->fd;
                pfd.events = POLLIN | POLLOUT;
                if (poll(&pfd, 1, 5000) <= 0) {
                    dead = true;
                    return false;
                }
                continue;
            }
            dead = true;
            return false;
        }
        return true;
    }

    bool pump() {
        if (!tls || dead) {
            return false;
        }
        uint8_t buf[8192];
        int ret = mbedtls_ssl_read(&tls->ssl, buf, sizeof(buf));
        if (ret > 0) {
            rx.insert(rx.end(), buf, buf + ret);
            return true;
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            return false;
        }
        dead = true;
        return false;
    }

    bool take(size_t n, uint8_t* dst) {
        if (rx.size() - rx_pos < n) {
            return false;
        }
        std::memcpy(dst, rx.data() + rx_pos, n);
        rx_pos += n;
        compact();
        return true;
    }

    bool next_data(std::vector<uint8_t>* out) {
        std::vector<uint8_t> frag;
        int frag_op = -1;
        // No "!dead" guard here: a peer may send its last payload and close in the same
        // poll round, that payload still has to be delivered before the tunnel is dropped.
        while (true) {
            if (rx.size() - rx_pos < 2) {
                return false;
            }
            size_t mark = rx_pos;
            uint8_t hdr[2];
            if (!take(2, hdr)) {
                rx_pos = mark;
                return false;
            }
            uint8_t opcode = hdr[0] & 0x0F;
            bool fin = (hdr[0] & 0x80) != 0;
            uint64_t length = hdr[1] & 0x7F;
            bool masked = (hdr[1] & 0x80) != 0;
            if (length == 126) {
                uint8_t ext[2];
                if (!take(2, ext)) {
                    rx_pos = mark;
                    return false;
                }
                length = ((uint64_t)ext[0] << 8) | ext[1];
            } else if (length == 127) {
                uint8_t ext[8];
                if (!take(8, ext)) {
                    rx_pos = mark;
                    return false;
                }
                length = 0;
                for (int i = 0; i < 8; ++i) {
                    length = (length << 8) | ext[i];
                }
            }
            uint8_t mask[4] = {};
            if (masked && !take(4, mask)) {
                rx_pos = mark;
                return false;
            }
            if (length > 8 * 1024 * 1024) {
                dead = true;
                return false;
            }
            if (rx.size() - rx_pos < length) {
                rx_pos = mark;
                return false;
            }
            std::vector<uint8_t> payload(static_cast<size_t>(length));
            if (length && !take(static_cast<size_t>(length), payload.data())) {
                rx_pos = mark;
                return false;
            }
            if (masked) {
                for (size_t i = 0; i < payload.size(); ++i) {
                    payload[i] ^= mask[i & 3];
                }
            }
            if (opcode == 0x8) {
                uint8_t close_payload[2] = {};
                size_t close_len = payload.size() >= 2 ? 2 : payload.size();
                write_frame(0x8, close_len ? payload.data() : close_payload, close_len);
                dead = true;
                return false;
            }
            if (opcode == 0x9) {
                write_frame(0xA, payload.data(), payload.size());
                continue;
            }
            if (opcode == 0xA) {
                continue;
            }
            if (opcode == 0x0) {
                frag.insert(frag.end(), payload.begin(), payload.end());
                if (fin) {
                    *out = std::move(frag);
                    return true;
                }
                continue;
            }
            if (opcode == 0x1 || opcode == 0x2) {
                if (!fin) {
                    frag = std::move(payload);
                    frag_op = opcode;
                    continue;
                }
                *out = std::move(payload);
                (void)frag_op;
                return true;
            }
        }
        return false;
    }
};

int read_http_status(const std::vector<uint8_t>& raw, size_t* header_end) {
    const char* marker = "\r\n\r\n";
    auto begin = raw.begin();
    auto found = std::search(begin, raw.end(), marker, marker + 4);
    if (found == raw.end()) {
        return -1;
    }
    *header_end = static_cast<size_t>(found - begin) + 4;
    std::string first;
    for (size_t i = 0; i < raw.size() && raw[i] != '\n' && i < 128; ++i) {
        if (raw[i] != '\r') {
            first.push_back(static_cast<char>(raw[i]));
        }
    }
    int status = 0;
    size_t space = first.find(' ');
    if (space != std::string::npos) {
        status = std::atoi(first.c_str() + space + 1);
    }
    return status;
}

std::unique_ptr<Link> upgrade_socket(int fd, const std::string& host, int timeout_ms, int* http_status) {
    if (http_status) {
        *http_status = 0;
    }
    auto tls = std::make_unique<TlsSocket>();
    tls->fd = fd;
    if (!tls_handshake(tls.get(), host.c_str(), timeout_ms)) {
        return nullptr;
    }
    auto link = std::make_unique<Link>();
    link->tls = std::move(tls);
    uint8_t key_raw[16];
    random_bytes(key_raw, sizeof(key_raw));
    std::string key = b64_encode(key_raw, sizeof(key_raw));
    std::string req = XS("GET /apiws HTTP/1.1\r\nHost: ") + host +
                      XS("\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ") + key +
                      XS("\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\n\r\n");
    int64_t deadline = now_ms() + timeout_ms;
    if (!ssl_write_all(&link->tls->ssl, reinterpret_cast<const uint8_t*>(req.data()), req.size(), deadline)) {
        return nullptr;
    }
    while (now_ms() <= deadline) {
        size_t header_end = 0;
        int status = read_http_status(link->rx, &header_end);
        if (status >= 0) {
            if (http_status) {
                *http_status = status;
            }
            if (status != 101) {
                return nullptr;
            }
            link->rx_pos = header_end;
            link->compact();
            set_timeout(link->fd(), 0);
            set_nonblock(link->fd(), true);
            return link;
        }
        if (!ssl_read_some(&link->tls->ssl, &link->rx, deadline)) {
            return nullptr;
        }
        if (link->rx.size() > 65536) {
            return nullptr;
        }
    }
    return nullptr;
}

bool is_ipv4(const std::string& text) {
    in_addr addr{};
    return inet_pton(AF_INET, text.c_str(), &addr) == 1;
}

std::string first_ipv4(const std::string& body) {
    size_t pos = 0;
    static const std::string kDataMark = XS("\"data\"");
    while ((pos = body.find(kDataMark, pos)) != std::string::npos) {
        size_t colon = body.find(':', pos + 6);
        if (colon == std::string::npos) {
            break;
        }
        size_t quote = body.find('"', colon + 1);
        if (quote == std::string::npos) {
            break;
        }
        size_t end = body.find('"', quote + 1);
        if (end == std::string::npos) {
            break;
        }
        std::string value = body.substr(quote + 1, end - quote - 1);
        if (is_ipv4(value)) {
            return value;
        }
        pos = end + 1;
    }
    return "";
}

std::string doh_lookup(const std::string& name) {
    std::string ips[] = {XS("1.1.1.1"), XS("8.8.8.8")};
    std::string snis[] = {XS("cloudflare-dns.com"), XS("dns.google")};
    for (int i = 0; i < 2; ++i) {
        int fd = tcp_connect(ips[i].c_str(), 443, 2000);
        if (fd < 0) {
            continue;
        }
        auto tls = std::make_unique<TlsSocket>();
        tls->fd = fd;
        if (!tls_handshake(tls.get(), snis[i].c_str(), 2500)) {
            continue;
        }
        std::string req = XS("GET /dns-query?name=") + name + XS("&type=A HTTP/1.1\r\nHost: ") + snis[i] +
                          XS("\r\nAccept: application/dns-json\r\nConnection: close\r\n\r\n");
        int64_t deadline = now_ms() + 2500;
        if (!ssl_write_all(&tls->ssl, reinterpret_cast<const uint8_t*>(req.data()), req.size(), deadline)) {
            continue;
        }
        std::vector<uint8_t> raw;
        while (now_ms() <= deadline && raw.size() < 65536) {
            if (!ssl_read_some(&tls->ssl, &raw, deadline)) {
                break;
            }
        }
        std::string body(raw.begin(), raw.end());
        std::string ip = first_ipv4(body);
        if (!ip.empty()) {
            return ip;
        }
    }
    return "";
}

void set_ping(int ms);

std::unique_ptr<Link> connect_host(const std::string& host, int* http_status, int connect_ms, int handshake_ms, bool publish_ping = true) {
    int64_t started = now_ms();
    int fd = tcp_connect(host.c_str(), 443, connect_ms);
    if (fd < 0) {
        std::string ip = doh_lookup(host);
        if (!ip.empty()) {
            fd = tcp_connect(ip.c_str(), 443, connect_ms);
        }
    }
    if (fd < 0) {
        return nullptr;
    }
    auto link = upgrade_socket(fd, host, handshake_ms, http_status);
    if (link && publish_ping) {
        set_ping(static_cast<int>(now_ms() - started));
    }
    return link;
}

struct Splitter {
    Ctr stream;
    int kind = 0;
    std::vector<uint8_t> cipher;
    std::vector<uint8_t> plain;
    bool disabled = false;

    void init(const uint8_t relay[64], uint32_t proto) {
        stream.init(relay + 8, relay + 40);
        uint8_t skip[64] = {};
        stream.xor_buf(skip, 64);
        if (proto == 0xEEEEEEEEu) {
            kind = 1;
        } else if (proto == 0xDDDDDDDDu) {
            kind = 2;
        } else {
            kind = 0;
        }
    }

    int64_t next_len() const {
        if (plain.empty()) {
            return -1;
        }
        if (kind == 0) {
            uint8_t first = plain[0] & 0x7F;
            int header;
            int64_t payload;
            if (first == 0x7F) {
                if (plain.size() < 4) {
                    return -1;
                }
                payload = (int64_t)(plain[1] | (plain[2] << 8) | (plain[3] << 16)) * 4;
                header = 4;
            } else {
                payload = (int64_t)first * 4;
                header = 1;
            }
            if (payload <= 0) {
                return 0;
            }
            int64_t total = header + payload;
            if ((int64_t)plain.size() < total) {
                return -1;
            }
            return total;
        }
        if (plain.size() < 4) {
            return -1;
        }
        uint32_t raw = (uint32_t)plain[0] | ((uint32_t)plain[1] << 8) | ((uint32_t)plain[2] << 16) | ((uint32_t)plain[3] << 24);
        int64_t payload = raw & 0x7FFFFFFF;
        if (payload <= 0) {
            return 0;
        }
        int64_t total = 4 + payload;
        if ((int64_t)plain.size() < total) {
            return -1;
        }
        return total;
    }

    std::vector<std::vector<uint8_t>> split(const uint8_t* chunk, size_t len) {
        std::vector<std::vector<uint8_t>> parts;
        if (len == 0) {
            return parts;
        }
        if (disabled) {
            parts.emplace_back(chunk, chunk + len);
            return parts;
        }
        cipher.insert(cipher.end(), chunk, chunk + len);
        std::vector<uint8_t> decoded(chunk, chunk + len);
        stream.xor_buf(decoded.data(), decoded.size());
        plain.insert(plain.end(), decoded.begin(), decoded.end());
        while (!cipher.empty()) {
            int64_t pkt = next_len();
            if (pkt < 0) {
                break;
            }
            if (pkt == 0 || pkt > 4 * 1024 * 1024) {
                parts.push_back(cipher);
                cipher.clear();
                plain.clear();
                disabled = true;
                break;
            }
            size_t n = static_cast<size_t>(pkt);
            if (cipher.size() < n) {
                break;
            }
            parts.emplace_back(cipher.begin(), cipher.begin() + static_cast<std::ptrdiff_t>(n));
            cipher.erase(cipher.begin(), cipher.begin() + static_cast<std::ptrdiff_t>(n));
            plain.erase(plain.begin(), plain.begin() + static_cast<std::ptrdiff_t>(n));
        }
        return parts;
    }

    std::vector<uint8_t> flush() {
        std::vector<uint8_t> tail;
        if (!cipher.empty()) {
            tail.swap(cipher);
            plain.clear();
        }
        return tail;
    }
};

enum {
    kFrontUnknown = 0,
    kFrontAlive = 1,
    kFrontDead = 2,
};

struct Rank {
    int ping = -1;
    int kbps = -1;
    int state = kFrontUnknown;
    int fails = 0;
    int64_t at = 0;
    int64_t check_at = 0;
    int64_t ping_at = 0;
};

struct Server {
    std::mutex mu;
    std::atomic<bool> running{false};
    int listen_fd = -1;
    std::thread accept_thread;
    std::thread health_thread;
    std::atomic<bool> health_stop{false};
    std::atomic<long long> health_wake{0};
    // Wake pipe: any thread can lift the health thread instantly, so the health
    // thread never has to poll a 200 ms timer just to notice a change.
    int wake_pipe[2] = {-1, -1};
    std::atomic<int> live_count{0};
    std::atomic<int> pool_state{0};
    std::atomic<long long> pool_bad_at{0};
    std::atomic<long long> last_net_change{0};
    std::atomic<int> dead_cycles{0};
    std::atomic<int> health_cycles{0};
    std::atomic<bool> ranks_dirty{false};
    std::atomic<int> last_dc{2};
    std::set<int> clients;
    std::string secret_hex;
    int pool_size = 4;
    bool cf_enabled = true;
    std::string cache_dir;
    std::vector<std::string> domains;
    std::map<int, std::string> preferred;
    std::map<std::string, std::pair<int, int>> cooldown;
    std::vector<std::string> found;
    std::map<std::string, Rank> rank;
    std::set<std::string> gone;
    std::string selected;
    std::atomic<bool> needs_restart{false};
    std::atomic<long long> restart_hold{0};
    std::atomic<bool> search_running{false};
    std::atomic<int> search_checked{0};
    std::atomic<int> search_live{0};
};

Server g;

struct Traffic {
    std::mutex mu;
    uint64_t up = 0;
    uint64_t down = 0;
    int ping_ms = -1;
    int64_t touch = 0;
};

Traffic traffic;

void add_up(uint64_t n) {
    if (n == 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(traffic.mu);
    traffic.up += n;
    traffic.touch = now_ms();
}

void add_down(uint64_t n) {
    if (n == 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(traffic.mu);
    traffic.down += n;
    traffic.touch = now_ms();
}

void set_ping(int ms) {
    if (ms < 1) {
        ms = 1;
    }
    if (ms > 9999) {
        ms = 9999;
    }
    std::lock_guard<std::mutex> lock(traffic.mu);
    traffic.ping_ms = ms;
}

bool still_running() {
    return g.running.load();
}

std::vector<std::string> copy_domains() {
    std::lock_guard<std::mutex> lock(g.mu);
    return g.domains;
}

bool cf_on() {
    std::lock_guard<std::mutex> lock(g.mu);
    return g.cf_enabled && !g.domains.empty();
}

int attempt_budget() {
    std::lock_guard<std::mutex> lock(g.mu);
    int n = g.pool_size;
    if (n < 1) n = 1;
    if (n > 8) n = 8;
    return n;
}

bool cooling(const std::string& domain) {
    std::lock_guard<std::mutex> lock(g.mu);
    auto it = g.cooldown.find(domain);
    if (it == g.cooldown.end()) {
        return false;
    }
    return it->second.first > (int)std::time(nullptr);
}

void mark_429(const std::string& domain) {
    std::lock_guard<std::mutex> lock(g.mu);
    auto& item = g.cooldown[domain];
    int strikes = item.second + 1;
    int delay = 45;
    for (int i = 1; i < strikes && delay < 300; ++i) {
        delay *= 2;
    }
    if (delay > 300) {
        delay = 300;
    }
    item = { (int)std::time(nullptr) + delay, strikes };
}

void clear_429(const std::string& domain) {
    std::lock_guard<std::mutex> lock(g.mu);
    g.cooldown.erase(domain);
}

// A front that could not be opened at all (dns/tls/upgrade failed).
void note_front_fail(const std::string& domain) {
    std::lock_guard<std::mutex> lock(g.mu);
    auto& item = g.rank[domain];
    item.fails += 1;
    item.check_at = now_ms();
    item.at = item.check_at;
    if (item.fails >= 2) {
        item.state = kFrontDead;
        item.ping = -1;
        item.ping_at = 0;
    }
    g.ranks_dirty.store(true);
}

// A live Telegram exchange received no tunnel data after sending a real request.
// Deprioritize only this front for a while; the client reconnects through the
// already-running local listener and can resume on another front.
void note_front_stalled(const std::string& domain) {
    const int64_t now = now_ms();
    const int now_seconds = static_cast<int>(std::time(nullptr));
    {
        std::lock_guard<std::mutex> lock(g.mu);
        auto& rank = g.rank[domain];
        rank.fails = std::max(rank.fails, 2);
        rank.state = kFrontDead;
        rank.ping = -1;
        rank.ping_at = 0;
        rank.check_at = now;
        rank.at = now;

        auto& cooldown = g.cooldown[domain];
        int strikes = std::max(1, cooldown.second + 1);
        int delay = 45;
        for (int i = 1; i < strikes && delay < 300; ++i) {
            delay *= 2;
        }
        if (delay > 300) {
            delay = 300;
        }
        cooldown = {now_seconds + delay, strikes};
        g.ranks_dirty.store(true);
    }
}

// A front that carried data for a real client. It is alive, but the ping stays unknown
// until a real round trip is measured, so the list never shows an invented number.
void note_front_ok(const std::string& domain) {
    std::lock_guard<std::mutex> lock(g.mu);
    auto& item = g.rank[domain];
    item.fails = 0;
    item.state = kFrontAlive;
    item.ping = -1;
    item.ping_at = 0;
    item.at = now_ms();
    item.check_at = item.at;
    g.ranks_dirty.store(true);
}

void note_probe(const std::string& domain, bool alive, int rtt_ms) {
    std::lock_guard<std::mutex> lock(g.mu);
    auto& item = g.rank[domain];
    item.check_at = now_ms();
    if (alive) {
        item.state = kFrontAlive;
        item.fails = 0;
        item.ping = rtt_ms > 0 ? rtt_ms : 1;
        item.ping_at = item.check_at;
        item.at = item.check_at;
        g.cooldown.erase(domain);
    } else {
        item.fails += 1;
        item.at = item.check_at;
        if (item.fails >= 2) {
            item.state = kFrontDead;
            item.ping = -1;
            item.ping_at = 0;
        }
    }
    g.ranks_dirty.store(true);
}

// Real measured throughput of live traffic, in kbit/s (exponential average).
void note_rate(const std::string& domain, uint64_t bytes, int64_t span_ms) {
    if (domain.empty() || bytes == 0 || span_ms < 250) {
        return;
    }
    int kbps = static_cast<int>((bytes * 8) / static_cast<uint64_t>(span_ms));
    if (kbps < 1) {
        kbps = 1;
    }
    if (kbps > 2000000) {
        kbps = 2000000;
    }
    std::lock_guard<std::mutex> lock(g.mu);
    auto& item = g.rank[domain];
    if (item.kbps < 0) {
        item.kbps = kbps;
    } else {
        item.kbps = (item.kbps * 2 + kbps) / 3;
    }
    item.at = now_ms();
}

std::string preferred_domain(int dc, const std::vector<std::string>& domains) {
    std::lock_guard<std::mutex> lock(g.mu);
    auto it = g.preferred.find(dc);
    if (it != g.preferred.end()) {
        return it->second;
    }
    if (domains.empty()) {
        return "";
    }
    uint32_t seed = 0;
    random_bytes(reinterpret_cast<uint8_t*>(&seed), sizeof(seed));
    std::string chosen = domains[seed % domains.size()];
    g.preferred[dc] = chosen;
    return chosen;
}

void remember_domain(int dc, const std::string& domain) {
    std::string dir;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        g.preferred[dc] = domain;
        dir = g.cache_dir;
    }
    if (dir.empty()) {
        return;
    }
    std::string path = dir + XS("/cf-pref.txt");
    FILE* file = std::fopen(path.c_str(), "a");
    if (!file) {
        return;
    }
    std::fprintf(file, "%d %s\n", dc, domain.c_str());
    std::fclose(file);
}

std::vector<std::string> copy_found() {
    std::lock_guard<std::mutex> lock(g.mu);
    return g.found;
}

bool recent_traffic() {
    std::lock_guard<std::mutex> lock(traffic.mu);
    return traffic.touch > 0 && now_ms() - traffic.touch < 3000;
}

// Lifts the health thread without waiting for its timer. Without the pipe a wake up
// request could sit unnoticed until the current sleep elapsed.
void poke_health() {
    g.health_wake.store(1);
    int fd = g.wake_pipe[1];
    if (fd >= 0) {
        uint8_t one = 1;
        ssize_t wr = ::write(fd, &one, 1);
        (void)wr;
    }
}

// One pipe per process, created on the first start and never closed, so no thread can
// ever race with a closing descriptor.
void ensure_wake_pipe() {
    if (g.wake_pipe[0] >= 0) {
        return;
    }
    int fds[2] = {-1, -1};
    if (::pipe(fds) == 0) {
        for (int i = 0; i < 2; ++i) {
            int flags = fcntl(fds[i], F_GETFL, 0);
            if (flags >= 0) {
                fcntl(fds[i], F_SETFL, flags | O_NONBLOCK);
            }
        }
        g.wake_pipe[0] = fds[0];
        g.wake_pipe[1] = fds[1];
    }
}

// Only the listener dying asks the service for a restart; front problems are healed in place.
void mark_needs_restart() {
    g.needs_restart.store(true);
}

void note_pool_bad() {
    if (g.pool_bad_at.load() == 0) {
        g.pool_bad_at.store(now_ms());
    }
    poke_health();
}

void note_pool_ok() {
    g.pool_bad_at.store(0);
}

int domain_score(const std::string& domain) {
    Rank rank;
    bool has = false;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        auto it = g.rank.find(domain);
        if (it != g.rank.end()) {
            rank = it->second;
            has = true;
        }
    }
    if (!has) {
        return 500000;
    }
    int ping = rank.ping < 1 ? 2000 : rank.ping;
    int kbps = rank.kbps < 1 ? 0 : rank.kbps;
    if (kbps > 50000) {
        kbps = 50000;
    }
    int score = ping;
    if (kbps > 0) {
        score += 1500000 / kbps;
    } else {
        score += 300;
    }
    return score + rank.fails * 1500;
}

std::vector<std::string> order_domains(int dc) {
    std::vector<std::string> builtin = copy_domains();
    std::vector<std::string> extra = copy_found();
    std::string chosen;
    std::string remembered;
    std::map<std::string, Rank> ranks;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        chosen = g.selected;
        auto it = g.preferred.find(dc);
        if (it != g.preferred.end()) {
            remembered = it->second;
        }
        ranks = g.rank;
    }
    auto push_unique = [](std::vector<std::string>* out, const std::string& domain) {
        if (domain.empty()) {
            return;
        }
        if (std::find(out->begin(), out->end(), domain) == out->end()) {
            out->push_back(domain);
        }
    };
    std::vector<std::string> pool;
    for (const std::string& domain : extra) {
        push_unique(&pool, domain);
    }
    for (const std::string& domain : builtin) {
        push_unique(&pool, domain);
    }
    auto by_score = [](const std::string& a, const std::string& b) {
        return domain_score(a) < domain_score(b);
    };
    std::vector<std::string> trusted;   // alive a moment ago
    std::vector<std::string> fresh;     // alive and just probed
    std::vector<std::string> unknown;   // never probed
    std::vector<std::string> dead;      // failed, retry last
    std::vector<std::string> cooling_list;
    int64_t now = now_ms();
    for (const std::string& domain : pool) {
        if (cooling(domain)) {
            cooling_list.push_back(domain);
            continue;
        }
        auto it = ranks.find(domain);
        if (it == ranks.end() || it->second.state == kFrontUnknown) {
            unknown.push_back(domain);
            continue;
        }
        if (it->second.state == kFrontDead) {
            dead.push_back(domain);
            continue;
        }
        if (now - it->second.ping_at < kAliveFreshMs && it->second.ping_at > 0) {
            fresh.push_back(domain);
        } else {
            trusted.push_back(domain);
        }
    }
    std::stable_sort(fresh.begin(), fresh.end(), by_score);
    std::stable_sort(trusted.begin(), trusted.end(), by_score);
    std::stable_sort(unknown.begin(), unknown.end(), by_score);
    std::stable_sort(dead.begin(), dead.end(), by_score);
    std::stable_sort(cooling_list.begin(), cooling_list.end(), by_score);
    // Сервер, который только что дважды не открылся по-настоящему, первым больше
    // не пробуется: сессия сразу уходит на живой. Он останется в конце списка и
    // получит шанс, когда остынет.
    auto usable = [&](const std::string& domain) {
        auto it = ranks.find(domain);
        return it == ranks.end() || it->second.state != kFrontDead;
    };
    std::vector<std::string> ordered;
    if (!chosen.empty() && !cooling(chosen) && usable(chosen)) {
        push_unique(&ordered, chosen);
    }
    if (!remembered.empty() && !cooling(remembered) && usable(remembered)) {
        push_unique(&ordered, remembered);
    }
    for (const std::string& domain : fresh) {
        push_unique(&ordered, domain);
    }
    for (const std::string& domain : trusted) {
        push_unique(&ordered, domain);
    }
    for (const std::string& domain : unknown) {
        push_unique(&ordered, domain);
    }
    for (const std::string& domain : dead) {
        push_unique(&ordered, domain);
    }
    for (const std::string& domain : cooling_list) {
        push_unique(&ordered, domain);
    }
    return ordered;
}

void bridge_tcp(int client, int remote, Ctr client_dec, Ctr client_enc, Ctr tg_enc, Ctr tg_dec) {
    set_nonblock(client, true);
    set_nonblock(remote, true);
    int64_t last = now_ms();
    while (still_running()) {
        pollfd pfds[2]{};
        pfds[0].fd = client;
        pfds[0].events = POLLIN;
        pfds[1].fd = remote;
        pfds[1].events = POLLIN;
        int pr = poll(pfds, 2, 30000);
        if (!still_running()) {
            break;
        }
        if (pr < 0) {
            if (errno == EINTR) {
                continue;
            }
            break;
        }
        if (pr == 0) {
            if (now_ms() - last > 120000) {
                break;
            }
            continue;
        }
        if (pfds[0].revents & POLLIN) {
            uint8_t buf[65536];
            ssize_t n = ::recv(client, buf, sizeof(buf), 0);
            if (n == 0 || (n < 0 && errno != EAGAIN && errno != EINTR)) {
                break;
            }
            if (n > 0) {
                last = now_ms();
                client_dec.xor_buf(buf, static_cast<size_t>(n));
                tg_enc.xor_buf(buf, static_cast<size_t>(n));
                if (!send_all(remote, buf, static_cast<size_t>(n), 20000)) {
                    break;
                }
                add_up(static_cast<uint64_t>(n));
            }
        } else if (pfds[0].revents & (POLLERR | POLLHUP | POLLNVAL)) {
            break;
        }
        if (pfds[1].revents & POLLIN) {
            uint8_t buf[65536];
            ssize_t n = ::recv(remote, buf, sizeof(buf), 0);
            if (n == 0 || (n < 0 && errno != EAGAIN && errno != EINTR)) {
                break;
            }
            if (n > 0) {
                last = now_ms();
                tg_dec.xor_buf(buf, static_cast<size_t>(n));
                client_enc.xor_buf(buf, static_cast<size_t>(n));
                if (!send_all(client, buf, static_cast<size_t>(n), 20000)) {
                    break;
                }
                add_down(static_cast<uint64_t>(n));
            }
        } else if (pfds[1].revents & (POLLERR | POLLHUP | POLLNVAL)) {
            break;
        }
    }
}

// Turns the plaintext Telegram transport stream into complete frames.
// The obfuscation layer of the tunnel is rebuilt on every reconnect, the plaintext
// stream of the client is continuous, so framing works on the plaintext side only.
struct Framer {
    std::vector<uint8_t> partial;
    bool disabled = false;

    int64_t want(const std::vector<uint8_t>& buf, int kind) const {
        if (buf.empty()) {
            return -1;
        }
        if (kind == 0) {
            uint8_t first = static_cast<uint8_t>(buf[0] & 0x7F);
            if (first == 0x7F) {
                if (buf.size() < 4) {
                    return -1;
                }
                int64_t payload = (int64_t)(buf[1] | (buf[2] << 8) | (buf[3] << 16)) * 4;
                return payload <= 0 ? 0 : 4 + payload;
            }
            int64_t payload = (int64_t)first * 4;
            return payload <= 0 ? 0 : 1 + payload;
        }
        if (buf.size() < 4) {
            return -1;
        }
        int64_t payload = static_cast<int64_t>(le32_at(buf.data()) & 0x7FFFFFFF);
        return payload <= 0 ? 0 : 4 + payload;
    }

    void feed(const uint8_t* data, size_t len, int kind, std::deque<std::vector<uint8_t>>* out) {
        if (len == 0 || out == nullptr) {
            return;
        }
        if (disabled) {
            out->emplace_back(data, data + len);
            return;
        }
        partial.insert(partial.end(), data, data + len);
        while (!partial.empty()) {
            int64_t need = want(partial, kind);
            if (need < 0) {
                break;
            }
            if (need == 0 || need > 4 * 1024 * 1024) {
                out->emplace_back(partial.begin(), partial.end());
                partial.clear();
                disabled = true;
                break;
            }
            if (static_cast<int64_t>(partial.size()) < need) {
                break;
            }
            out->emplace_back(partial.begin(), partial.begin() + static_cast<std::ptrdiff_t>(need));
            partial.erase(partial.begin(), partial.begin() + static_cast<std::ptrdiff_t>(need));
        }
    }
};

// Serves one client connection over one upstream tunnel.
// 0 = the client went away (or the proxy stopped), 1 = tunnel died before any payload,
// 2 = tunnel died after payload flowed, 4 = a real client request got no tunnel reply.
int pump_tunnel(int client, Link* link, Ctr* tg_enc, Ctr* tg_dec,
                Ctr* client_dec, Ctr* client_enc, Framer* framer,
                std::deque<std::vector<uint8_t>>* outbox, size_t* outbox_bytes, int proto_kind,
                const std::string& base, int64_t* last_activity, uint64_t* moved,
                int64_t connected_at, int* first_rtt) {
    set_nonblock(client, true);
    int64_t last = now_ms();
    int64_t last_send_at = 0;
    bool client_closed = false;
    bool tunnel_dead = false;
    uint64_t moved_here = 0;
    uint64_t window_bytes = 0;
    int64_t window_start = now_ms();
    bool marked_alive = false;
    bool awaiting_response = false;
    int64_t response_wait_started = 0;
    unsigned pending_frames = 0;
    size_t pending_bytes = 0;
    bool response_stalled = false;
    // A real round trip: from the last frame we pushed into the tunnel until the far side
    // answered. An unprompted push counts from the relay instead.
    auto mark_rtt = [&]() {
        if (first_rtt != nullptr && *first_rtt <= 0) {
            int64_t origin = last_send_at > 0 ? last_send_at : connected_at;
            if (origin > 0) {
                int rtt = static_cast<int>(now_ms() - origin);
                *first_rtt = rtt > 0 ? rtt : 1;
            }
        }
    };
    // The moment the tunnel delivers real data from the far side the front is proven
    // alive, a long living session must not stay "unknown" in the list while it works.
    auto mark_alive = [&]() {
        if (marked_alive) {
            return;
        }
        marked_alive = true;
        if (first_rtt != nullptr && *first_rtt > 0) {
            note_probe(base, true, *first_rtt);
        } else {
            note_front_ok(base);
        }
    };
    auto note_tunnel_data = [&](const std::vector<uint8_t>& data) {
        if (!data.empty()) {
            awaiting_response = false;
            response_wait_started = 0;
            pending_frames = 0;
            pending_bytes = 0;
        }
    };
    auto has_stalled_request = [&]() {
        return awaiting_response
                && (!marked_alive || pending_frames >= kTunnelStallMinFrames
                    || pending_bytes >= kTunnelStallMinBytes);
    };
    auto flush_rate = [&]() {
        int64_t span = now_ms() - window_start;
        if (span >= 1000 && window_bytes > 0) {
            note_rate(base, window_bytes, span);
            window_bytes = 0;
            window_start = now_ms();
        }
    };
    auto send_frame = [&](const std::vector<uint8_t>& frame) -> bool {
        if (frame.empty()) {
            return true;
        }
        if (frame.size() > 4u * 1024u * 1024u) {
            return false;
        }
        std::vector<uint8_t> encoded = frame;
        tg_enc->xor_buf(encoded.data(), encoded.size());
        if (!link->write_frame(0x2, encoded.data(), encoded.size())) {
            return false;
        }
        last_send_at = now_ms();
        if (!awaiting_response) {
            awaiting_response = true;
            response_wait_started = last_send_at;
        }
        if (pending_frames < kTunnelStallMinFrames) {
            ++pending_frames;
        }
        pending_bytes = std::min(kTunnelStallMinBytes, pending_bytes + frame.size());
        add_up(encoded.size());
        window_bytes += encoded.size();
        moved_here += encoded.size();
        if (moved) {
            *moved += encoded.size();
        }
        return true;
    };
    while (!outbox->empty()) {
        if (!send_frame(outbox->front())) {
            return moved_here > 0 ? 2 : 1;
        }
        outbox->pop_front();
        last = now_ms();
    }
    if (outbox_bytes) {
        *outbox_bytes = 0;
    }
    while (still_running() && link && !link->dead) {
        bool full = outbox_bytes != nullptr && *outbox_bytes > 768 * 1024;
        pollfd pfds[2]{};
        pfds[0].fd = client;
        pfds[0].events = full ? 0 : POLLIN;
        pfds[1].fd = link->fd();
        pfds[1].events = POLLIN;
        int poll_timeout = 30000;
        if (has_stalled_request()) {
            int64_t remaining = kTunnelResponseStallMs - (now_ms() - response_wait_started);
            if (remaining <= 0) {
                pollfd pending{};
                pending.fd = link->fd();
                pending.events = POLLIN;
                if (poll(&pending, 1, 0) <= 0) {
                    response_stalled = true;
                    break;
                }
                poll_timeout = 0;
            } else if (remaining < poll_timeout) {
                poll_timeout = static_cast<int>(remaining);
            }
        }
        int pr = poll(pfds, 2, poll_timeout);
        if (!still_running()) {
            break;
        }
        if (pr < 0) {
            if (errno == EINTR) {
                continue;
            }
            break;
        }
        if (pr == 0) {
            flush_rate();
            if (has_stalled_request()
                    && now_ms() - response_wait_started >= kTunnelResponseStallMs) {
                response_stalled = true;
                break;
            }
            if (now_ms() - last > 30000) {
                // A keep alive ping; a silent idle tunnel is not killed here, dead peers
                // are detected by the TCP keepalive and by the health thread instead.
                if (!link->write_frame(0x9, nullptr, 0)) {
                    tunnel_dead = true;
                    break;
                }
                last = now_ms();
            }
            continue;
        }
        // A busy tunnel still has to report its real speed about once a second.
        if (window_bytes > 0 && now_ms() - window_start >= 1000) {
            flush_rate();
        }
        // The tunnel is inspected before the client socket so that a dying tunnel never
        // swallows client data: whatever was not read stays in the socket for the next one.
        if (link->dead || (pfds[1].revents & (POLLERR | POLLHUP | POLLNVAL))) {
            std::vector<uint8_t> tail;
            int spins = 0;
            while (spins < 8 && link->pump()) {
                ++spins;
            }
            uint64_t flushed = 0;
            while (link->next_data(&tail)) {
                note_tunnel_data(tail);
                mark_rtt();
                mark_alive();
                tg_dec->xor_buf(tail.data(), tail.size());
                client_enc->xor_buf(tail.data(), tail.size());
                if (!send_all(client, tail.data(), tail.size(), 20000)) {
                    break;
                }
                add_down(tail.size());
                window_bytes += tail.size();
                flushed += tail.size();
            }
            if (flushed > 0) {
                last = now_ms();
                if (moved) {
                    *moved += flushed;
                }
                continue;
            }
            tunnel_dead = true;
            break;
        }
        if (pfds[0].revents & POLLIN) {
            uint8_t buf[65536];
            ssize_t n = ::recv(client, buf, sizeof(buf), 0);
            if (n == 0 || (n < 0 && errno != EAGAIN && errno != EINTR)) {
                client_closed = true;
                break;
            }
            if (n > 0) {
                last = now_ms();
                client_dec->xor_buf(buf, static_cast<size_t>(n));
                framer->feed(buf, static_cast<size_t>(n), proto_kind, outbox);
                if (outbox_bytes) {
                    for (const auto& item : *outbox) {
                        *outbox_bytes += item.size();
                    }
                }
                while (!outbox->empty()) {
                    if (!send_frame(outbox->front())) {
                        tunnel_dead = true;
                        break;
                    }
                    if (outbox_bytes) {
                        *outbox_bytes -= outbox->front().size();
                    }
                    outbox->pop_front();
                }
                if (tunnel_dead) {
                    break;
                }
            }
        } else if (pfds[0].revents & (POLLERR | POLLHUP | POLLNVAL)) {
            client_closed = true;
            break;
        }
        if (pfds[1].revents & (POLLIN | POLLERR | POLLHUP)) {
            int spins = 0;
            while (spins < 8 && link->pump()) {
                ++spins;
            }
            if (link->dead && link->rx.size() == link->rx_pos) {
                tunnel_dead = true;
                break;
            }
            std::vector<uint8_t> message;
            bool failed = false;
            while (link->next_data(&message)) {
                last = now_ms();
                note_tunnel_data(message);
                mark_rtt();
                mark_alive();
                tg_dec->xor_buf(message.data(), message.size());
                client_enc->xor_buf(message.data(), message.size());
                if (!send_all(client, message.data(), message.size(), 20000)) {
                    failed = true;
                    break;
                }
                add_down(message.size());
                window_bytes += message.size();
                if (moved) {
                    *moved += message.size();
                }
            }
            if (failed || link->dead) {
                if (link->dead) {
                    tunnel_dead = true;
                }
                break;
            }
        }
    }
    flush_rate();
    if (last_activity) {
        *last_activity = last;
    }
    if (!still_running() || client_closed) {
        return 0;
    }
    if (response_stalled || (tunnel_dead && awaiting_response)) {
        return 4;
    }
    if (tunnel_dead && moved_here == 0) {
        return 1;
    }
    return tunnel_dead ? 2 : 0;
}

// Opens one front and pumps the client session through it. A fresh relay handshake and a
// fresh obfuscation layer are built for every attempt, the client stream itself never resets.
int serve_front(int client, const std::string& base, int dc, const uint8_t proto_tag[4], int proto_kind,
                Framer* framer, std::deque<std::vector<uint8_t>>* outbox, size_t* outbox_bytes,
                Ctr* client_dec, Ctr* client_enc, int64_t* last_activity, uint64_t* moved,
                int* first_rtt) {
    int status = 0;
    std::string host = XS("kws") + std::to_string(dc) + XS(".") + base;
    auto link = connect_host(host, &status, kConnectMs, kHandshakeMs, false);
    if (!link) {
        if (status == 429) {
            mark_429(base);
            return 3;
        }
        return 1;
    }
    clear_429(base);
    uint8_t relay[64];
    Ctr tg_enc;
    Ctr tg_dec;
    build_relay(proto_tag, dc, false, relay, &tg_enc, &tg_dec);
    if (!link->write_frame(0x2, relay, 64)) {
        return 1;
    }
    int64_t connected_at = now_ms();
    return pump_tunnel(client, link.get(), &tg_enc, &tg_dec, client_dec, client_enc, framer,
                       outbox, outbox_bytes, proto_kind, base, last_activity, moved,
                       connected_at, first_rtt);
}

bool tcp_fallback(int client, int dc, const uint8_t relay[64], Ctr client_dec, Ctr client_enc, Ctr tg_enc, Ctr tg_dec) {
    std::string ip = dc_ip(dc);
    if (ip.empty()) {
        return false;
    }
    int64_t started = now_ms();
    int remote = tcp_connect(ip.c_str(), 443, 8000);
    if (remote < 0) {
        return false;
    }
    set_ping(static_cast<int>(now_ms() - started));
    set_timeout(remote, 8);
    if (!send_all(remote, relay, 64, 8000)) {
        ::close(remote);
        return false;
    }
    set_timeout(remote, 0);
    bridge_tcp(client, remote, client_dec, client_enc, tg_enc, tg_dec);
    ::close(remote);
    return true;
}

bool client_alive(int fd) {
    pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLIN;
    int pr = poll(&pfd, 1, 0);
    if (pr < 0) {
        return errno == EINTR;
    }
    if (pr == 0) {
        return true;
    }
    if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) {
        return false;
    }
    if (pfd.revents & POLLIN) {
        uint8_t probe = 0;
        ssize_t n = ::recv(fd, &probe, 1, MSG_PEEK);
        if (n == 0) {
            return false;
        }
        if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) {
            return false;
        }
    }
    return true;
}

void handle_client(int client) {
    // Data pumping may yield: niceness 8 is invisible in throughput but keeps the
    // phone from heating up when the CPU is busy.
    setpriority(PRIO_PROCESS, 0, 8);
    set_timeout(client, 10);
    uint8_t handshake[64];
    if (!recv_exact(client, handshake, 64)) {
        return;
    }
    if (looks_like_http(handshake, 64)) {
        const char* gone = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n";
        send_all(client, reinterpret_cast<const uint8_t*>(gone), std::strlen(gone), 2000);
        return;
    }
    std::string secret_hex;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        secret_hex = g.secret_hex;
    }
    std::vector<uint8_t> secret = hex_decode(secret_hex);
    Handshake parsed;
    if (!read_handshake(handshake, secret.data(), secret.size(), &parsed)) {
        return;
    }
    g.last_dc.store(parsed.dc);
    set_timeout(client, 0);

    if (!cf_on()) {
        uint8_t relay[64];
        Ctr tg_enc;
        Ctr tg_dec;
        build_relay(parsed.proto_tag, parsed.dc, parsed.media, relay, &tg_enc, &tg_dec);
        tcp_fallback(client, parsed.dc, relay, parsed.client_dec, parsed.client_enc, tg_enc, tg_dec);
        return;
    }

    int proto_kind = parsed.proto == 0xEEEEEEEEu ? 1 : (parsed.proto == 0xDDDDDDDDu ? 2 : 0);
    Framer framer;
    std::deque<std::vector<uint8_t>> outbox;
    size_t outbox_bytes = 0;
    int64_t started = now_ms();
    int64_t last_activity = started;
    uint64_t moved = 0;
    bool served = false;
    int same_front_retries = 0;
    int attempts = 0;
    int64_t last_try_at = 0;
    std::vector<std::string> queue = order_domains(parsed.dc);
    size_t queue_pos = 0;

    while (still_running() && client_alive(client)) {
        if (!served && now_ms() - started > kSessionBudgetMs) {
            break;
        }
        if (served && now_ms() - last_activity > 900000) {
            break;
        }
        if (attempts > 96) {
            break;
        }
        if (queue.empty()) {
            // No front to try at all: wait calmly instead of spinning, a working front
            // appears as soon as the search or the health thread finds one.
            if (now_ms() - last_activity > 60000) {
                break;
            }
            usleep(500000);
            queue = order_domains(parsed.dc);
            queue_pos = 0;
            continue;
        }
        if (queue_pos >= queue.size()) {
            queue = order_domains(parsed.dc);
            queue_pos = 0;
            if (!served) {
                note_pool_bad();
                if (now_ms() - started > kSessionBudgetMs) {
                    break;
                }
            }
            usleep(150000);
            if (!client_alive(client)) {
                break;
            }
            continue;
        }
        std::string base = queue[queue_pos++];
        ++attempts;
        if (!served && last_try_at > 0 && now_ms() - last_try_at < 500) {
            // A front that refused in a few milliseconds must not turn the retry
            // loop into a handshake conveyor: at most two tries per second. A real
            // attempt already took its time, so it is never padded.
            while (now_ms() - last_try_at < 500 && still_running() && client_alive(client)) {
                usleep(100000);
            }
        }
        int rtt = 0;
        int result = serve_front(client, base, parsed.dc, parsed.proto_tag, proto_kind, &framer, &outbox,
                                 &outbox_bytes, &parsed.client_dec, &parsed.client_enc,
                                 &last_activity, &moved, &rtt);
        last_try_at = now_ms();
        if (!still_running() || !client_alive(client)) {
            break;
        }
        if (result == 0) {
            break;
        }
        if (result == 4) {
            note_front_stalled(base);
            break;  // Close only this local MTProto session; Telegram reconnects via another front.
        }
        if (result == 3) {
            continue;
        }
        if (result == 1) {
            note_front_fail(base);
            continue;
        }
        served = true;
        if (rtt > 0) {
            // A real answered round trip: this is the only source of the shown ping.
            note_probe(base, true, rtt);
        } else {
            note_front_ok(base);
        }
        remember_domain(parsed.dc, base);
        note_pool_ok();
        if (same_front_retries < 2) {
            ++same_front_retries;
            queue.insert(queue.begin() + static_cast<std::ptrdiff_t>(queue_pos), base);
        } else {
            same_front_retries = 0;
        }
    }
}


void accept_loop() {
    // The listener idles in poll, but under load it must yield to the UI and to
    // system processes: a lower priority keeps the body cooler.
    setpriority(PRIO_PROCESS, 0, 10);
    while (g.running.load()) {
        int listen_fd = g.listen_fd;
        if (listen_fd < 0) {
            break;
        }
        pollfd wait_fd{};
        wait_fd.fd = listen_fd;
        wait_fd.events = POLLIN;
        int ready = poll(&wait_fd, 1, 30000);
        if (!g.running.load()) {
            break;
        }
        if (ready < 0) {
            if (errno == EINTR) {
                continue;
            }
            usleep(200000);
            continue;
        }
        if (ready == 0) {
            continue;
        }
        sockaddr_in peer{};
        socklen_t peer_len = sizeof(peer);
        int client = ::accept(listen_fd, reinterpret_cast<sockaddr*>(&peer), &peer_len);
        if (client < 0) {
            if (!g.running.load()) {
                break;
            }
            if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) {
                continue;
            }
            usleep(200000);
            continue;
        }
        {
            std::lock_guard<std::mutex> lock(g.mu);
            if (!g.running.load()) {
                ::close(client);
                break;
            }
            g.clients.insert(client);
        }
        // A client showed up: check the fronts right away instead of waiting for
        // the next scheduled cycle.
        poke_health();
        tune_socket(client);
        std::thread([client]() {
            handle_client(client);
            std::lock_guard<std::mutex> lock(g.mu);
            g.clients.erase(client);
            ::close(client);
        }).detach();
    }
    if (g.running.load()) {
        g.running.store(false);
    }
}

void add_domain(std::vector<std::string>* out, const std::string& raw) {
    std::string domain = normalize_cf_domain(raw);
    if (domain.empty()) {
        return;
    }
    if (std::find(out->begin(), out->end(), domain) == out->end()) {
        out->push_back(domain);
    }
}

void load_cached_domains(const std::string& dir, std::vector<std::string>* out) {
    if (dir.empty()) {
        return;
    }
    FILE* file = std::fopen((dir + XS("/ts-cache.txt")).c_str(), "r");
    if (!file) {
        return;
    }
    char line[256];
    while (std::fgets(line, sizeof(line), file)) {
        add_domain(out, line);
    }
    std::fclose(file);
}

void load_preferred(const std::string& dir) {
    if (dir.empty()) {
        return;
    }
    FILE* file = std::fopen((dir + XS("/cf-pref.txt")).c_str(), "r");
    if (!file) {
        return;
    }
    char line[256];
    while (std::fgets(line, sizeof(line), file)) {
        int dc = 0;
        char domain[200];
        if (std::sscanf(line, "%d %199s", &dc, domain) == 2) {
            std::string normalized = normalize_cf_domain(domain);
            if (!normalized.empty()) {
                g.preferred[dc] = normalized;
            }
        }
    }
    std::fclose(file);
}

bool is_builtin_domain(const std::string& domain) {
    for (const std::string& plain : plain_domains()) {
        if (domain == plain) {
            return true;
        }
    }
    for (const std::string& encoded : encoded_domains()) {
        if (normalize_cf_domain(encoded) == domain) {
            return true;
        }
    }
    return false;
}

std::string trim_host(const std::string& raw) {
    size_t begin = 0;
    size_t end = raw.size();
    while (begin < end && (raw[begin] == ' ' || raw[begin] == '\t' || raw[begin] == '\r' || raw[begin] == '\n')) {
        ++begin;
    }
    while (end > begin && (raw[end - 1] == ' ' || raw[end - 1] == '\t' || raw[end - 1] == '\r' || raw[end - 1] == '\n')) {
        --end;
    }
    return raw.substr(begin, end - begin);
}

std::string lower_host(std::string text) {
    for (char& c : text) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    return text;
}

bool valid_host(const std::string& host) {
    if (host.size() < 5 || host.size() > 80 || host.find('.') == std::string::npos) {
        return false;
    }
    if (host.front() == '.' || host.back() == '.' || host.find("..") != std::string::npos) {
        return false;
    }
    for (unsigned char c : host) {
        bool ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
        if (!ok) {
            return false;
        }
    }
    if (host.find("telegram") != std::string::npos || host.find("cloudflare") != std::string::npos
            || host.find("google") != std::string::npos || host.find("github") != std::string::npos) {
        return false;
    }
    return true;
}

std::string strip_klink(const std::string& host) {
    if (host.rfind(XS("kws"), 0) != 0) {
        return host;
    }
    size_t dot = host.find('.');
    if (dot == std::string::npos || dot <= 3) {
        return host;
    }
    for (size_t i = 3; i < dot; ++i) {
        if (host[i] < '0' || host[i] > '9') {
            return host;
        }
    }
    return host.substr(dot + 1);
}

void add_candidate(std::vector<std::string>* out, const std::string& raw) {
    std::string text = lower_host(trim_host(raw));
    size_t scheme = text.find("://");
    if (scheme != std::string::npos) {
        text = text.substr(scheme + 3);
    }
    size_t slash = text.find('/');
    if (slash != std::string::npos) {
        text = text.substr(0, slash);
    }
    size_t colon = text.find(':');
    if (colon != std::string::npos) {
        text = text.substr(0, colon);
    }
    bool from_klink = text.rfind(XS("kws"), 0) == 0;
    std::string chosen;
    if (from_klink) {
        chosen = strip_klink(text);
    } else if (text.size() > 4 && text.compare(text.size() - 4, 4, ".com") == 0 && text.find('.') == text.size() - 4) {
        bool letters = true;
        for (char c : text.substr(0, text.size() - 4)) {
            if (c < 'a' || c > 'z') {
                letters = false;
                break;
            }
        }
        if (letters) {
            chosen = normalize_cf_domain(text);
        }
    }
    if (!valid_host(chosen) || is_builtin_domain(chosen)) {
        return;
    }
    if (std::find(out->begin(), out->end(), chosen) == out->end()) {
        out->push_back(chosen);
    }
}

void add_list_line(std::vector<std::string>* out, const std::string& raw) {
    std::string text = lower_host(trim_host(raw));
    if (text.empty() || text[0] == '#') {
        return;
    }
    add_candidate(out, text);
    bool encoded_com = text.size() > 4 && text.compare(text.size() - 4, 4, ".com") == 0 && text.find('.') == text.size() - 4;
    if (encoded_com || !valid_host(text) || is_builtin_domain(text)) {
        return;
    }
    if (std::find(out->begin(), out->end(), text) == out->end()) {
        out->push_back(text);
    }
}

void add_list_body(const std::string& body, std::vector<std::string>* out) {
    std::string line;
    for (char c : body) {
        if (c == '\n' || c == '\r') {
            add_list_line(out, line);
            line.clear();
        } else {
            line.push_back(c);
        }
    }
    add_list_line(out, line);
}

void collect_hosts(const std::string& text, std::vector<std::string>* out) {
    std::string token;
    auto flush = [&]() {
        if (!token.empty()) {
            add_candidate(out, token);
            token.clear();
        }
    };
    for (unsigned char c : text) {
        char ch = c >= 'A' && c <= 'Z' ? static_cast<char>(c - 'A' + 'a') : static_cast<char>(c);
        bool ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-';
        if (ok) {
            token.push_back(ch);
        } else {
            flush();
        }
    }
    flush();
}

std::string unchunk(const std::string& body) {
    std::string out;
    size_t i = 0;
    while (i < body.size() && out.size() < 180000) {
        size_t line = body.find("\r\n", i);
        if (line == std::string::npos) {
            break;
        }
        int size = 0;
        bool any = false;
        for (size_t k = i; k < line; ++k) {
            char c = body[k];
            int value = -1;
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                value = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                value = c - 'A' + 10;
            } else if (c == ';') {
                break;
            }
            if (value < 0) {
                continue;
            }
            any = true;
            size = (size << 4) | value;
        }
        if (!any || size <= 0) {
            break;
        }
        size_t start = line + 2;
        if (start >= body.size()) {
            break;
        }
        size_t take = static_cast<size_t>(size);
        if (start + take > body.size()) {
            take = body.size() - start;
        }
        out.append(body, start, take);
        i = start + static_cast<size_t>(size) + 2;
    }
    return out;
}

std::string https_get(const std::string& host, const std::string& path, int timeout_ms) {
    int fd = tcp_connect(host.c_str(), 443, timeout_ms);
    if (fd < 0) {
        std::string ip = doh_lookup(host);
        if (!ip.empty()) {
            fd = tcp_connect(ip.c_str(), 443, timeout_ms);
        }
    }
    if (fd < 0) {
        return "";
    }
    auto tls = std::make_unique<TlsSocket>();
    tls->fd = fd;
    if (!tls_handshake(tls.get(), host.c_str(), timeout_ms)) {
        return "";
    }
        std::string req = XS("GET ") + path + XS(" HTTP/1.1\r\nHost: ") + host +
                          XS("\r\nUser-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n");
    int64_t deadline = now_ms() + timeout_ms;
    if (!ssl_write_all(&tls->ssl, reinterpret_cast<const uint8_t*>(req.data()), req.size(), deadline)) {
        return "";
    }
    std::vector<uint8_t> raw;
    while (now_ms() <= deadline && raw.size() < 180000) {
        if (!ssl_read_some(&tls->ssl, &raw, deadline)) {
            break;
        }
    }
    std::string text(raw.begin(), raw.end());
    size_t split = text.find("\r\n\r\n");
    if (split == std::string::npos) {
        return "";
    }
    std::string headers = lower_host(text.substr(0, split));
    std::string body = text.substr(split + 4);
    if (headers.find("transfer-encoding: chunked") != std::string::npos) {
        return unchunk(body);
    }
    return body;
}

void write_text_file(const std::string& path, const std::string& body) {
    FILE* file = std::fopen(path.c_str(), "w");
    if (!file) {
        return;
    }
    if (!body.empty()) {
        std::fwrite(body.data(), 1, body.size(), file);
    }
    std::fclose(file);
}

void save_internet(const std::string& dir) {
    if (dir.empty()) {
        return;
    }
    std::vector<std::string> found;
    std::map<std::string, Rank> rank;
    std::string selected;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        found = g.found;
        rank = g.rank;
        selected = g.selected;
    }
    std::string domains;
    std::string ranks;
    for (const std::string& domain : found) {
        domains += domain;
        domains += "\n";
        auto it = rank.find(domain);
        if (it != rank.end()) {
            ranks += domain;
            ranks += " ";
            ranks += std::to_string(it->second.ping);
            ranks += " ";
            ranks += std::to_string(it->second.kbps);
            ranks += " ";
            ranks += std::to_string(it->second.state);
            ranks += "\n";
        }
    }
    write_text_file(dir + XS("/cf-internet.txt"), domains);
    write_text_file(dir + XS("/cf-rank.txt"), ranks);
    write_text_file(dir + XS("/cf-choice.txt"), selected.empty() ? std::string() : selected + "\n");
}

bool is_gone(const std::string& domain) {
    std::lock_guard<std::mutex> lock(g.mu);
    return g.gone.count(domain) != 0;
}

void load_gone(const std::string& dir, std::set<std::string>* out) {
    if (dir.empty() || out == nullptr) {
        return;
    }
    FILE* file = std::fopen((dir + XS("/cf-gone.txt")).c_str(), "r");
    if (!file) {
        return;
    }
    char line[256];
    while (std::fgets(line, sizeof(line), file)) {
        std::string host = lower_host(trim_host(line));
        if (valid_host(host)) {
            out->insert(host);
        }
    }
    std::fclose(file);
}

void save_gone(const std::string& dir) {
    if (dir.empty()) {
        return;
    }
    std::set<std::string> gone;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        gone = g.gone;
    }
    std::string body;
    for (const std::string& domain : gone) {
        body += domain;
        body += "\n";
    }
    write_text_file(dir + XS("/cf-gone.txt"), body);
}

void erase_found(const std::string& domain) {
    auto it = std::find(g.found.begin(), g.found.end(), domain);
    if (it != g.found.end()) {
        g.found.erase(it);
    }
    g.rank.erase(domain);
    if (g.selected == domain) {
        g.selected.clear();
    }
    g.search_live.store(static_cast<int>(g.found.size()));
}

void forget_failed(const std::vector<std::string>& domains) {
    if (domains.empty()) {
        return;
    }
    std::string keep;
    if (g.running.load() && recent_traffic()) {
        std::lock_guard<std::mutex> lock(g.mu);
        keep = g.selected;
    }
    std::string dir;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        for (const std::string& domain : domains) {
            if (!keep.empty() && domain == keep) {
                continue;
            }
            erase_found(domain);
        }
        dir = g.cache_dir;
    }
    save_internet(dir);
}

void remember_new(const std::string& domain, int ping, int kbps) {
    if (domain.empty() || !valid_host(domain) || is_gone(domain)) {
        return;
    }
    std::string dir;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        if (g.gone.count(domain) != 0) {
            return;
        }
        if (std::find(g.found.begin(), g.found.end(), domain) == g.found.end()) {
            g.found.push_back(domain);
        }
        auto& item = g.rank[domain];
        if (ping > 0) {
            item.ping = ping;
        }
        if (kbps > 0) {
            item.kbps = kbps;
        }
        item.at = now_ms();
        dir = g.cache_dir;
        g.search_live.store(static_cast<int>(g.found.size()));
    }
    save_internet(dir);
}

struct Net4 {
    uint32_t base = 0;
    uint32_t mask = 0;
};

struct Net6 {
    uint8_t base[16]{};
    int bits = 0;
};

bool parse_v4_cidr(const std::string& text, Net4* out) {
    unsigned a = 0;
    unsigned b = 0;
    unsigned c = 0;
    unsigned d = 0;
    unsigned bits = 0;
    if (std::sscanf(text.c_str(), "%u.%u.%u.%u/%u", &a, &b, &c, &d, &bits) != 5) {
        return false;
    }
    if (a > 255 || b > 255 || c > 255 || d > 255 || bits > 32) {
        return false;
    }
    uint32_t ip = (a << 24) | (b << 16) | (c << 8) | d;
    uint32_t mask = bits == 0 ? 0u : (0xFFFFFFFFu << (32 - bits));
    out->base = ip & mask;
    out->mask = mask;
    return true;
}

bool parse_v6_cidr(const std::string& text, Net6* out) {
    size_t slash = text.find('/');
    if (slash == std::string::npos || slash == 0) {
        return false;
    }
    int bits = std::atoi(text.c_str() + slash + 1);
    if (bits < 0 || bits > 128) {
        return false;
    }
    in6_addr ip{};
    if (inet_pton(AF_INET6, text.substr(0, slash).c_str(), &ip) != 1) {
        return false;
    }
    std::memcpy(out->base, ip.s6_addr, 16);
    out->bits = bits;
    return true;
}

void collect_cidrs(const std::string& body, std::vector<Net4>* v4, std::vector<Net6>* v6) {
    if (body.empty() || v4 == nullptr || v6 == nullptr) {
        return;
    }
    for (size_t i = 0; i < body.size(); ++i) {
        if (body[i] != '/') {
            continue;
        }
        size_t begin = i;
        while (begin > 0) {
            unsigned char c = static_cast<unsigned char>(body[begin - 1]);
            bool ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.';
            if (!ok) {
                break;
            }
            --begin;
        }
        size_t end = i + 1;
        while (end < body.size() && body[end] >= '0' && body[end] <= '9') {
            ++end;
        }
        if (begin == i || end == i + 1) {
            continue;
        }
        std::string token = body.substr(begin, end - begin);
        Net4 n4;
        if (parse_v4_cidr(token, &n4)) {
            v4->push_back(n4);
            continue;
        }
        Net6 n6;
        if (parse_v6_cidr(token, &n6)) {
            v6->push_back(n6);
        }
    }
}

bool v4_hit(uint32_t ip, const std::vector<Net4>& nets) {
    for (const Net4& net : nets) {
        if ((ip & net.mask) == net.base) {
            return true;
        }
    }
    return false;
}

bool v6_hit(const uint8_t ip[16], const std::vector<Net6>& nets) {
    for (const Net6& net : nets) {
        int full = net.bits / 8;
        int rem = net.bits % 8;
        if (std::memcmp(ip, net.base, static_cast<size_t>(full)) != 0) {
            continue;
        }
        if (rem == 0) {
            return true;
        }
        uint8_t mask = static_cast<uint8_t>(0xFFu << (8 - rem));
        if ((ip[full] & mask) == (net.base[full] & mask)) {
            return true;
        }
    }
    return false;
}

bool peer_on_cloudflare(int fd, const std::vector<Net4>& v4, const std::vector<Net6>& v6) {
    if (v4.empty() && v6.empty()) {
        return true;
    }
    sockaddr_storage ss{};
    socklen_t len = sizeof(ss);
    if (fd < 0 || ::getpeername(fd, reinterpret_cast<sockaddr*>(&ss), &len) != 0) {
        return true;
    }
    if (ss.ss_family == AF_INET) {
        auto* in = reinterpret_cast<sockaddr_in*>(&ss);
        return v4_hit(ntohl(in->sin_addr.s_addr), v4);
    }
    if (ss.ss_family == AF_INET6) {
        auto* in6 = reinterpret_cast<sockaddr_in6*>(&ss);
        return v6_hit(in6->sin6_addr.s6_addr, v6);
    }
    return true;
}

// Unencrypted MTProto request (req_pq_multi). A live Telegram DC always answers it,
// so a real round trip through the front proves the tunnel really works.
void build_ping_packet(uint8_t out[40]) {
    std::memset(out, 0, 40);
    int64_t seconds = static_cast<int64_t>(std::time(nullptr));
    uint64_t msg_id = (static_cast<uint64_t>(seconds) << 32)
            | (static_cast<uint64_t>(now_ms() & 0x3FFFFFFF) << 2);
    for (int i = 0; i < 8; ++i) {
        out[8 + i] = static_cast<uint8_t>((msg_id >> (8 * i)) & 0xFF);
    }
    uint32_t body_len = 20;
    for (int i = 0; i < 4; ++i) {
        out[16 + i] = static_cast<uint8_t>((body_len >> (8 * i)) & 0xFF);
    }
    uint32_t ctor = 0xbe7e8ef1u;
    for (int i = 0; i < 4; ++i) {
        out[20 + i] = static_cast<uint8_t>((ctor >> (8 * i)) & 0xFF);
    }
    random_bytes(out + 24, 16);
}

bool looks_like_mtproto_reply(const std::vector<uint8_t>& plain) {
    if (plain.size() < 28) {
        return false;
    }
    for (int i = 0; i < 8; ++i) {
        if (plain[i] != 0) {
            return false;
        }
    }
    uint32_t len = static_cast<uint32_t>(plain[16]) | (static_cast<uint32_t>(plain[17]) << 8)
            | (static_cast<uint32_t>(plain[18]) << 16) | (static_cast<uint32_t>(plain[19]) << 24);
    if (len < 4 || len > 1024) {
        return false;
    }
    return plain.size() >= 20u + len;
}

// Returns true when the chosen DC answered through this front. rtt_ms is the real
// round trip of the whole chain (tls -> websocket -> worker -> dc -> back).
bool probe_front(const std::string& base, int dc, int* rtt_ms, int* status_out) {
    if (rtt_ms) {
        *rtt_ms = 0;
    }
    if (status_out) {
        *status_out = 0;
    }
    uint8_t relay[64];
    Ctr tg_enc;
    Ctr tg_dec;
    uint8_t proto_tag[4] = {0xDD, 0xDD, 0xDD, 0xDD};
    build_relay(proto_tag, dc, false, relay, &tg_enc, &tg_dec);

    // Inner stream is Telegram transport framing (4 byte little-endian length).
    uint8_t plain[44];
    uint32_t body_len = 40;
    for (int i = 0; i < 4; ++i) {
        plain[i] = static_cast<uint8_t>((body_len >> (8 * i)) & 0xFF);
    }
    build_ping_packet(plain + 4);
    tg_enc.xor_buf(plain, sizeof(plain));

    std::string host = XS("kws") + std::to_string(dc) + XS(".") + base;
    int status = 0;
    int64_t started = now_ms();
    auto link = connect_host(host, &status, kConnectMs, kHandshakeMs, false);
    if (status_out) {
        *status_out = status;
    }
    if (!link) {
        return false;
    }
    if (!link->write_frame(0x2, relay, 64) || !link->write_frame(0x2, plain, sizeof(plain))) {
        return false;
    }
    int64_t deadline = now_ms() + kProbeBudgetMs;
    std::vector<uint8_t> acc;
    while (now_ms() < deadline && !link->dead) {
        pollfd pfd{};
        pfd.fd = link->fd();
        pfd.events = POLLIN;
        poll(&pfd, 1, 150);
        int spins = 0;
        while (spins < 8 && link->pump()) {
            ++spins;
        }
        std::vector<uint8_t> message;
        while (link->next_data(&message)) {
            tg_dec.xor_buf(message.data(), message.size());
            acc.insert(acc.end(), message.begin(), message.end());
        }
        while (acc.size() >= 4) {
            uint32_t frame_len = le32_at(acc.data());
            if (frame_len == 0 || frame_len > 4096) {
                break;
            }
            if (acc.size() < 4u + frame_len) {
                break;
            }
            std::vector<uint8_t> body(acc.begin() + 4, acc.begin() + 4 + static_cast<std::ptrdiff_t>(frame_len));
            acc.erase(acc.begin(), acc.begin() + 4 + static_cast<std::ptrdiff_t>(frame_len));
            if (looks_like_mtproto_reply(body)) {
                if (rtt_ms) {
                    int rtt = static_cast<int>(now_ms() - started);
                    *rtt_ms = rtt > 0 ? rtt : 1;
                }
                return true;
            }
        }
    }
    return false;
}

// Cheap secondary check: the tunnel opens and answers like a real Cloudflare worker.
// Used only to avoid calling a front dead when the deep probe is inconclusive.
bool front_reachable(const std::string& base, int dc, int* status_out) {
    std::string host = XS("kws") + std::to_string(dc) + XS(".") + base;
    int status = 0;
    auto link = connect_host(host, &status, kConnectMs, kHandshakeMs, false);
    if (status_out) {
        *status_out = status;
    }
    return link != nullptr;
}

bool device_online();
void probe_mark(const std::string& domain, bool alive, int rtt_ms);

// A front can route different DCs; use the DC the user actually talks to and
// one spare before calling the front dead.
bool probe_front_any(const std::string& base, int* rtt_ms) {
    int primary = g.last_dc.load();
    if (primary <= 0) {
        primary = 2;
    }
    int status = 0;
    if (probe_front(base, primary, rtt_ms, &status)) {
        return true;
    }
    if (!device_online()) {
        return false;
    }
    int spare = primary == 4 ? 2 : 4;
    return probe_front(base, spare, rtt_ms, &status);
}

void load_internet(const std::string& dir, std::vector<std::string>* out, std::map<std::string, Rank>* ranks, std::string* selected) {
    if (dir.empty()) {
        return;
    }
    FILE* domains = std::fopen((dir + XS("/cf-internet.txt")).c_str(), "r");
    if (domains) {
        char line[256];
        while (std::fgets(line, sizeof(line), domains)) {
            std::string host = lower_host(trim_host(line));
            if (host.size() > 4 && host.compare(host.size() - 4, 4, ".com") == 0) {
                host = normalize_cf_domain(host);
            }
            if (!valid_host(host)) {
                continue;
            }
            if (std::find(out->begin(), out->end(), host) == out->end()) {
                out->push_back(host);
            }
        }
        std::fclose(domains);
    }
    FILE* rank_file = std::fopen((dir + XS("/cf-rank.txt")).c_str(), "r");
    if (rank_file) {
        char line[256];
        while (std::fgets(line, sizeof(line), rank_file)) {
            char host[180];
            int ping = 0;
            int kbps = 0;
            int state = kFrontUnknown;
            int fields = std::sscanf(line, "%179s %d %d %d", host, &ping, &kbps, &state);
            if (fields >= 3) {
                std::string name = lower_host(host);
                if (valid_host(name)) {
                    // Cached numbers are hints only: state stays "unknown" until this session
                    // really reaches the front, so the list never shows a stale "alive".
                    Rank rank;
                    rank.ping = ping;
                    rank.kbps = kbps;
                    rank.state = kFrontUnknown;
                    rank.at = now_ms();
                    (*ranks)[name] = rank;
                }
            }
        }
        std::fclose(rank_file);
    }
    FILE* choice = std::fopen((dir + XS("/cf-choice.txt")).c_str(), "r");
    if (choice) {
        char line[256];
        if (std::fgets(line, sizeof(line), choice)) {
            std::string host = lower_host(trim_host(line));
            if (valid_host(host)) {
                *selected = host;
            }
        }
        std::fclose(choice);
    }
}

void rank_list() {
    std::vector<std::string> domains = copy_domains();
    for (const std::string& domain : domains) {
        if (is_gone(domain)) {
            continue;
        }
        int rtt = 0;
        probe_mark(domain, probe_front_any(domain, &rtt), rtt);
        usleep(150000);
    }
}

// Declared here on purpose: the sweep below uses the same waiting primitive and the
// same liveness counter, both of which are defined further down.
void health_sleep(int ms);
int live_fronts();

void refresh_known() {
    g.search_checked.store(0);
    std::vector<std::string> known;
    std::string dir;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        known = g.found;
        dir = g.cache_dir;
    }
    bool any_ok = false;
    std::vector<std::string> failed;
    for (const std::string& domain : known) {
        if (is_gone(domain)) {
            continue;
        }
        if (!g.running.load() || g.health_stop.load()) {
            break;
        }
        if (any_ok && live_fronts() > 0) {
            // One front already answers: no reason to burn the radio over the rest.
            break;
        }
        g.search_checked.store(g.search_checked.load() + 1);
        int rtt = 0;
        bool alive = probe_front_any(domain, &rtt);
        if (alive) {
            remember_new(domain, rtt, -1);
            any_ok = true;
        } else {
            failed.push_back(domain);
        }
        probe_mark(domain, alive, rtt);
        // A stop or a wake up request cuts the gap short instead of a fixed sleep.
        health_sleep(250);
    }
    if (any_ok) {
        forget_failed(failed);
    }
    save_internet(dir);
}

bool device_online() {
    static std::atomic<long long> cached_at{0};
    static std::atomic<int> cached{1};
    long long now = now_ms();
    if (now - cached_at.load() < 30000) {
        return cached.load() != 0;
    }
    int fd = tcp_connect(XS("1.1.1.1").c_str(), 443, 1200);
    bool ok = fd >= 0;
    if (fd >= 0) {
        ::close(fd);
    }
    cached.store(ok ? 1 : 0);
    cached_at.store(now);
    return ok;
}

// Живым считается фронт, который уже провёл реальный трафик в этой сессии.
// Срок давности здесь не важен: фронт остаётся живым, пока он не провалил
// настоящее соединение. Проверок «на всякий случай» нет — именно они рвали связь.
int live_fronts() {
    std::lock_guard<std::mutex> lock(g.mu);
    int count = 0;
    for (const auto& item : g.rank) {
        if (item.second.state == kFrontAlive) {
            ++count;
        }
    }
    return count;
}

void publish_ping() {
    std::lock_guard<std::mutex> lock(g.mu);
    int best = -1;
    for (const auto& item : g.rank) {
        if (item.second.state == kFrontAlive && item.second.ping > 0) {
            if (best < 0 || item.second.ping < best) {
                best = item.second.ping;
            }
        }
    }
    if (best > 0) {
        std::lock_guard<std::mutex> stats(traffic.mu);
        traffic.ping_ms = best;
    }
}

// One blocking wait instead of five wake ups per second. The pipe delivers stop and
// wake up requests immediately, so a long sleep costs nothing and heats nothing.
void health_sleep(int ms) {
    if (ms <= 0) {
        return;
    }
    while (true) {
        if (g.health_stop.load()) {
            return;
        }
        if (g.health_wake.exchange(0) != 0) {
            return;
        }
        int fd = g.wake_pipe[0];
        if (fd < 0) {
            usleep(static_cast<useconds_t>(ms) * 1000);
            return;
        }
        int slice = ms > 120000 ? 120000 : ms;
        pollfd pfd{};
        pfd.fd = fd;
        pfd.events = POLLIN;
        int pr = poll(&pfd, 1, slice);
        if (pr < 0) {
            if (errno == EINTR) {
                continue;
            }
            return;
        }
        if (pr == 0) {
            return;
        }
        uint8_t drain[64];
        while (::read(fd, drain, sizeof(drain)) > 0) {
        }
        if (g.health_stop.load()) {
            return;
        }
        if (g.health_wake.exchange(0) != 0) {
            return;
        }
        return;
    }
}

void health_cycle() {
    g.health_cycles.fetch_add(1);
    int live = live_fronts();
    g.live_count.store(live);
    // Фронты никто не опрашивает: обновление списка, пинги и скорости — только
    // по кнопке «Обновить». Здесь лишь честная сводка: сколько серверов реально
    // провели трафик и есть ли у телефона сеть вообще.
    if (!device_online()) {
        g.pool_state.store(3);
        return;
    }
    if (live > 0) {
        g.pool_state.store(1);
        note_pool_ok();
    } else {
        g.pool_state.store(2);
    }
    publish_ping();
}

int probe_dc() {
    int dc = g.last_dc.load();
    return dc > 0 ? dc : 2;
}

// Round trip failed: never blacklist a front that still opens its tunnel,
// it stays "unverified" instead of a fake "dead".
void probe_mark(const std::string& domain, bool alive, int rtt_ms) {
    if (alive) {
        note_probe(domain, true, rtt_ms);
        return;
    }
    if (!device_online()) {
        return;
    }
    int status = 0;
    if (front_reachable(domain, probe_dc(), &status)) {
        std::lock_guard<std::mutex> lock(g.mu);
        auto& item = g.rank[domain];
        item.check_at = now_ms();
        item.at = item.check_at;
        if (item.state != kFrontAlive) {
            item.state = kFrontUnknown;
            item.ping = -1;
            item.ping_at = 0;
        }
        return;
    }
    if (status == 429) {
        mark_429(domain);
        return;
    }
    note_probe(domain, false, 0);
}

void set_background_priority() {
    setpriority(PRIO_PROCESS, 0, 10);
}

void health_loop() {
    set_background_priority();
    while (!g.health_stop.load()) {
        if (g.running.load()) {
            health_cycle();
            if (g.ranks_dirty.exchange(false)) {
                std::string dir;
                {
                    std::lock_guard<std::mutex> lock(g.mu);
                    dir = g.cache_dir;
                }
                save_internet(dir);
                save_gone(dir);
            }
        }
        // Никакого автоматического опроса серверов в такте: дозор только
        // защитный, а связь держит сам туннель. Пока всё живо — один дешёвый
        // такт в минуту; если совсем ничего не подтверждено, чуть чаще.
        int wait_ms = 60000;
        if (!g.running.load()) {
            // Proxy is stopped: sleep until an explicit call lifts us.
            wait_ms = 300000;
        } else if (g.pool_state.load() == 3) {
            // No network at all: waking the radio cannot help, the system reports
            // every connectivity change by itself.
            wait_ms = 60000;
        } else if (live_fronts() == 0) {
            wait_ms = 20000;
        }
        health_sleep(wait_ms);
    }
}

}  // namespace

int proxy_start(const char* host, int port, const char* secret) {
    if (host == nullptr || secret == nullptr || port <= 0 || port > 65535) {
        return -2;
    }
    std::lock_guard<std::mutex> lock(g.mu);
    if (g.running.load()) {
        return -1;
    }
    ::signal(SIGPIPE, SIG_IGN);
    g.secret_hex = secret;
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        return -3;
    }
    int yes = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(static_cast<uint16_t>(port));
    if (inet_pton(AF_INET, host, &addr.sin_addr) != 1) {
        ::close(fd);
        return -3;
    }
    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0 || listen(fd, 128) != 0) {
        ::close(fd);
        return -3;
    }
    {
        std::lock_guard<std::mutex> stats(traffic.mu);
        traffic.up = 0;
        traffic.down = 0;
        traffic.ping_ms = -1;
    }
    g.listen_fd = fd;
    g.running.store(true);
    g.needs_restart.store(false);
    g.pool_state.store(0);
    g.pool_bad_at.store(0);
    if (!g.health_thread.joinable()) {
        ensure_wake_pipe();
        g.health_stop.store(false);
        g.health_cycles.store(0);
        poke_health();
        g.health_thread = std::thread(health_loop);
    }
    g.accept_thread = std::thread(accept_loop);
    return 0;
}

void proxy_stop() {
    std::thread to_join;
    std::thread health_join;
    int listen_fd = -1;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        if (!g.running.load() && g.listen_fd < 0 && !g.health_thread.joinable()) {
            return;
        }
        g.running.store(false);
        listen_fd = g.listen_fd;
        g.listen_fd = -1;
        for (int fd : g.clients) {
            ::shutdown(fd, SHUT_RDWR);
        }
        if (g.accept_thread.joinable()) {
            to_join = std::move(g.accept_thread);
        }
        g.health_stop.store(true);
        poke_health();
        if (g.health_thread.joinable()) {
            health_join = std::move(g.health_thread);
        }
    }
    if (listen_fd >= 0) {
        ::shutdown(listen_fd, SHUT_RDWR);
        ::close(listen_fd);
    }
    if (to_join.joinable()) {
        to_join.join();
    }
    if (health_join.joinable()) {
        health_join.join();
    }
}

void proxy_configure(int pool, const char* cache_dir, int cf_enabled, const char* user_domain) {
    std::vector<std::string> domains;
    std::string user = user_domain ? user_domain : "";
    std::string cache = cache_dir ? cache_dir : "";
    std::vector<std::string> found;
    std::map<std::string, Rank> ranks;
    std::set<std::string> gone;
    std::string selected;
    if (!user.empty()) {
        add_domain(&domains, user);
    } else {
        load_cached_domains(cache, &domains);
        for (const std::string& plain : plain_domains()) {
            if (std::find(domains.begin(), domains.end(), plain) == domains.end()) {
                domains.push_back(plain);
            }
        }
        for (const std::string& encoded : encoded_domains()) {
            add_domain(&domains, encoded);
        }
        load_internet(cache, &found, &ranks, &selected);
        load_gone(cache, &gone);
        found.erase(std::remove_if(found.begin(), found.end(), [&](const std::string& domain) {
            return gone.count(domain) != 0;
        }), found.end());
        if (gone.count(selected) != 0) {
            selected.clear();
        }
        for (const std::string& domain : domains) {
            if (gone.count(domain) != 0) {
                continue;
            }
            if (std::find(found.begin(), found.end(), domain) == found.end()) {
                found.push_back(domain);
            }
        }
    }
    std::lock_guard<std::mutex> lock(g.mu);
    g.pool_size = pool;
    g.cache_dir = cache;
    g.cf_enabled = cf_enabled != 0;
    g.domains = std::move(domains);
    if (user.empty() && !g.search_running.load()) {
        std::map<std::string, Rank> merged;
        for (const auto& item : ranks) {
            merged[item.first] = item.second;
        }
        for (const auto& item : g.rank) {
            Rank& slot = merged[item.first];
            if (item.second.state == kFrontAlive && item.second.ping_at > 0
                    && now_ms() - item.second.ping_at < 180000) {
                slot = item.second;
            } else if (slot.state != kFrontAlive) {
                slot.fails = item.second.fails;
                slot.kbps = item.second.kbps;
                if (slot.ping <= 0) {
                    slot.ping = item.second.ping;
                }
            }
        }
        g.found = std::move(found);
        g.rank = std::move(merged);
        g.gone = std::move(gone);
        g.selected = std::move(selected);
    }
    load_preferred(g.cache_dir);
}

int proxy_alive() {
    if (!g.running.load()) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(g.mu);
    return g.listen_fd >= 0 ? 1 : 0;
}

long long proxy_bytes_up() {
    std::lock_guard<std::mutex> lock(traffic.mu);
    return static_cast<long long>(traffic.up);
}

long long proxy_bytes_down() {
    std::lock_guard<std::mutex> lock(traffic.mu);
    return static_cast<long long>(traffic.down);
}

int proxy_ping_ms() {
    std::lock_guard<std::mutex> lock(traffic.mu);
    return traffic.ping_ms;
}

int proxy_search_start() {
    bool expected = false;
    if (!g.search_running.compare_exchange_strong(expected, true)) {
        return 1;
    }
    g.search_checked.store(0);
    std::thread([]() {
        rank_list();
        g.search_running.store(false);
    }).detach();
    return 0;
}

int proxy_refresh_start() {
    bool expected = false;
    if (!g.search_running.compare_exchange_strong(expected, true)) {
        return 1;
    }
    g.search_checked.store(0);
    std::thread([]() {
        refresh_known();
        g.search_running.store(false);
    }).detach();
    return 0;
}

int proxy_search_running() {
    return g.search_running.load() ? 1 : 0;
}

int proxy_search_checked() {
    return g.search_checked.load();
}

int copy_text(char* out, int out_len, const std::string& text) {
    if (out == nullptr || out_len <= 1) {
        return 0;
    }
    int n = static_cast<int>(text.size());
    if (n > out_len - 1) {
        n = out_len - 1;
    }
    if (n > 0) {
        std::memcpy(out, text.data(), static_cast<size_t>(n));
    }
    out[n] = '\0';
    return n;
}

std::string proxy_fronts_text() {
    std::vector<std::string> found;
    std::map<std::string, Rank> ranks;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        found = g.found;
        ranks = g.rank;
    }
    int64_t now = now_ms();
    std::stable_sort(found.begin(), found.end(), [](const std::string& a, const std::string& b) {
        return domain_score(a) < domain_score(b);
    });
    std::string text;
    for (const std::string& domain : found) {
        if (is_gone(domain)) {
            continue;
        }
        int ping = -1;
        int kbps = -1;
        int state = kFrontUnknown;
        auto it = ranks.find(domain);
        if (it != ranks.end()) {
            state = it->second.state;
            if (state == kFrontAlive && it->second.ping_at > 0 && now - it->second.ping_at < 180000) {
                ping = it->second.ping;
                kbps = it->second.kbps;
            } else if (state == kFrontDead) {
                ping = -1;
                kbps = -1;
            } else {
                ping = it->second.ping > 0 ? it->second.ping : -1;
                kbps = it->second.kbps;
            }
        }
        text += domain;
        text += "|";
        text += std::to_string(ping);
        text += "|";
        text += std::to_string(kbps);
        text += "|";
        text += std::to_string(state);
        text += "\n";
    }
    return text;
}

int proxy_copy_fronts(char* out, int out_len) {
    return copy_text(out, out_len, proxy_fronts_text());
}

int proxy_select(const char* domain) {
    std::string host = domain ? lower_host(trim_host(domain)) : "";
    if (!host.empty() && !valid_host(host)) {
        return -1;
    }
    std::string dir;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        bool known = host.empty();
        if (!known) {
            known = std::find(g.found.begin(), g.found.end(), host) != g.found.end();
        }
        if (!known) {
            return -1;
        }
        bool changed = host != g.selected;
        g.selected = host;
        dir = g.cache_dir;
        if (changed) {
            // A deliberate switch: let the sessions of the old front reconnect onto the new one.
            for (int fd : g.clients) {
                ::shutdown(fd, SHUT_RDWR);
            }
        }
    }
    save_internet(dir);
    return 0;
}

int proxy_copy_selected(char* out, int out_len) {
    std::string selected;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        selected = g.selected;
    }
    if (selected.empty()) {
        selected.clear();
    }
    return copy_text(out, out_len, selected);
}

int proxy_copy_fastest(char* out, int out_len) {
    std::string selected;
    std::vector<std::string> found;
    {
        std::lock_guard<std::mutex> lock(g.mu);
        selected = g.selected;
        found = g.found;
    }
    if (!selected.empty()) {
        return copy_text(out, out_len, selected);
    }
    std::string best;
    int best_score = 10000000;
    for (const std::string& domain : found) {
        int score = domain_score(domain);
        if (best.empty() || score < best_score) {
            best = domain;
            best_score = score;
        }
    }
    return copy_text(out, out_len, best);
}

int proxy_needs_restart() {
    return g.needs_restart.load() ? 1 : 0;
}

void proxy_clear_restart() {
    g.needs_restart.store(false);
}

// 0 = unknown, 1 = at least one live front, 2 = every front of the pool is dead,
// 3 = the phone has no network at all.
int proxy_heal() {
    g.dead_cycles.store(0);
    poke_health();
    return g.pool_state.load();
}

int proxy_live_fronts() {
    return live_fronts();
}

void proxy_network_changed() {
    long long now = now_ms();
    if (now - g.last_net_change.load() < 3000) {
        return;
    }
    g.last_net_change.store(now);
    g.dead_cycles.store(0);
    {
        std::lock_guard<std::mutex> lock(g.mu);
        for (auto& item : g.rank) {
            item.second.state = kFrontUnknown;
            item.second.ping = -1;
            item.second.fails = 0;
            item.second.ping_at = 0;
            item.second.check_at = 0;
        }
        g.cooldown.clear();
        for (int fd : g.clients) {
            ::shutdown(fd, SHUT_RDWR);
        }
    }
    g.pool_state.store(0);
    g.pool_bad_at.store(0);
    poke_health();
}
