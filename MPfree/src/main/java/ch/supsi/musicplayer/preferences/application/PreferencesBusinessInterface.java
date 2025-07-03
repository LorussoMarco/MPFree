package ch.supsi.musicplayer.preferences.application;

public interface PreferencesBusinessInterface {
    String getCurrentLanguage();
    Object getPreference(String key);
    boolean saveLanguage(String languageTag);
}
