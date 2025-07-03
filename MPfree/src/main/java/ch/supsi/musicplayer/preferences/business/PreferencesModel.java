package ch.supsi.musicplayer.preferences.business;

import ch.supsi.musicplayer.preferences.application.PreferencesBusinessInterface;
import ch.supsi.musicplayer.preferences.dataaccess.PreferencesPropertiesDataAccess;
import ch.supsi.musicplayer.translations.business.TranslationsModel;

import java.util.Properties;


public class PreferencesModel implements PreferencesBusinessInterface {

    private static PreferencesModel instance;

    private final PreferencesDataAccessInterface preferencesDao;

    private final Properties userPreferences;

    private PreferencesModel() {
        preferencesDao = PreferencesPropertiesDataAccess.getInstance();
        userPreferences = preferencesDao.getPreferences();
    }

    public static PreferencesModel getInstance() {
        if (instance == null) {
            instance = new PreferencesModel();
        }

        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    @Override
    public String getCurrentLanguage() {
        return userPreferences.getProperty("language-tag");
    }

    @Override
    public Object getPreference(String key) {
        if(key == null || key.isEmpty()) {
            return null;
        }

        if(userPreferences == null) {
            return null;
        }

        return userPreferences.get(key);
    }

    @Override
    public boolean saveLanguage(String languageTag) {
        if(!TranslationsModel.getInstance().isSupportedLanguageTag(languageTag)) return false;
        userPreferences.setProperty("language-tag", languageTag);
        return preferencesDao.savePreferences(userPreferences);
    }
}
