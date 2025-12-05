package com.guapi_exe.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Converts OBJ model files to JSON format.
 */
public final class ObjConverter {

    private ObjConverter() {
        // Utility class, no instantiation
    }

    /**
     * Convert an OBJ file to a JSON representation.
     *
     * @param inputStream Input stream of the OBJ file
     * @return JsonObject representing the model
     * @throws IOException If reading fails
     */
    public static JsonObject convertObjToJson(InputStream inputStream) throws IOException {
        JsonObject model = new JsonObject();
        JsonArray vertices = new JsonArray();
        JsonArray texCoords = new JsonArray();
        JsonArray normals = new JsonArray();
        JsonArray faces = new JsonArray();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length == 0) continue;

                switch (parts[0]) {
                    case "v":
                        parseVertex(parts, vertices);
                        break;
                    case "vt":
                        parseTexCoord(parts, texCoords);
                        break;
                    case "vn":
                        parseNormal(parts, normals);
                        break;
                    case "f":
                        parseFace(parts, faces);
                        break;
                }
            }
        }

        model.addProperty("loader", "forge:obj");
        model.add("vertices", vertices);
        model.add("tex_coords", texCoords);
        model.add("normals", normals);
        model.add("faces", faces);
        return model;
    }

    private static void parseVertex(String[] parts, JsonArray vertices) {
        for (int i = 1; i < 4 && i < parts.length; i++) {
            vertices.add(Float.parseFloat(parts[i]));
        }
    }

    private static void parseTexCoord(String[] parts, JsonArray texCoords) {
        for (int i = 1; i < 3 && i < parts.length; i++) {
            texCoords.add(Float.parseFloat(parts[i]));
        }
    }

    private static void parseNormal(String[] parts, JsonArray normals) {
        for (int i = 1; i < 4 && i < parts.length; i++) {
            normals.add(Float.parseFloat(parts[i]));
        }
    }

    private static void parseFace(String[] parts, JsonArray faces) {
        JsonArray face = new JsonArray();
        for (int i = 1; i < parts.length; i++) {
            face.add(parts[i]);
        }
        faces.add(face);
    }
}
