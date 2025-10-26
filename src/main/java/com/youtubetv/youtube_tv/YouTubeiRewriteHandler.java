package com.youtubetv.youtube_tv;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.json.JSONObject;

/**
 * Proxies YouTubei endpoints and scrubs ad-related fields from JSON responses.
 */
public class YouTubeiRewriteHandler implements CefResourceHandler {
    private final String userAgent;
    private byte[] bodyBytes;
    private int offset = 0;
    private int statusCode = 200;
    private String statusText = "OK";
    private final Map<String, String> responseHeaders = new HashMap<>();

    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(15).toMillis();
    private static final int READ_TIMEOUT_MS = (int) Duration.ofSeconds(30).toMillis();

    public YouTubeiRewriteHandler(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        try {
            URI uri = URI.create(request.getURL());
            String method = request.getMethod();

            // Copy headers from the incoming request
            Map<String, String> hmap = new HashMap<>();
            try { request.getHeaderMap(hmap); } catch (Throwable ignored) {}

            // Assemble headers to forward
            Map<String, String> outHeaders = new HashMap<>();
            if (userAgent != null && !userAgent.isEmpty()) {
                outHeaders.put("User-Agent", userAgent);
            }
            for (Map.Entry<String, String> e : hmap.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null || v == null) continue;
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.equals("user-agent") || kl.equals("content-length") || kl.equals("accept-encoding")) continue;
                outHeaders.put(k, v);
            }
            // Prefer identity to avoid handling compression, but be resilient if server compresses anyway
            outHeaders.put("Accept-Encoding", "identity");

