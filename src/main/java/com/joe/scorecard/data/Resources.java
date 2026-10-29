package com.joe.scorecard.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the data files bundled with the app, such as the list of MLB teams. */
final class Resources {

    private Resources() {
    }

    /** Returns the file's lines, trimmed, skipping blank lines and # comments. */
    static List<String> lines(String name) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = Resources.class.getResourceAsStream(name);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lines;
    }
}
