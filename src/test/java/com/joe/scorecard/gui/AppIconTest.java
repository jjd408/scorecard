package com.joe.scorecard.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AppIconTest {

    @Test
    void everySizeIsPackagedAtThatSize() throws IOException {
        for (int size : AppIcon.SIZES) {
            try (InputStream in = AppIcon.class.getResourceAsStream("icons/scorecard-" + size + ".png")) {
                assertNotNull(in, "missing icon " + size);
                BufferedImage image = ImageIO.read(in);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
            }
        }
        assertEquals(AppIcon.SIZES.length, AppIcon.images().size());
    }
}
