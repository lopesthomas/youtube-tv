package com.youtubetv.youtube_tv;

import org.cef.handler.CefCookieAccessFilter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.StringRef;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

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

	private boolean isYouTubeiTarget(String url) {
		if (url == null) return false;
		try {
			URI u = URI.create(url);
			String host = Objects.toString(u.getHost(), "").toLowerCase(Locale.ROOT);
			String path = Objects.toString(u.getPath(), "").toLowerCase(Locale.ROOT);
			String query = Objects.toString(u.getQuery(), "").toLowerCase(Locale.ROOT);
			boolean ytHost = (host.endsWith("youtube.com") || host.endsWith("c.youtube.com") || host.equals("youtubei.googleapis.com"));
			if (!ytHost) return false;
			// youtubei JSON APIs
			if (path.startsWith("/youtubei/")) {
				if (path.contains("/player") || path.contains("/get_watch") || path.contains("/next") || path.contains("/browse")) return true;
			}
			// TV watch and playlist endpoints often carry ad placement data
			if ("/watch".equals(path) && query.contains("tv=")) return true;
			if ("/playlist".equals(path) && query.contains("list=")) return true;
			return false;
		} catch (Throwable ignored) {
			return false;
		}
	}

    @Override
    public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
		String systemLanguage = System.getProperty("user.language") + "-" + System.getProperty("user.country");
		request.setHeaderByName("accept-language", systemLanguage, true);
		request.setHeaderByName("User-Agent", userAgent, true);
		
		
		
		System.out.println(systemLanguage);
        return false;
    }

	@Override
	public CefCookieAccessFilter getCookieAccessFilter(CefBrowser browser, CefFrame frame, CefRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	// @Override
	// public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
	// 	// TODO Auto-generated method stub

	// 		String systemLanguage = System.getProperty("user.language") + "-" + System.getProperty("user.country");
	// 		request.setHeaderByName("accept.language", systemLanguage, true);
	// 	return null;
	// }

	@Override
	public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
		// Route specific YouTubei JSON API requests through our rewrite handler to prune ad fields.
		try {
			String url = request.getURL();
			// Bypass explicit ad media playback segments (initplayback/videoplayback with ad flags)
			if (isAdMediaUrl(url)) {
				return new MediaAdBypassHandler();
			}
			if (isYouTubeiTarget(url)) {
				System.out.println("[ADBLOCK] using YouTubeiRewriteHandler for: " + url);
				return new YouTubeiRewriteHandler(userAgent);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	private boolean isAdMediaUrl(String url) {
		if (url == null) return false;
		String l = url.toLowerCase(Locale.ROOT);
		if (!(l.contains("/initplayback") || l.contains("/videoplayback"))) return false;
		// Heuristics: if the request includes clear ad markers, we short-circuit it.
		// Examples seen on TVHTML5: oad=, oaad=, oavd=, ad3_module, ctier=A (or url-encoded), and others.
		if (l.contains("is_ad=1") || l.contains("adformat=") || l.contains("ad_v=") || l.contains("ad_sys=")) return true;
		if (l.contains("oad=") || l.contains("oaad=") || l.contains("oavd=") || l.contains("ad3_module")) return true;
		if (l.contains("ctier=a") || l.contains("ctier%3da") || l.contains("ctier%3da")) return true;
		if (l.contains("aitags=") || l.contains("ad_host=")) return true;
		return false;
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

}
