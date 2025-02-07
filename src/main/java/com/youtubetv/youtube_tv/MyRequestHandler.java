package com.youtubetv.youtube_tv;

import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest;

public class MyRequestHandler extends CefRequestHandlerAdapter {
    private final String userAgent;


    public MyRequestHandler(String userAgent ) {
        this.userAgent = userAgent;

    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                                                               boolean isNavigation, boolean isDownload, String requestInitiator,
                                                               BoolRef disableDefaultHandling) {

        return new MyResourceRequestHandler(userAgent);
    }
}
