#include "crypto.h"

#include "obf.h"

#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <unistd.h>

namespace {

const uint8_t kSbox[256] = {
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16};

const uint8_t kRcon[15] = {0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36, 0x6c, 0xd8, 0xab, 0x4d};

const uint32_t kSha[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

uint8_t xtime(uint8_t value) {
    return static_cast<uint8_t>((value << 1) ^ ((value & 0x80) ? 0x1b : 0));
}

void expand_key(const uint8_t key[32], uint8_t round_key[240]) {
    std::memcpy(round_key, key, 32);
    int generated = 32;
    int rcon_index = 1;
    while (generated < 240) {
        uint8_t temp[4] = {
            round_key[generated - 4],
            round_key[generated - 3],
            round_key[generated - 2],
            round_key[generated - 1]};
        if (generated % 32 == 0) {
            uint8_t first = temp[0];
            temp[0] = kSbox[temp[1]] ^ kRcon[rcon_index++];
            temp[1] = kSbox[temp[2]];
            temp[2] = kSbox[temp[3]];
            temp[3] = kSbox[first];
        } else if (generated % 32 == 16) {
            temp[0] = kSbox[temp[0]];
            temp[1] = kSbox[temp[1]];
            temp[2] = kSbox[temp[2]];
            temp[3] = kSbox[temp[3]];
        }
        for (int i = 0; i < 4; ++i) {
            round_key[generated] = round_key[generated - 32] ^ temp[i];
            ++generated;
        }
    }
}

void add_round_key(uint8_t state[16], const uint8_t* round_key) {
    for (int i = 0; i < 16; ++i) {
        state[i] ^= round_key[i];
    }
}

void sub_bytes(uint8_t state[16]) {
    for (int i = 0; i < 16; ++i) {
        state[i] = kSbox[state[i]];
    }
}

void shift_rows(uint8_t state[16]) {
    uint8_t temp = state[1];
    state[1] = state[5];
    state[5] = state[9];
    state[9] = state[13];
    state[13] = temp;

    temp = state[2];
    state[2] = state[10];
    state[10] = temp;
    temp = state[6];
    state[6] = state[14];
    state[14] = temp;

    temp = state[15];
    state[15] = state[11];
    state[11] = state[7];
    state[7] = state[3];
    state[3] = temp;
}

void mix_columns(uint8_t state[16]) {
    for (int column = 0; column < 4; ++column) {
        int i = column * 4;
        uint8_t a0 = state[i];
        uint8_t a1 = state[i + 1];
        uint8_t a2 = state[i + 2];
        uint8_t a3 = state[i + 3];
        state[i] = xtime(a0) ^ (xtime(a1) ^ a1) ^ a2 ^ a3;
        state[i + 1] = a0 ^ xtime(a1) ^ (xtime(a2) ^ a2) ^ a3;
        state[i + 2] = a0 ^ a1 ^ xtime(a2) ^ (xtime(a3) ^ a3);
        state[i + 3] = (xtime(a0) ^ a0) ^ a1 ^ a2 ^ xtime(a3);
    }
}

void encrypt_with_round_key(const uint8_t round_key[240], const uint8_t block[16], uint8_t out[16]) {
    uint8_t state[16];
    std::memcpy(state, block, 16);
    add_round_key(state, round_key);
    for (int round = 1; round < 14; ++round) {
        sub_bytes(state);
        shift_rows(state);
        mix_columns(state);
        add_round_key(state, round_key + round * 16);
    }
    sub_bytes(state);
    shift_rows(state);
    add_round_key(state, round_key + 224);
    std::memcpy(out, state, 16);
}

void increment_counter(uint8_t counter[16]) {
    for (int i = 15; i >= 8; --i) {
        if (++counter[i] != 0) {
            break;
        }
    }
}

uint32_t rotr(uint32_t value, int bits) {
    return (value >> bits) | (value << (32 - bits));
}

uint32_t load_be(const uint8_t* data) {
    return (uint32_t)data[0] << 24 | (uint32_t)data[1] << 16 | (uint32_t)data[2] << 8 | data[3];
}

void store_be(uint8_t* out, uint32_t value) {
    out[0] = static_cast<uint8_t>(value >> 24);
    out[1] = static_cast<uint8_t>(value >> 16);
    out[2] = static_cast<uint8_t>(value >> 8);
    out[3] = static_cast<uint8_t>(value);
}

void sha256_block(uint32_t state[8], const uint8_t block[64]) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = load_be(block + i * 4);
    }
    for (int i = 16; i < 64; ++i) {
        uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
        uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
    for (int i = 0; i < 64; ++i) {
        uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        uint32_t ch = (e & f) ^ (~e & g);
        uint32_t temp1 = h + s1 + ch + kSha[i] + w[i];
        uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = s0 + maj;
        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
    state[4] += e;
    state[5] += f;
    state[6] += g;
    state[7] += h;
}

uint32_t read_le32(const uint8_t* data) {
    return (uint32_t)data[0] | ((uint32_t)data[1] << 8) | ((uint32_t)data[2] << 16) | ((uint32_t)data[3] << 24);
}

uint16_t read_le16(const uint8_t* data) {
    return (uint16_t)(data[0] | (data[1] << 8));
}

void write_le16(uint8_t* data, uint16_t value) {
    data[0] = static_cast<uint8_t>(value);
    data[1] = static_cast<uint8_t>(value >> 8);
}

bool valid_proto(uint32_t proto) {
    return proto == 0xEFEFEFEFu || proto == 0xEEEEEEEEu || proto == 0xDDDDDDDDu;
}

bool bad_relay_prefix(const uint8_t relay[64]) {
    if (relay[0] == 0xEF) {
        return true;
    }
    static const std::string kHead = XS("HEAD");
    static const std::string kPost = XS("POST");
    static const std::string kGet = XS("GET ");
    if (std::memcmp(relay, kHead.c_str(), 4) == 0 || std::memcmp(relay, kPost.c_str(), 4) == 0
            || std::memcmp(relay, kGet.c_str(), 4) == 0) {
        return true;
    }
    if (relay[0] == 0xEE && relay[1] == 0xEE && relay[2] == 0xEE && relay[3] == 0xEE) {
        return true;
    }
    if (relay[0] == 0xDD && relay[1] == 0xDD && relay[2] == 0xDD && relay[3] == 0xDD) {
        return true;
    }
    if (relay[0] == 0x16 && relay[1] == 0x03 && relay[2] == 0x01 && relay[3] == 0x02) {
        return true;
    }
    return relay[4] == 0 && relay[5] == 0 && relay[6] == 0 && relay[7] == 0;
}

std::string trim_copy(const std::string& value) {
    size_t begin = 0;
    while (begin < value.size() && (value[begin] == ' ' || value[begin] == '\t' || value[begin] == '\r' || value[begin] == '\n')) {
        ++begin;
    }
    size_t end = value.size();
    while (end > begin && (value[end - 1] == ' ' || value[end - 1] == '\t' || value[end - 1] == '\r' || value[end - 1] == '\n')) {
        --end;
    }
    return value.substr(begin, end - begin);
}

}  // namespace

void Ctr::init(const uint8_t key[32], const uint8_t iv[16]) {
    expand_key(key, round_key);
    std::memcpy(counter, iv, 16);
    std::memset(stream, 0, sizeof(stream));
    offset = 0;
    processed = 0;
}

void Ctr::xor_buf(uint8_t* data, size_t len) {
    size_t index = 0;
    while (index < len) {
        if (offset == 0) {
            encrypt_with_round_key(round_key, counter, stream);
            increment_counter(counter);
        }
        size_t chunk = 16 - offset;
        if (chunk > len - index) {
            chunk = len - index;
        }
        for (size_t i = 0; i < chunk; ++i) {
            data[index + i] ^= stream[offset + i];
        }
        offset += chunk;
        if (offset == 16) {
            offset = 0;
        }
        index += chunk;
    }
    processed += len;
}

bool aes256_encrypt_block(const uint8_t key[32], const uint8_t block[16], uint8_t out[16]) {
    uint8_t round_key[240];
    expand_key(key, round_key);
    encrypt_with_round_key(round_key, block, out);
    return true;
}

void sha256(const uint8_t* data, size_t len, uint8_t out[32]) {
    uint32_t state[8] = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    uint8_t block[64];
    size_t offset = 0;
    uint64_t bit_len = (uint64_t)len * 8;
    while (offset + 64 <= len) {
        sha256_block(state, data + offset);
        offset += 64;
    }
    size_t remain = len - offset;
    std::memset(block, 0, sizeof(block));
    if (remain) {
        std::memcpy(block, data + offset, remain);
    }
    block[remain] = 0x80;
    if (remain >= 56) {
        sha256_block(state, block);
        std::memset(block, 0, sizeof(block));
    }
    for (int i = 0; i < 8; ++i) {
        block[63 - i] = static_cast<uint8_t>(bit_len >> (i * 8));
    }
    sha256_block(state, block);
    for (int i = 0; i < 8; ++i) {
        store_be(out + i * 4, state[i]);
    }
}

void sha256_2(const uint8_t* a, size_t a_len, const uint8_t* b, size_t b_len, uint8_t out[32]) {
    std::vector<uint8_t> joined(a_len + b_len);
    if (a_len) {
        std::memcpy(joined.data(), a, a_len);
    }
    if (b_len) {
        std::memcpy(joined.data() + a_len, b, b_len);
    }
    sha256(joined.data(), joined.size(), out);
}

void random_bytes(uint8_t* out, size_t len) {
    size_t got = 0;
    int fd = ::open(XS("/dev/urandom").c_str(), O_RDONLY);
    if (fd >= 0) {
        while (got < len) {
            ssize_t n = ::read(fd, out + got, len - got);
            if (n <= 0) {
                break;
            }
            got += static_cast<size_t>(n);
        }
        ::close(fd);
    }
    if (got < len) {
        uint64_t mix = (uint64_t)std::time(nullptr) ^ (uint64_t)(uintptr_t)out ^ (uint64_t)len;
        for (; got < len; ++got) {
            mix = mix * 6364136223846793005ULL + 1;
            out[got] = static_cast<uint8_t>(mix >> 33);
        }
    }
}

std::vector<uint8_t> hex_decode(const std::string& hex) {
    std::vector<uint8_t> out;
    if (hex.size() % 2 != 0) {
        return out;
    }
    out.reserve(hex.size() / 2);
    auto nibble = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    for (size_t i = 0; i < hex.size(); i += 2) {
        int hi = nibble(hex[i]);
        int lo = nibble(hex[i + 1]);
        if (hi < 0 || lo < 0) {
            out.clear();
            return out;
        }
        out.push_back(static_cast<uint8_t>((hi << 4) | lo));
    }
    return out;
}

std::string b64_encode(const uint8_t* data, size_t len) {
    static const char table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    out.reserve(((len + 2) / 3) * 4);
    for (size_t i = 0; i < len; i += 3) {
        uint32_t value = (uint32_t)data[i] << 16;
        if (i + 1 < len) value |= (uint32_t)data[i + 1] << 8;
        if (i + 2 < len) value |= data[i + 2];
        out.push_back(table[(value >> 18) & 63]);
        out.push_back(table[(value >> 12) & 63]);
        out.push_back(i + 1 < len ? table[(value >> 6) & 63] : '=');
        out.push_back(i + 2 < len ? table[value & 63] : '=');
    }
    return out;
}

std::string decode_cf_domain(const std::string& encoded) {
    if (encoded.size() < 4 || encoded.compare(encoded.size() - 4, 4, XS(".com")) != 0) {
        return encoded;
    }
    std::string prefix = encoded.substr(0, encoded.size() - 4);
    int letters = 0;
    for (unsigned char c : prefix) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            ++letters;
        }
    }
    int shift = letters % 26;
    std::string out;
    out.reserve(prefix.size() + 6);
    for (unsigned char c : prefix) {
        if (c >= 'a' && c <= 'z') {
            out.push_back(static_cast<char>('a' + (c - 'a' - shift + 26) % 26));
        } else if (c >= 'A' && c <= 'Z') {
            out.push_back(static_cast<char>('A' + (c - 'A' - shift + 26) % 26));
        } else {
            out.push_back(static_cast<char>(c));
        }
    }
    out += XS(".co.uk");
    return out;
}

