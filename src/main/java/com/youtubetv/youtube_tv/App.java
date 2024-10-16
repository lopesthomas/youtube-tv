// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.youtubetv.youtube_tv;

import me.friwi.jcefmaven.*;
import me.friwi.jcefmaven.impl.step.init.CefInitializer;

import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefRequestContext;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;

import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestContextHandler;
import org.cef.handler.CefRequestContextHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.network.CefRequest;
import org.cef.network.CefURLRequest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.File;
import java.util.Locale;

public class App extends JFrame {
    private static final long serialVersionUID = -5570653778104813836L;
    private final JTextField address_;
    private final CefApp cefApp_;
    private final CefClient client_;
    private final CefBrowser browser_;
    private final Component browerUI_;
    private boolean browserFocus_ = true;

    private App(String startURL, boolean useOSR, boolean isTransparent, String[] args) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        // (0) Initialize CEF using the maven loader
        CefAppBuilder builder = new CefAppBuilder();
        // windowless_rendering_enabled must be set to false if not wanted. 
        builder.getCefSettings().windowless_rendering_enabled = useOSR;
        // USE builder.setAppHandler INSTEAD OF CefApp.addAppHandler!
        // Fixes compatibility issues with MacOSX
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                // Shutdown the app if the native CEF part is terminated
                if (state == CefAppState.TERMINATED) System.exit(0);
            }
        });
        
        if (args.length > 0) {
        	builder.addJcefArgs(args);    
        }

        // Activer la persistance des cookies de session
        builder.getCefSettings().persist_session_cookies = true; // Important pour que les sessions persistent
        
        // Récupérer le chemin du répertoire où se trouve le JAR
        File jarDir = new File(CefApp.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile();
        String cachePath = new File(jarDir, "cache").getAbsolutePath(); // Créer un dossier "cache" à côté du JAR

        // Configurer un chemin de cache pour stocker les données
        builder.getCefSettings().cache_path = cachePath ;

        System.out.println("CEF lancé avec le cache dans le répertoire : " + cachePath);

        String systemLanguage = Locale.getDefault().getLanguage();
        
        builder.addJcefArgs("--lang", systemLanguage);
        cefApp_ = builder.build();
        
        client_ = cefApp_.createClient();
        
        // (3) Create a simple message router to receive messages from CEF.
        CefMessageRouter msgRouter = CefMessageRouter.create();
        client_.addMessageRouter(msgRouter);

        browser_ = client_.createBrowser(startURL, false, isTransparent);

        // Assigner le gestionnaire personnalisé
        browser_.getClient().addRequestHandler(new MyRequestHandler("Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.71 Safari/537.36 DMOST/2.0.0 (; LGE; webOSTV; WEBOS6.0.1 03.10.26; W6_lm21u;)"));
        browerUI_ = browser_.getUIComponent();

        // (5) For this minimal browser, we need only a text field to enter an URL
        //     we want to navigate to and a CefBrowser window to display the content
        //     of the URL. To respond to the input of the user, we're registering an
        //     anonymous ActionListener. This listener is performed each time the
        //     user presses the "ENTER" key within the address field.
        //     If this happens, the entered value is passed to the CefBrowser
        //     instance to be loaded as URL.
        address_ = new JTextField(startURL, 100);
        address_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.loadURL(address_.getText());
            }
        });

        // Update the address field when the browser URL changes.
        client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                address_.setText(url);
            }
        });

        // Clear focus from the browser when the address field gains focus.
        address_.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!browserFocus_) return;
                browserFocus_ = false;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                address_.requestFocus();
            }
        });

        // Clear focus from the address field when the browser gains focus.
        client_.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public void onGotFocus(CefBrowser browser) {
                if (browserFocus_) return;
                browserFocus_ = true;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                browser.setFocus(true);
            }

            @Override
            public void onTakeFocus(CefBrowser browser, boolean next) {
                browserFocus_ = false;
            }
        });
        
        // Set the frame to full screen
        setUndecorated(true);  // Enlève la décoration de la fenêtre (barre de titre, bordures)
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Ferme l'application à la fermeture de la fenêtre

        // (6) All UI components are assigned to the default content pane of this
        //     JFrame and afterwards the frame is made visible to the user.
      //  getContentPane().add(address_, BorderLayout.NORTH);
        getContentPane().add(browerUI_, BorderLayout.CENTER);
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int fHight = (int)screenSize.getHeight();
        int fWidth = (int)screenSize.getWidth();
        setSize(fWidth, fHight);
        System.out.println(fHight);
        System.out.println(fWidth);
        

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);

        //     To take care of shutting down CEF accordingly, it's important to call
        //     the method "dispose()" of the CefApp instance if the Java
        //     application will be closed. Otherwise you'll get asserts from CEF.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CefApp.getInstance().dispose();
                dispose();
            }
        });

        // Gestionnaire de chargement de la page
        client_.addLoadHandler(new CefLoadHandlerAdapter() {
        	public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        	    String loadScript =     	        		
        	        "const observer = new MutationObserver(() => {" +
        	            //    "var thumbnailDetails = document.querySelector(`ytlr-thumbnail-details tabindex='-1' class='ytlr-thumbnail-details--full-height ytlr-thumbnail-details--bg-hundred-percent ytlr-thumbnail-details'`);" +
                        "var thumbnailDetails = document.getElementsByClassName('ytlr-thumbnail-details--bg-contain ytlr-thumbnail-details ytlr-entity-metadata-renderer__thumbnail')[0];" +

        	            "if (thumbnailDetails) {" +
        	        	    //    "thumbnailDetails.style.backgroundColor = 'orange';" +
        	        	    // Remplacer l'URL de l'image dans le style
        	        	    "thumbnailDetails.style.backgroundImage = 'url(\"https://image.noelshack.com/fichiers/2024/41/6/1728685934-steamdeck-12-10-2024.png\")';" + // Modifie uniquement le style
        	                "console.log('URL de l\\'image remplacée');" +
        	        	"}" +
        	        "});" +

        	        // Configurer l'observateur pour surveiller les changements d'enfants dans le body
        	        "observer.observe(document.body, {" +
        	        	"childList: true," +
        	        	"subtree: true" +
        	        "});";

        	    browser.executeJavaScript(loadScript, browser.getURL(), 0);
        	}
        });
   
    }

    public static void main(String[] args) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        UpdateChecker.checkForUpdates(); // Lancer la vérification de la mise à jour
    //    JOptionPane.showMessageDialog(null, UpdateChecker.CURRENT_VERSION, "null", 0);
        boolean useOsr = false;
        new App("https://www.youtube.com/tv#/", useOsr, false, args);
    }
}