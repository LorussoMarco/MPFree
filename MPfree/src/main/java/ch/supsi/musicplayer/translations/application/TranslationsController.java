package ch.supsi.musicplayer.translations.application;

import ch.supsi.musicplayer.preferences.application.PreferencesBusinessInterface;
import ch.supsi.musicplayer.preferences.business.PreferencesModel;
import ch.supsi.musicplayer.translations.business.TranslationsModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;
import java.util.Locale;

public class TranslationsController {
    private static TranslationsController instance;

    private final TranslationsBusinessInterface translationsModel;
    private final PreferencesBusinessInterface preferencesModel;
    private final StringProperty currentLanguage = new SimpleStringProperty();

    /**
     * Costruttore privato per il pattern Singleton.
     * Inizializza il controller delle traduzioni caricando la lingua corrente
     * dalle preferenze dell'utente e aggiornando il modello delle traduzioni.
     * Imposta anche la proprietà osservabile della lingua corrente.
     */
    private TranslationsController() {
        preferencesModel = PreferencesModel.getInstance();
        translationsModel = TranslationsModel.getInstance();

        String currentLanguage = preferencesModel.getCurrentLanguage();
        translationsModel.changeLanguage(currentLanguage);
        this.currentLanguage.set(currentLanguage);
    }

    public static TranslationsController getInstance() {
        if (instance == null) {
            instance = new TranslationsController();
        }

        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public String translate(String key) {
        return translationsModel.translate(key);
    }

    public List<String> getSupportedLanguageTags() {
        return translationsModel.getSupportedLanguageTags();
    }

    public boolean isSupportedLanguageTag(String languageTag) {
        return translationsModel.isSupportedLanguageTag(languageTag);
    }

    /**
     * Cambia la lingua corrente dell'applicazione.
     * Aggiorna sia il modello delle traduzioni che la proprietà osservabile.
     * @param languageTag Il codice della lingua da impostare (es. "it", "en")
     * @return true se il cambio di lingua è avvenuto con successo
     */
    public boolean changeLanguage(String languageTag) {
        boolean success = translationsModel.changeLanguage(languageTag);
        if (success) {
            currentLanguage.set(languageTag);
        }
        return success;
    }

    public StringProperty currentLanguageProperty() {
        return currentLanguage;
    }

    /**
     * Ottiene la locale corrente dell'applicazione.
     * Effettua un cast del modello delle traduzioni per accedere al metodo
     * getCurrentLocale() che non è definito nell'interfaccia.
     * @return La locale corrente dell'applicazione
     */
    public Locale getCurrentLocale() {
        return ((TranslationsModel)translationsModel).getCurrentLocale();
    }
}