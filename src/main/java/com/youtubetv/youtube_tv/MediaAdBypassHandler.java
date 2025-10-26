package com.youtubetv.youtube_tv;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/**
 * Returns an immediate 204 No Content for ad media requests (initplayback/videoplayback
 * with explicit ad flags) to try to skip pre-roll without breaking main content.
 */
public class MediaAdBypassHandler implements CefResourceHandler {

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        try {
            System.out.println("[ADBLOCK] bypassing ad media: " + request.getURL());
        } catch (Throwable ignored) {}
        try { callback.Continue(); } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setStatus(204);
        response.setStatusText("No Content");
        response.setMimeType("text/plain");
        response.setHeaderByName("Content-Length", "0", true);
        // Allow cross-origin fetch/XHR to succeed without CORS errors
        try {
            response.setHeaderByName("Access-Control-Allow-Origin", "*", true);
            response.setHeaderByName("Access-Control-Allow-Methods", "GET,HEAD,OPTIONS", true);
            response.setHeaderByName("Access-Control-Allow-Headers", "*", true);
        } catch (Throwable ignored) {}
        responseLength.set(0);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        bytesRead.set(0);
        return false; // no body
    }

    @Override
    public void cancel() {
        // nothing to do
    }
}
