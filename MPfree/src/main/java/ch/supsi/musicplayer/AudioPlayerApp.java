package ch.supsi.musicplayer;

import ch.supsi.musicplayer.translations.application.TranslationsController;
import ch.supsi.musicplayer.controller.PlayerController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class AudioPlayerApp extends Application {
    private Stage primaryStage;
    private TranslationsController translations;
    
    @Override
    public void start(Stage primaryStage) {
        try {
            this.primaryStage = primaryStage;
            translations = TranslationsController.getInstance();
            loadUI();
            
            translations.currentLanguageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerView.fxml"));
                        loader.setResources(ResourceBundle.getBundle("i18n.labels"));
                        if (loader.getController() != null) {
                            ch.supsi.musicplayer.controller.PlayerController controller = 
                                (ch.supsi.musicplayer.controller.PlayerController) loader.getController();
                            if (controller != null) {
                                controller.onStopClicked();
                            }
                        }
                        loadUI();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadUI() throws IOException {
        if (primaryStage.getScene() != null) {
            PlayerController oldController = (PlayerController) primaryStage.getScene().getUserData();
            if (oldController != null) {
                oldController.onStopClicked();
            }
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerView.fxml"));
        loader.setResources(ResourceBundle.getBundle("i18n.labels", translations.getCurrentLocale()));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/player.css")).toExternalForm());

        scene.setUserData(loader.getController());

        URL imageUrl = getClass().getResource("/images/Logo.jpg");
        if (imageUrl != null) {
            Image image = new Image(imageUrl.toExternalForm());
            primaryStage.getIcons().add(image);
        } else {
            System.err.println("Error: Unable to load logo image.");
        }

        primaryStage.setScene(scene);
        primaryStage.setTitle("MP3 Player");
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(350);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void stop() {
        System.out.println("Application closing...");
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerView.fxml"));
            loader.setResources(ResourceBundle.getBundle("i18n.labels"));
            if (loader.getController() != null) {
                ch.supsi.musicplayer.controller.PlayerController controller = 
                    (ch.supsi.musicplayer.controller.PlayerController) loader.getController();
                if (controller != null) {
                    controller.exitApplication();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}