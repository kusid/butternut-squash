package com.kusid.butternut.image.thumbnailer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.event.Event;
import reactor.function.Function;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DESC: Worker to thumbnail an image by way of implementing Reactor's apply method of its Function interface.
 * It then uses jdk's internal image transformer.
 * <p/>
 * //todo: handle exceptions
 * //todo: refactor to use GraphicsMagick.
 */
public class ImageThumbnailer implements Function<Event<Path>, Path> {

    private static final ImageObserver DUMMY_OBSERVER = (img, infoflags, x, y, width, height) -> true;
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageThumbnailer.class.getSimpleName());
    private final int maxLongSide;

    public ImageThumbnailer(int maxLongSide) {
        this.maxLongSide = maxLongSide;
    }

    /**
     * Thumbnail worker method.
     *
     * @param event Image input event with the payload as the image's path.
     * @return Path to the thumbnail created.
     */
    @Override
    public Path apply(Event<Path> event) {
        try {
            Path srcPath = event.getData();
            Path thumbnailPath = Files.createTempFile("thumbnail", ".jpg").toAbsolutePath();

            LOGGER.info("Attempting to read image @ {}", srcPath);
            BufferedImage incomingBufferedImage = ImageIO.read(srcPath.toFile());


            double scale;
            if (incomingBufferedImage.getWidth() >= incomingBufferedImage.getHeight()) {
                // landscape
                scale = Math.min(maxLongSide, incomingBufferedImage.getWidth()) / (double) incomingBufferedImage.getWidth();
            } else {
                // portrait
                scale = Math.min(maxLongSide, incomingBufferedImage.getHeight()) / (double) incomingBufferedImage.getHeight();
            }

            BufferedImage thumbnailOut = new BufferedImage((int) (scale * incomingBufferedImage.getWidth()),
                    (int) (scale * incomingBufferedImage.getHeight()),
                    incomingBufferedImage.getType());
            Graphics2D thumbnailOutGraphicsDrawer = thumbnailOut.createGraphics();

            AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
            thumbnailOutGraphicsDrawer.drawImage(incomingBufferedImage, transform, DUMMY_OBSERVER);

            LOGGER.info("Attempting to create thumbnail...");
            ImageIO.write(thumbnailOut, "jpeg", thumbnailPath.toFile());

            LOGGER.info("Success. Image thumbnail created at: {}", thumbnailPath);

            return thumbnailPath;
        } catch (Exception e) {
            LOGGER.error("Exception occurred while trying to read image...");
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}
