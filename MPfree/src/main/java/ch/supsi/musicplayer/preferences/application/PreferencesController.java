package ch.supsi.musicplayer.preferences.application;

import ch.supsi.musicplayer.preferences.business.PreferencesModel;
import ch.supsi.musicplayer.translations.application.TranslationsController;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import java.util.List;

public class PreferencesController {
    private static PreferencesController instance;

    private final PreferencesBusinessInterface preferencesModel;
    private final TranslationsController translationsController;

    @FXML private ComboBox<String> languageComboBox;

    public PreferencesController() {
        preferencesModel = PreferencesModel.getInstance();
        translationsController = TranslationsController.getInstance();
    }

    // Costruttore private per pattern singleton
    private PreferencesController(boolean isSingleton) {
        preferencesModel = PreferencesModel.getInstance();
        translationsController = TranslationsController.getInstance();
    }

    public static PreferencesController getInstance() {
        if (instance == null) {
            instance = new PreferencesController(true);
        }

        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public Object getPreference(String key) {
        return preferencesModel.getPreference(key);
    }

    public String getCurrentLanguage() {
        return preferencesModel.getCurrentLanguage();
    }

    public boolean saveLanguage(String languageTag) {
        return preferencesModel.saveLanguage(languageTag);
    }

    @FXML
    public void initialize() {
        List<String> supportedLanguages = translationsController.getSupportedLanguageTags();
        languageComboBox.getItems().addAll(supportedLanguages);
        
        // Imposta la lingua selezionata
        String currentLanguage = getCurrentLanguage();
        if (currentLanguage != null) {
            languageComboBox.setValue(currentLanguage);
        }
    }

    @FXML
    public void saveChangesAndClose() {
        String selectedLanguage = languageComboBox.getValue();
        if (selectedLanguage != null) {
            saveLanguage(selectedLanguage);
            translationsController.changeLanguage(selectedLanguage);
        }
        
        Stage stage = (Stage) languageComboBox.getScene().getWindow();
        stage.close();
    }
}
