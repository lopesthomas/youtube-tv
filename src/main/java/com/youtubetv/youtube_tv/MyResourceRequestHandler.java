package com.youtubetv.youtube_tv;

import org.cef.handler.CefCookieAccessFilter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.StringRef;

import java.net.URL;
import java.util.Locale;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest.Status;

public class MyResourceRequestHandler implements CefResourceRequestHandler {
    private final String userAgent;
	//private final String acceptLanguage;

    public MyResourceRequestHandler(String userAgent) {
        this.userAgent = userAgent;
		//this.acceptLanguage = acceptLanguage;
    }

    @Override
    public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
		String systemLanguage = Locale.getDefault().toLanguageTag();
		//request.setHeaderByName("accept-language", acceptLanguage, true); 
        request.setHeaderByName("User-Agent", userAgent, true);
		
		
		
		System.out.println(systemLanguage);
        return false;  // Continuer le chargement
    }

	@Override
	public CefCookieAccessFilter getCookieAccessFilter(CefBrowser browser, CefFrame frame, CefRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
		// TODO Auto-generated method stub

			String systemLanguage = System.getProperty("user.language") + "-" + System.getProperty("user.country");
			request.setHeaderByName("accept.language", systemLanguage, true);
		return null;
	}

	@Override
	public void onResourceRedirect(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response,
			StringRef new_url) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onResourceResponse(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onResourceLoadComplete(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response,
			Status status, long receivedContentLength) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProtocolExecution(CefBrowser browser, CefFrame frame, CefRequest request, BoolRef allowOsExecution) {
		// TODO Auto-generated method stub
		
	}

    // Implémentez les autres méthodes de l'interface ici (comme requis)
}
