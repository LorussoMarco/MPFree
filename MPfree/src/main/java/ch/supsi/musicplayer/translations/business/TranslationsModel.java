package ch.supsi.musicplayer.translations.business;

import ch.supsi.musicplayer.translations.application.TranslationsBusinessInterface;
import ch.supsi.musicplayer.translations.dataaccess.TranslationsPropertiesDataAccess;


import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class TranslationsModel implements TranslationsBusinessInterface {
    private static TranslationsModel instance;

    private final TranslationsDataAccessInterface translationsDao;

    private final List<String> supportedLanguageTags;

    private Properties translations;
    private Locale currentLocale;

    private TranslationsModel() {
        translationsDao = TranslationsPropertiesDataAccess.getInstance();
        supportedLanguageTags = translationsDao.getSupportedLanguageTags();
    }

    public static TranslationsModel getInstance() {
        if (instance == null) {
            instance = new TranslationsModel();
        }

        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    @Override
    public boolean isSupportedLanguageTag(String languageTag) {
        return supportedLanguageTags.contains(languageTag);
    }

    @Override
    public boolean changeLanguage(String languageTag) {
        if(!isSupportedLanguageTag(languageTag)) return false;
        currentLocale = Locale.forLanguageTag(languageTag);
        translations = translationsDao.getTranslations(currentLocale);
        return translations != null;
    }

    @Override
    public String translate(String key) {
        return translations.getProperty(key);
    }

    @Override
    public List<String> getSupportedLanguageTags() {
        return supportedLanguageTags;
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }
}
