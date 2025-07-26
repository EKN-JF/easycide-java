package editor;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.file.*;
import java.util.List;

import javax.swing.plaf.TreeUI;

import java.io.File;

public class SettingsManager {
    private static final String SETTINGS_FILE = "settings.json";
    //private static final String CSETTINGS_FILE = "compilesettings.json";
    public static void saveSettings(String rootDir, List<String> openFiles, boolean autoSave) {
        JSONObject json = new JSONObject();
        json.put("rootDirectory", rootDir);
        json.put("openFiles", new JSONArray(openFiles));
        json.put("autoSave", autoSave);
        try (FileWriter file = new FileWriter(SETTINGS_FILE)) {
            file.write(json.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static JSONObject loadSettings() {
        try {
            if (Files.exists(Paths.get(SETTINGS_FILE))) {
                String content = new String(Files.readAllBytes(Paths.get(SETTINGS_FILE)));
                return new JSONObject(content);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }
    public static boolean compileSettings(String MAINFILE,List<String> INCLUDEPATH){
        JSONObject json = new JSONObject();
        json.put("mainfile", MAINFILE);
        json.put("includepath", new JSONArray(INCLUDEPATH));
        try (FileWriter file = new FileWriter(SETTINGS_FILE)) {
            file.write(json.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    } 
}