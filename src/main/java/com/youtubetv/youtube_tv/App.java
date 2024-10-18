// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.youtubetv.youtube_tv;

import me.friwi.jcefmaven.*;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;

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
        // Initialize CEF using the maven loader
        CefAppBuilder builder = new CefAppBuilder();
        // JCEF Download progress
        builder.setProgressHandler(new ConsoleProgressHandler() {
            @Override
            public void handleProgress(EnumProgress state, float percent) {
                int i = Math.round(percent);
                String etat = state.toString();
                if (i < 99){
                    ProgressBar.updateProgress(i);
                } else if (i > 99){
                    ProgressBar.updateProgress(100);
                    ProgressBar.closeProgressBar();
                } else if (percent < 0){
                    ProgressBar.closeProgressBar();
                } else if (etat == "INITIALIZED"){
                    ProgressBar.closeProgressBar();
                }
                System.out.println(state);
                System.out.println("etat : " + etat);
                System.out.println("Progress: " + percent + "%");
            }
        });

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

        builder.getCefSettings().persist_session_cookies = true;
        // Path of the JAR
        File jarDir = new File(CefApp.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile();
        String cachePath = new File(jarDir, "cache").getAbsolutePath();
        builder.getCefSettings().cache_path = cachePath ;
        // Setup Language
        String systemLanguage = Locale.getDefault().getLanguage();
        builder.addJcefArgs("--lang", systemLanguage);

        // The builder.build() method to build the CefApp on first run and fetch the instance on all consecutive runs
        cefApp_ = builder.build();

        // Method "createClient()" of your CefApp instance
        client_ = cefApp_.createClient();

        // Create a simple message router to receive messages from CEF.
        CefMessageRouter msgRouter = CefMessageRouter.create();
        client_.addMessageRouter(msgRouter);

        // UI Components
        browser_ = client_.createBrowser(startURL, useOSR, isTransparent);
        browerUI_ = browser_.getUIComponent();

        // Request userAgent of HEADER personalized
        browser_.getClient().addRequestHandler(new MyRequestHandler("Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.71 Safari/537.36 DMOST/2.0.0 (; LGE; webOSTV; WEBOS6.0.1 03.10.26; W6_lm21u;)"));

        // Need JTextField to run
        address_ = new JTextField("", 0);
        address_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               // browser_.loadURL(address_.getText());
            }
        });

        // Update the address field when the browser URL changes.
        client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                //address_.setText(url);
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

        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        setUndecorated(true);  // Removes window decoration (title bar, borders)
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Closes application when window is closed

        // All UI components are assigned to the default content pane of this
        // JFrame and afterwards the frame is made visible to the user.
        getContentPane().add(address_, null);
        getContentPane().add(browerUI_, BorderLayout.CENTER);
        device.getFullScreenWindow();
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int fHight = (int)screenSize.getHeight();
        int fWidth = (int)screenSize.getWidth();
        setSize(fWidth, fHight);
        browerUI_.setSize(fWidth, fHight);
        //browerUI_.repaint(Color.BLUE);
        //address_.setMinimumSize(0,0);
        address_.setSize(1, 1);
        setResizable(true);
        
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);

        // Shutting down CEF accordingly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CefApp.getInstance().dispose();
                dispose();
            }
        });
    }

    public static void main(String[] args) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        // Check updates
        UpdateChecker.checkForUpdates(); // Lancer la vérification de la mise à jour
        // If Jcef is installed, the progress bar is not invoked.
        File installDir = new File("jcef-bundle");
        if (!installDir.exists()) {
            SwingUtilities.invokeLater(ProgressBar::createAndShowProgressBar);
        } else {
            System.out.println("The jcef-bundle folder already exists");
        }
        boolean useOsr = false;
        new App("https://www.youtube.com/tv#/", useOsr, false, args);
    }
}