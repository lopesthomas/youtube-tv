package com.youtubetv.youtube_tv;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class UpdateChecker {
    private static final String VERSION_URL = "https://orange-squirrel-925737.hostingersite.com/version.json";
    static final String CURRENT_VERSION = "1.0.3"; // Current version
    static final String stringCurrentVersion = CURRENT_VERSION.replace(".", "");
    static final Integer intCurrentVersion = Integer.valueOf(stringCurrentVersion);
    private static final String JAR_NAME = "youtube-tv.jar"; 
    private static final String DOWNLOAD_DIR = "."; 

    public static void checkForUpdates() {
        try {
            URL url = new URL(VERSION_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();

            JSONObject jsonResponse = new JSONObject(content.toString());
            String JSONlatestVersion = jsonResponse.getString("version");
            String stringLatestVersion = JSONlatestVersion.replace(".", "");
            Integer intLatestVersion = Integer.valueOf(stringLatestVersion);
            String downloadUrl = jsonResponse.getString("download_url");

            System.out.println(jsonResponse);
            System.out.println("Last version " + JSONlatestVersion);
            System.out.println("Download URL " + downloadUrl);

            if (intCurrentVersion < intLatestVersion) {
                // Show confirmation window
                int response = JOptionPane.showConfirmDialog(null,
                        "A new version (" + JSONlatestVersion + ") is available. Do you want to update ? \n" + "Current Version : " + CURRENT_VERSION,
                        "Update available",
                        JOptionPane.YES_NO_OPTION);

                if (response == JOptionPane.YES_OPTION) {
                    downloadNewVersion(downloadUrl, JAR_NAME);
                    JOptionPane.showMessageDialog(null, "Update completed. The application needs to be restarted.");
                    restartApplication();
                } else {
                    
                }
            } else {
                System.out.println("Your application is up to date.");
            }

        } catch (Exception e) {
            System.out.println("UpdateChecker error or invalid URL");
            
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e, CURRENT_VERSION, 0);
        }
    }

    // Download the new .jar
    public static void downloadNewVersion(String downloadUrl, String fileName) throws IOException {
        URL url = new URL(downloadUrl);
        HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
        httpConnection.setRequestMethod("GET");
        InputStream inputStream = httpConnection.getInputStream();

        FileOutputStream fileOutputStream = new FileOutputStream(DOWNLOAD_DIR + "/" + fileName);
        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }
        fileOutputStream.close();
        inputStream.close();

        System.out.println("New version downloaded : " + fileName);
    }

    // After the update (Not working)
    public static void restartApplication() {
        try {
            // Run the new .jar with a shell command
            String javaBin = System.getProperty("java.home") + "/bin/java";
            File currentJar = new File(UpdateChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            // Check if the file is an executable JAR
            if (!currentJar.getName().endsWith(".jar")) {
                return;
            }

            // Command to restart the application
            final String[] command = new String[] { javaBin, " -jar ", currentJar.getPath() };

            // Run the new command
            new ProcessBuilder(command).start();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        checkForUpdates();
        
    }
}
