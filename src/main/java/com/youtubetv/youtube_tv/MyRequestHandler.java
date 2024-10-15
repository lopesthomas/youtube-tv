package com.youtubetv.youtube_tv;
import java.util.Locale;

import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest;

public class MyRequestHandler extends CefRequestHandlerAdapter {
    private final String userAgent;
  //  private final String acceptLanguage;

    public MyRequestHandler(String userAgent ) {
        this.userAgent = userAgent;
      //  this.acceptLanguage = acceptLanguage;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                                                               boolean isNavigation, boolean isDownload, String requestInitiator,
                                                               BoolRef disableDefaultHandling) {

        //         // Récupérer la langue du système
        // //    String systemLanguage = Locale.getDefault().getLanguage();
        // String systemLanguage = Locale.getDefault().toLanguageTag(); // "fr-FR" ou "en-US", etc.

        //         // Ajouter l'en-tête "Accept-Language" à la requête
        //     request.setHeaderByName("Accept-Language", systemLanguage, true); 

        // String systemLanguage = System.getProperty("user.language") + "-" + System.getProperty("user.country");
                
        // // Modifier l'en-tête Accept-Language
        // request.setHeaderByName("Accept-Language", systemLanguage, true);

        return new MyResourceRequestHandler(userAgent);
    }
}