            byte[] postBytes = null;
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                postBytes = collectPostDataBytes(request.getPostData());
                if (postBytes == null) postBytes = new byte[0];
            }

            SimpleResponse res = doHttp(uri.toURL(), method, outHeaders, postBytes);
            statusCode = res.status;
            statusText = (statusCode == 200 ? "OK" : Integer.toString(statusCode));

            // Copy response headers
            for (Map.Entry<String, String> e : res.headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                responseHeaders.put(e.getKey(), e.getValue());
            }

            byte[] raw = res.body;
            String contentEncoding = headerValue("content-encoding");
            if (contentEncoding != null && contentEncoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                try {
                    raw = ungzip(raw);
                    responseHeaders.remove("Content-Encoding");
                } catch (Throwable ignored) { }
            }

            String ctype = headerValue("content-type");
            boolean isJson = ctype != null && ctype.toLowerCase(Locale.ROOT).contains("application/json");

            // Try to scrub JSON even when Content-Type isn't correctly set: many endpoints return
            // JSON-like payloads with non-standard types or XSSI prefixes. We'll attempt to locate
            // the first JSON slice and parse it; if we successfully prune ad fields we replace the
            // response body and adjust headers.
            boolean didScrub = false;
            if (raw != null) {
                try {
                    String txt = new String(raw, StandardCharsets.UTF_8);
                    String slice = firstJsonSlice(txt);
                    if (slice != null && !slice.isEmpty()) {
                        try {
                            // Attempt to parse a JSONObject and prune. If parsing fails, we fall back.
                            org.json.JSONObject root = new org.json.JSONObject(slice);
                            int removed = prune(root);
                            if (removed > 0) {
                                String cleaned = root.toString();
                                bodyBytes = cleaned.getBytes(StandardCharsets.UTF_8);
                                responseHeaders.put("Content-Type", "application/json; charset=UTF-8");
                                responseHeaders.put("Content-Length", Integer.toString(bodyBytes.length));
                                didScrub = true;
                            }
                        } catch (Throwable parseEx) {
                            // not a top-level JSONObject; ignore and fall back to ctype-based handling
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (!didScrub) {
                if (isJson && raw != null) {
                    String txt = new String(raw, StandardCharsets.UTF_8);
                    String cleaned = scrubJson(txt);
                    bodyBytes = cleaned.getBytes(StandardCharsets.UTF_8);
                    responseHeaders.put("Content-Type", "application/json; charset=UTF-8");
                    responseHeaders.put("Content-Length", Integer.toString(bodyBytes.length));
                } else {
                    bodyBytes = raw == null ? new byte[0] : raw;
                    responseHeaders.put("Content-Length", Integer.toString(bodyBytes.length));
                }
            }

            try { System.out.println("[ADBLOCK] youtubei rewrite: url=" + uri.toString() + " ctype=" + (ctype==null?"(none)":ctype) + " pruned=" + (didScrub?"1+":"0")); } catch (Throwable ignored) {}

            callback.Continue();
            return true;
        } catch (Throwable t) {
            // Fail open: don't break playback, return original error status and empty body
            bodyBytes = new byte[0];
            statusCode = 200;
            statusText = "OK";
            responseHeaders.clear();
            responseHeaders.put("Content-Type", "application/json; charset=UTF-8");
            responseHeaders.put("Content-Length", "0");
            try { callback.Continue(); } catch (Throwable ignored) {}
            return true;
        }
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setStatus(statusCode);
        response.setStatusText(statusText);
        String mime = responseHeaders.getOrDefault("Content-Type", "application/json; charset=UTF-8");
        response.setMimeType(mime);
        for (Map.Entry<String, String> e : responseHeaders.entrySet()) {
            try { response.setHeaderByName(e.getKey(), e.getValue(), true); } catch (Throwable ignored) {}
        }
        responseLength.set(bodyBytes == null ? 0 : bodyBytes.length);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        if (bodyBytes == null) {
            bytesRead.set(0);
            return false;
        }
        int remaining = bodyBytes.length - offset;
        if (remaining <= 0) {
            bytesRead.set(0);
            return false;
        }
        int toCopy = Math.min(bytesToRead, remaining);
        System.arraycopy(bodyBytes, offset, dataOut, 0, toCopy);
        offset += toCopy;
        bytesRead.set(toCopy);
        return true;
    }

    @Override
    public void cancel() {
        // nothing to cancel in this simple implementation
    }

    private static byte[] collectPostDataBytes(CefPostData post) {
        if (post == null) return null;
        try {
            java.util.Vector<CefPostDataElement> els = new java.util.Vector<>();
            post.getElements(els);
            if (els.isEmpty()) return null;
            int total = 0;
            List<byte[]> chunks = new ArrayList<>();
            for (CefPostDataElement el : els) {
                try {
                    int size = el.getBytesCount();
                    if (size <= 0) continue;
                    byte[] arr = new byte[size];
                    int read = el.getBytes(size, arr);
                    if (read > 0) {
                        byte[] part = arr;
                        if (read != arr.length) {
                            part = new byte[read];
                            System.arraycopy(arr, 0, part, 0, read);
                        }
                        chunks.add(part);
                        total += read;
                    }
                } catch (Throwable ignored) {}
            }
            if (total == 0) return null;
            byte[] out = new byte[total];
            int pos = 0;
            for (byte[] c : chunks) { System.arraycopy(c, 0, out, pos, c.length); pos += c.length; }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private String headerValue(String keyLower) {
        for (Map.Entry<String, String> e : responseHeaders.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(keyLower)) return e.getValue();
        }
        return null;
    }

    private static byte[] ungzip(byte[] in) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(in))) {
            byte[] buf = new byte[8192];
            List<byte[]> parts = new ArrayList<>();
            int total = 0;
            int r;
            while ((r = gis.read(buf)) != -1) {
                byte[] p = new byte[r];
                System.arraycopy(buf, 0, p, 0, r);
                parts.add(p);
                total += r;
            }
            byte[] out = new byte[total];
            int pos = 0;
            for (byte[] p : parts) { System.arraycopy(p, 0, out, pos, p.length); pos += p.length; }
            return out;
        }
    }

    private static String firstJsonSlice(String t) {
        if (t == null) return null;
        int i1 = t.indexOf('{');
        int i2 = t.indexOf('[');
        int i;
        if (i1 == -1) i = i2; else if (i2 == -1) i = i1; else i = Math.min(i1, i2);
        if (i > 0) return t.substring(i);
        return t;
    }

    private static String scrubJson(String txt) {
        try {
            String s = firstJsonSlice(txt);
            if (s == null || s.isEmpty()) return txt;
            JSONObject root = new JSONObject(s);
            int removed = prune(root);
            try { System.out.println("[ADBLOCK] youtubei rewrite pruned fields: " + removed); } catch (Throwable ignored) {}
            return root.toString();
        } catch (Throwable t) {
            return txt; // fail-open
        }
    }

    private static int prune(JSONObject obj) {
        int removed = 0;
        try {
            // Top-level fields
            String[] killKeys = new String[] {
                "adPlacements", "adBreaks", "adSlot", "adSlots", "adSafetyReason", "adTag",
                "adParams", "adClient", "adLoggingData", "adEngagement", "adFormat",
                "adPlacementsVmap", "adBreakServiceParams", "adSignals", "adSignalsInfo",
                "adDisplayComponent", "adPlaybackContext", "adSlotsData"
            };
            for (String k : killKeys) {
                if (obj.has(k)) { obj.remove(k); removed++; }
            }
            if (obj.has("ads")) { obj.put("ads", new org.json.JSONArray()); removed++; }
            if (obj.has("playerAds")) { obj.put("playerAds", new org.json.JSONArray()); removed++; }
            if (obj.has("playerResponse") && obj.get("playerResponse") instanceof JSONObject) {
                removed += prune(obj.getJSONObject("playerResponse"));
            }
            if (obj.has("playerConfig") && obj.get("playerConfig") instanceof JSONObject) {
                removed += prune(obj.getJSONObject("playerConfig"));
            }
            if (obj.has("streamingData") && obj.get("streamingData") instanceof JSONObject) {
                try { obj.getJSONObject("streamingData").put("isAd", false); } catch (Throwable ignored) {}
            }
            if (obj.has("playbackTracking")) { obj.remove("playbackTracking"); removed++; }
            if (obj.has("responseContext") && obj.get("responseContext") instanceof JSONObject) {
                JSONObject rc = obj.getJSONObject("responseContext");
                if (rc.has("adSlots")) { rc.put("adSlots", new org.json.JSONArray()); removed++; }
            }
            if (obj.has("playabilityStatus") && obj.get("playabilityStatus") instanceof JSONObject) {
                JSONObject ps = obj.getJSONObject("playabilityStatus");
                if (ps.has("miniplayer")) { JSONObject mini = new JSONObject(); mini.put("miniplayerRenderer", new JSONObject()); ps.put("miniplayer", mini); }
            }
            if (obj.has("adSignals")) { obj.put("adSignals", new JSONObject()); removed++; }

            // Recurse into nested objects and arrays
            for (String key : obj.keySet()) {
                try {
                    Object v = obj.get(key);
                    if (v instanceof JSONObject) {
                        removed += prune((JSONObject) v);
                    } else if (v instanceof org.json.JSONArray) {
                        org.json.JSONArray arr = (org.json.JSONArray) v;
                        for (int i = 0; i < arr.length(); i++) {
                            try {
                                Object el = arr.get(i);
                                if (el instanceof JSONObject) {
                                    removed += prune((JSONObject) el);
                                } else if (el instanceof org.json.JSONArray) {
                                    // Nested arrays
                                    removed += pruneArray((org.json.JSONArray) el);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return removed;
    }

    private static int pruneArray(org.json.JSONArray arr) {
        int removed = 0;
        for (int i = 0; i < arr.length(); i++) {
            try {
                Object el = arr.get(i);
                if (el instanceof JSONObject) {
                    removed += prune((JSONObject) el);
                } else if (el instanceof org.json.JSONArray) {
                    removed += pruneArray((org.json.JSONArray) el);
                }
            } catch (Throwable ignored) {}
        }
        return removed;
    }

    // Simple HTTP response container compatible with Java 8
    private static class SimpleResponse {
        final int status;
        final Map<String, String> headers;
        final byte[] body;
        SimpleResponse(int status, Map<String, String> headers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }
    }

    // Java 8 compatible HTTP request using HttpURLConnection
    private static class Http8Compat {
        static SimpleResponse request(URL url, String method, Map<String, String> headers, byte[] body,
                                      int connectTimeoutMs, int readTimeoutMs) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            String m = method == null ? "GET" : method.toUpperCase(java.util.Locale.ROOT);
            try {
                conn.setRequestMethod(m);
            } catch (java.net.ProtocolException pe) {
                // Fallback for unsupported verbs (e.g., PATCH on some JDK8): use POST
                conn.setRequestMethod("POST");
                if (!"POST".equals(m)) {
                    conn.setRequestProperty("X-HTTP-Method-Override", m);
                }
            }
            conn.setInstanceFollowRedirects(true);
            // Set headers
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        conn.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
            }

            // Write body when applicable
            if (body != null && ("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m))) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            }

            int code;
            byte[] respBytes;
            Map<String, String> respHeaders = new HashMap<>();
            try {
                code = conn.getResponseCode();
            } catch (IOException ioe) {
                // Still try to consume error stream to obtain headers/body
                code = conn.getResponseCode();
            }

            // Headers
            Map<String, java.util.List<String>> hf = conn.getHeaderFields();
            if (hf != null) {
                for (Map.Entry<String, java.util.List<String>> e : hf.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
                    respHeaders.put(e.getKey(), String.join(", ", e.getValue()));
                }
            }

            // Body
            try (java.io.InputStream is = (code >= 400 ? conn.getErrorStream() : conn.getInputStream())) {
                if (is == null) {
                    respBytes = new byte[0];
                } else {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(8192);
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    respBytes = baos.toByteArray();
                }
            }
            conn.disconnect();
            return new SimpleResponse(code, respHeaders, respBytes);
        }
    }

    // Helper used above
    private static SimpleResponse doHttp(URL url, String method, Map<String, String> headers, byte[] body) throws IOException {
        return Http8Compat.request(url, method, headers, body, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }
}
