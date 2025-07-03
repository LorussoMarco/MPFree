package ch.supsi.musicplayer.preferences.dataaccess;


import ch.supsi.musicplayer.preferences.business.PreferencesDataAccessInterface;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class PreferencesPropertiesDataAccess implements PreferencesDataAccessInterface {
    private static final String defaultPreferencesPath = "/default-user-preferences.properties";

    private static final String userHomeDirectory = System.getProperty("user.home");

    private static final String preferencesDirectory = ".musicplayer";

    private static final String preferencesFile = "preferences.properties";

    private static PreferencesPropertiesDataAccess instance;

    private static Properties userPreferences;

    private PreferencesPropertiesDataAccess() { }

    public static PreferencesPropertiesDataAccess getInstance() {
        if (instance == null) {
            instance = new PreferencesPropertiesDataAccess();
        }

        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    private Path getUserPreferencesDirectoryPath() {
        return Path.of(userHomeDirectory, preferencesDirectory);
    }

    private boolean userPreferencesDirectoryExists() {
        return Files.exists(this.getUserPreferencesDirectoryPath());
    }

    private Path createUserPreferencesDirectory() {
        try {
            return Files.createDirectories(this.getUserPreferencesDirectoryPath());
        } catch (IOException ignored) {
            ;
        }

        return null;
    }

    private Path getUserPreferencesFilePath() {
        return Path.of(userHomeDirectory, preferencesDirectory, preferencesFile);
    }

    private boolean userPreferencesFileExists() {
        return Files.exists(this.getUserPreferencesFilePath());
    }

    private boolean createUserPreferencesFile(Properties defaultPreferences) {
        if (defaultPreferences == null) {
            return false;
        }

        if (!userPreferencesDirectoryExists()) {
            this.createUserPreferencesDirectory();
        }

        if (!userPreferencesFileExists()) {
            try {
                // crea il file delle preferenze utente
                FileOutputStream outputStream = new FileOutputStream(String.valueOf(this.getUserPreferencesFilePath()));
                defaultPreferences.store(outputStream, null);
                return true;

            } catch (IOException ignored) {
                return false;
            }
        }

        return true;
    }

    private Properties loadDefaultPreferences() {
        Properties defaultPreferences = new Properties();
        try {
            InputStream defaultPreferencesStream = this.getClass().getResourceAsStream(defaultPreferencesPath);
            defaultPreferences.load(defaultPreferencesStream);

        } catch (IOException ignored) {
            ;
        }
        return defaultPreferences;
    }

    private Properties loadPreferences(Path path) {
        Properties preferences = new Properties();

        try {
            preferences.load(new FileInputStream(String.valueOf(path)));
            if(!checkProperties(preferences)) return loadDefaultPreferences(); 
        } catch (IOException ignored) {
            return null;
        }

        return preferences;
    }

    private boolean checkProperties(Properties properties) {
        return properties != null && properties.containsKey("language-tag");
    }

    @Override
    public Properties getPreferences() {
        if (userPreferences != null) {
            return userPreferences;
        }

        if (userPreferencesFileExists()) {
            userPreferences = this.loadPreferences(this.getUserPreferencesFilePath());
            return userPreferences;
        }

        userPreferences = this.loadDefaultPreferences();
        this.createUserPreferencesFile(userPreferences);

        return userPreferences;
    }

    @Override
    public boolean savePreferences(Properties preferences) {
        if(preferences == null) return false;

        // Update preferences
        userPreferences = preferences;

        if(!userPreferencesFileExists()) { // If preferences file does not exist, create it with new properties
            return createUserPreferencesFile(userPreferences);
        } else { // If preferences file exists, update it with new values
            try(FileOutputStream output = new FileOutputStream(String.valueOf(getUserPreferencesFilePath()))) {
                userPreferences.store(output, null);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }
}
