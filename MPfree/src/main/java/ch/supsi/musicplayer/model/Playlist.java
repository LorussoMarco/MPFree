package ch.supsi.musicplayer.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Playlist {
    private String name;
    private List<SongModel> songs;
    private File playlistDirectory;

    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
    }
    
    public Playlist(File directory) {
        this.playlistDirectory = directory;
        this.name = directory.getName();
        this.songs = new ArrayList<>();
        loadSongsFromDirectory();
    }
 
    private boolean loadSongsFromDirectory() {
        if (playlistDirectory != null && playlistDirectory.exists() && playlistDirectory.isDirectory()) {
            File[] files = playlistDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));
            
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.canRead()) {
                        songs.add(new SongModel(file));
                    }
                }
            }
            
            return !songs.isEmpty();
        }
        return false;
    }
    
    public boolean saveToFilesystem(String basePath) throws IOException {
        if (playlistDirectory == null) {
            Path playlistPath = Paths.get(basePath, name);
            playlistDirectory = playlistPath.toFile();
            
            if (!playlistDirectory.exists()) {
                Files.createDirectories(playlistPath);
            }
        }
        return true;
    }
   
    public void addSong(SongModel song) {
        // Verifica che la canzone non sia già presente
        boolean alreadyExists = false;
        for (SongModel existingSong : songs) {
            if (existingSong.getFile().getName().equals(song.getFile().getName())) {
                alreadyExists = true;
                break;
            }
        }
        
        if (!alreadyExists) {
            songs.add(song);
        }
    }
    
    public void removeSong(SongModel song) {
        songs.remove(song);
    }
    
    public boolean moveSongUp(SongModel song) {
        int index = songs.indexOf(song);
        
        if (index <= 0) {
            return false;
        }
        
        Collections.swap(songs, index, index - 1);
        return true;
    }
    
    public boolean moveSongDown(SongModel song) {
        int index = songs.indexOf(song);
        
        if (index < 0 || index >= songs.size() - 1) {
            return false;
        }
        
        Collections.swap(songs, index, index + 1);
        return true;
    }
    
    public boolean moveSongToPosition(SongModel song, int newIndex) {
        int currentIndex = songs.indexOf(song);
        
        if (currentIndex < 0 || currentIndex == newIndex || newIndex < 0 || newIndex >= songs.size()) {
            return false;
        }
        
        songs.remove(currentIndex);
        songs.add(newIndex, song);
        return true;
    }
 
    public List<SongModel> getSongs() {
        return Collections.unmodifiableList(songs);
    }
    
    public int getTotalDuration() {
        return songs.stream()
                .mapToInt(SongModel::getDuration)
                .sum();
    }
 
    public String getName() {
        return name;
    }
  
    public void setName(String name) {
        this.name = name;
    }

    public File getPlaylistDirectory() {
        return playlistDirectory;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
