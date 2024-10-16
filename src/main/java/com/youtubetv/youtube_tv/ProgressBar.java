package com.youtubetv.youtube_tv;
import javax.swing.*;
import java.awt.*;

public class ProgressBar {

    public static JProgressBar progressBar;  // Déclarez-le ici en tant que champ statique
    private static JFrame frame;

    public static void createAndShowProgressBar() {
        // Créer la fenêtre
        frame = new JFrame("Initializing...");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(400, 100);
        frame.setLayout(new BorderLayout());

        // Initialiser la barre de progression
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        // Ajouter la barre de progression à la fenêtre
        frame.add(progressBar, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);  // Centrer la fenêtre
        frame.setVisible(true);
    }

    public static void updateProgress(int percent) {
        // Vérifier que progressBar n'est pas null avant de l'utiliser
        if (progressBar != null) {
            progressBar.setValue(percent);
        } else {
            System.out.println("progressBar est null !");
        }
    }

    public static void closeProgressBar() {
        if (frame != null) {
            frame.dispose();
        }
    }
}