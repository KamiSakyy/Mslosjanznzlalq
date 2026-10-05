#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

struct Ctr {
    uint8_t round_key[240];
    uint8_t counter[16];
    uint8_t stream[16];
    size_t offset;
    uint64_t processed;

    void init(const uint8_t key[32], const uint8_t iv[16]);
    void xor_buf(uint8_t* data, size_t len);
};

void sha256(const uint8_t* data, size_t len, uint8_t out[32]);
void sha256_2(const uint8_t* a, size_t a_len, const uint8_t* b, size_t b_len, uint8_t out[32]);
void random_bytes(uint8_t* out, size_t len);
bool aes256_encrypt_block(const uint8_t key[32], const uint8_t block[16], uint8_t out[16]);

std::vector<uint8_t> hex_decode(const std::string& hex);
std::string b64_encode(const uint8_t* data, size_t len);
std::string decode_cf_domain(const std::string& encoded);
std::string normalize_cf_domain(const std::string& raw);

struct Handshake {
    bool ok;
    uint32_t proto;
    int dc;
    bool media;
    uint8_t proto_tag[4];
    Ctr client_dec;
    Ctr client_enc;
};

bool read_handshake(const uint8_t packet[64], const uint8_t* secret, size_t secret_len, Handshake* out);
void build_relay(const uint8_t proto_tag[4], int dc, bool media, uint8_t relay[64], Ctr* telegram_enc, Ctr* telegram_dec);
bool looks_like_http(const uint8_t* data, size_t len);