std::string normalize_cf_domain(const std::string& raw) {
    std::string decoded = decode_cf_domain(trim_copy(raw));
    for (char& c : decoded) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    while (!decoded.empty() && decoded.back() == '.') {
        decoded.pop_back();
    }
    if (decoded.size() < 6 || decoded.compare(decoded.size() - 6, 6, XS(".co.uk")) != 0) {
        return "";
    }
    return decoded;
}

bool looks_like_http(const uint8_t* data, size_t len) {
    if (len < 4) {
        return false;
    }
    static const std::string kPost = XS("POST");
    static const std::string kHead = XS("HEAD");
    static const std::string kGet = XS("GET");
    static const std::string kOptions = XS("OPTIONS");
    if (std::memcmp(data, kPost.c_str(), 4) == 0 || std::memcmp(data, kHead.c_str(), 4) == 0) {
        return true;
    }
    if (std::memcmp(data, kGet.c_str(), 3) == 0) {
        return true;
    }
    return len >= 7 && std::memcmp(data, kOptions.c_str(), 7) == 0;
}

bool read_handshake(const uint8_t packet[64], const uint8_t* secret, size_t secret_len, Handshake* out) {
    out->ok = false;
    uint8_t key[32];
    sha256_2(packet + 8, 32, secret, secret_len, key);
    out->client_dec.init(key, packet + 40);
    uint8_t decrypted[64];
    std::memcpy(decrypted, packet, 64);
    out->client_dec.xor_buf(decrypted, 64);
    out->proto = read_le32(decrypted + 56);
    if (!valid_proto(out->proto)) {
        return false;
    }
    int16_t dc_raw = static_cast<int16_t>(read_le16(decrypted + 60));
    out->media = dc_raw < 0;
    out->dc = dc_raw < 0 ? -dc_raw : dc_raw;
    std::memcpy(out->proto_tag, decrypted + 56, 4);

    uint8_t reversed[48];
    for (int i = 0; i < 48; ++i) {
        reversed[i] = packet[8 + 47 - i];
    }
    sha256_2(reversed, 32, secret, secret_len, key);
    out->client_enc.init(key, reversed + 32);
    out->ok = true;
    return true;
}

void build_relay(const uint8_t proto_tag[4], int dc, bool media, uint8_t relay[64], Ctr* telegram_enc, Ctr* telegram_dec) {
    do {
        random_bytes(relay, 64);
    } while (bad_relay_prefix(relay));

    telegram_enc->init(relay + 8, relay + 40);
    uint8_t reversed[48];
    for (int i = 0; i < 48; ++i) {
        reversed[i] = relay[8 + 47 - i];
    }
    telegram_dec->init(reversed, reversed + 32);

    uint8_t dc_bytes[2];
    int dc_index = media ? -dc : dc;
    write_le16(dc_bytes, static_cast<uint16_t>(dc_index));
    uint8_t tail[8];
    std::memcpy(tail, proto_tag, 4);
    std::memcpy(tail + 4, dc_bytes, 2);
    random_bytes(tail + 6, 2);

    uint8_t encrypted[64];
    std::memcpy(encrypted, relay, 64);
    telegram_enc->xor_buf(encrypted, 64);
    for (int i = 0; i < 8; ++i) {
        uint8_t keystream = encrypted[56 + i] ^ relay[56 + i];
        relay[56 + i] = tail[i] ^ keystream;
    }
}
