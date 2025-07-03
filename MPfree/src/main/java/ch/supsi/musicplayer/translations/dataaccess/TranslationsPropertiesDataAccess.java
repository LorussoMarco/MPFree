package ch.supsi.musicplayer.translations.dataaccess;

import ch.supsi.musicplayer.translations.business.TranslationsDataAccessInterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.util.ResourceBundle.Control.FORMAT_DEFAULT;

public class TranslationsPropertiesDataAccess implements TranslationsDataAccessInterface {
    private static final String translationsResourceBundlePath = "i18n.labels";

    private static final String supportedLanguagesPath = "/supported-languages.properties";

    private static TranslationsPropertiesDataAccess instance;

    private TranslationsPropertiesDataAccess() { }

    public static TranslationsPropertiesDataAccess getInstance() {
        if (instance == null) {
            instance = new TranslationsPropertiesDataAccess();
        }

        return instance;
    }

    // FOR TESTING ONLY
    public static void resetInstance() {
        instance = null;
    }

    private Properties loadSupportedLanguageTags() {
        Properties supportedLanguageTags = new Properties();
        try {
            InputStream supportedLanguageTagsStream = getClass().getResourceAsStream(supportedLanguagesPath);
            supportedLanguageTags.load(supportedLanguageTagsStream);
        } catch(IOException ignored) {
            ;
        }

        return supportedLanguageTags;
    }

    @Override
    public List<String> getSupportedLanguageTags() {
        List<String> supportedLanguageTags = new ArrayList<>();

        Properties props = loadSupportedLanguageTags();
        for(Object key : props.keySet()) {
            supportedLanguageTags.add(props.getProperty((String) key));
        }

        return supportedLanguageTags;
    }

    @Override
    public Properties getTranslations(Locale locale) {
        final Properties translations = new Properties();
        ResourceBundle bundle;
        try {
            bundle = ResourceBundle.getBundle(
                    translationsResourceBundlePath,
                    locale,
                    ResourceBundle.Control.getNoFallbackControl(FORMAT_DEFAULT)
            );
        } catch(MissingResourceException e) {
            System.err.println("unsupported language tag..." + locale.toLanguageTag());

            List<String> supportedLanguageTags = getSupportedLanguageTags();
            String fallbackLanguageTag = supportedLanguageTags.get(0);
            System.err.println("falling back to..." + fallbackLanguageTag);

            bundle = ResourceBundle.getBundle(
                    translationsResourceBundlePath,
                    Locale.forLanguageTag(fallbackLanguageTag),
                    ResourceBundle.Control.getNoFallbackControl(FORMAT_DEFAULT)
            );
        }

        for(String key : bundle.keySet()) {
            translations.put(key, bundle.getString(key));
        }

        return translations;
    }
}
