package com.quillapiclient.json;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonProcessor {
    JSONObject jsonObject;
    File file;

    public JsonProcessor() {
    }

    public JsonProcessor(String json) {
        this.jsonObject = new JSONObject(json);
    }

    public JsonProcessor(File file) {
        this.file = file;
    }

    public void loadJsonFromSource(){
        String content = "";
        try {
            content = new String(Files.readAllBytes(this.file.toPath()));
            setJsonObject(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void extractKeyValuePairs(Object json) {
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                System.out.println("Key: " + key + ", Value: " + value);
                // Recursively process nested objects or arrays
                extractKeyValuePairs(value);
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                extractKeyValuePairs(arr.get(i));
            }
        }
    }

    public Map<String, String> getMap() {
        Map<String, String> returnMap = new HashMap<>();
        Iterator<String> keys = this.jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = jsonObject.getString(key);
            returnMap.put(key, value);
        }
        return returnMap;
    }

    public void setJsonObject(String json) {
        this.jsonObject = new JSONObject(json);
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }
}
