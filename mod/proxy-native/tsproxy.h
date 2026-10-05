#pragma once

#include <string>

int proxy_start(const char* host, int port, const char* secret);
void proxy_stop();
void proxy_configure(int pool, const char* cache_dir, int cf_enabled, const char* user_domain);
int proxy_alive();
long long proxy_bytes_up();
long long proxy_bytes_down();
int proxy_ping_ms();
int proxy_search_start();
int proxy_refresh_start();
int proxy_search_running();
int proxy_search_checked();
int proxy_copy_fronts(char* out, int out_len);
std::string proxy_fronts_text();
int proxy_select(const char* domain);
int proxy_copy_selected(char* out, int out_len);
int proxy_copy_fastest(char* out, int out_len);
int proxy_needs_restart();
void proxy_clear_restart();
int proxy_heal();
int proxy_live_fronts();
void proxy_network_changed();
