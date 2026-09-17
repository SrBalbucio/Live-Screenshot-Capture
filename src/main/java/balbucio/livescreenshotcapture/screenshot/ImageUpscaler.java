package balbucio.livescreenshotcapture.screenshot;

import java.awt.image.BufferedImage;

public interface ImageUpscaler {
    BufferedImage upscale(BufferedImage src, int factor);
}
