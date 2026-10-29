package com.joe.scorecard.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HelpTest {

    private static String manual() throws IOException {
        try (InputStream in = Help.class.getResourceAsStream(Help.MANUAL)) {
            assertNotNull(in, "the manual is packaged with the app");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> matches(String html, String regex) {
        Set<String> found = new HashSet<>();
        Matcher m = Pattern.compile(regex).matcher(html);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    void everyTopicHasASection() throws IOException {
        Set<String> anchors = matches(manual(), "<a name=\"([^\"]+)\"");
        for (Help.Topic topic : Help.Topic.values()) {
            assertTrue(anchors.contains(topic.anchor()), "no section for " + topic);
        }
    }

    @Test
    void everyLinkInTheManualGoesSomewhere() throws IOException {
        String html = manual();
        Set<String> anchors = matches(html, "<a name=\"([^\"]+)\"");
        for (String link : matches(html, "<a href=\"#([^\"]+)\"")) {
            assertTrue(anchors.contains(link), "broken link #" + link);
        }
    }

    @Test
    void everyPictureIsPackaged() throws IOException {
        for (String image : matches(manual(), "<img src=\"([^\"]+)\"")) {
            assertNotNull(Help.class.getResource("help/" + image), "missing picture " + image);
        }
    }
}
