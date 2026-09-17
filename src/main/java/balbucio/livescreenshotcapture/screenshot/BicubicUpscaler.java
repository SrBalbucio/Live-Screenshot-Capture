package balbucio.livescreenshotcapture.screenshot;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

public class BicubicUpscaler implements ImageUpscaler {
    private static final float[] SHARPEN = {0, -1, 0, -1, 5, -1, 0, -1, 0};
    private final boolean sharpen;

    public BicubicUpscaler() {
        this(true);
    }

    public BicubicUpscaler(boolean sharpen) {
        this.sharpen = sharpen;
    }

    @Override
    public BufferedImage upscale(BufferedImage src, int factor) {
        if (src == null) {
            throw new IllegalArgumentException("src must not be null");
        }
        if (factor <= 1) {
            return src;
        }
        int targetW = src.getWidth() * factor;
        int targetH = src.getHeight() * factor;
        BufferedImage img = src;
        int w = src.getWidth();
        int h = src.getHeight();
        while (w < targetW || h < targetH) {
            w = Math.min(targetW, w * 2);
            h = Math.min(targetH, h * 2);
            img = scaleStep(img, w, h);
        }
        return sharpen ? sharpen(img) : img;
    }

    private BufferedImage scaleStep(BufferedImage src, int w, int h) {
        int type = src.getTransparency() == java.awt.Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private BufferedImage sharpen(BufferedImage src) {
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, SHARPEN),
                ConvolveOp.EDGE_NO_OP, null);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), src.getType() == 0
                ? BufferedImage.TYPE_INT_RGB
                : src.getType());
        return op.filter(src, out);
    }
}
