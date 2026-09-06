package com.petrick.vtt.feature.asset.animation;

import com.mojang.blaze3d.platform.NativeImage;
import com.petrick.vtt.VTT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.awt.AlphaComposite;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Carrega GIFs como várias DynamicTextures do Minecraft.
 *
 * Primeira versão:
 * - suporta GIF;
 * - registra cada frame como uma textura dinâmica;
 * - usa o delay do GIF quando possível;
 * - compõe os frames em um canvas para funcionar melhor com transparência.
 */
public final class AnimatedTextureLoader {

    private static final int DEFAULT_FRAME_DURATION_MS = 100;

    private static final int MAX_FRAMES = 120;

    private static final int MAX_WIDTH = 2048;

    private static final int MAX_HEIGHT = 2048;

    public AnimatedTexture loadGif(
            String animatedTextureId,
            Path file
    ) {
        if (animatedTextureId == null || animatedTextureId.isBlank()) {
            throw new IllegalArgumentException(
                    "Animated texture id cannot be null or blank"
            );
        }

        if (file == null) {
            throw new IllegalArgumentException(
                    "Animated texture file cannot be null"
            );
        }

        List<AnimatedTextureFrame> frames = new ArrayList<>();
        ImageReader reader = null;

        try (
                ImageInputStream imageInputStream =
                        ImageIO.createImageInputStream(
                                Files.newInputStream(file)
                        )
        ) {
            if (imageInputStream == null) {
                throw new IOException(
                        "Could not create image input stream for " + file
                );
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReadersByFormatName("gif");

            if (!readers.hasNext()) {
                throw new IOException("No GIF ImageReader available");
            }

            reader = readers.next();
            reader.setInput(imageInputStream, false, false);

            int frameCount = Math.min(
                    reader.getNumImages(true),
                    MAX_FRAMES
            );

            if (frameCount <= 0) {
                throw new IOException(
                        "GIF has no frames: " + file
                );
            }

            BufferedImage canvas = null;

            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {

                BufferedImage rawFrame = reader.read(frameIndex);

                if (rawFrame == null) {
                    throw new IOException(
                            "Could not read GIF frame "
                                    + frameIndex
                                    + ": "
                                    + file
                    );
                }

                if (
                        rawFrame.getWidth() > MAX_WIDTH
                                || rawFrame.getHeight() > MAX_HEIGHT
                ) {
                    throw new IOException(
                            "Animated image is too large: "
                                    + rawFrame.getWidth()
                                    + "x"
                                    + rawFrame.getHeight()
                    );
                }

                if (canvas == null) {
                    canvas = new BufferedImage(
                            rawFrame.getWidth(),
                            rawFrame.getHeight(),
                            BufferedImage.TYPE_INT_ARGB
                    );
                }

                Graphics2D graphics = canvas.createGraphics();

                try {
                    graphics.setComposite(AlphaComposite.Clear);
                    graphics.fillRect(
                            0,
                            0,
                            canvas.getWidth(),
                            canvas.getHeight()
                    );

                    graphics.setComposite(AlphaComposite.SrcOver);

                    graphics.drawImage(
                            rawFrame,
                            0,
                            0,
                            null
                    );
                } finally {
                    graphics.dispose();
                }

                NativeImage nativeImage =
                        convertToNativeImage(canvas);

                ResourceLocation textureLocation =
                        Minecraft.getInstance()
                                .getTextureManager()
                                .register(
                                        "vtt_animated_texture/"
                                                + sanitizeTextureName(
                                                animatedTextureId
                                        )
                                                + "/frame_"
                                                + frameIndex,
                                        new DynamicTexture(nativeImage)
                                );

                int durationMs =
                        readFrameDurationMs(
                                reader.getImageMetadata(frameIndex)
                        );

                frames.add(
                        new AnimatedTextureFrame(
                                textureLocation,
                                canvas.getWidth(),
                                canvas.getHeight(),
                                durationMs
                        )
                );
            }

            AnimatedTexture animatedTexture =
                    new AnimatedTexture(
                            animatedTextureId,
                            frames
                    );

            VTT.LOGGER.info(
                    "Loaded VTT animated texture: {} ({} frames)",
                    animatedTextureId,
                    frames.size()
            );

            return animatedTexture;

        } catch (IOException exception) {

            releaseFrames(frames);

            VTT.LOGGER.error(
                    "Failed to load animated texture: {}",
                    file,
                    exception
            );

            return null;

        } catch (RuntimeException exception) {

            releaseFrames(frames);

            VTT.LOGGER.error(
                    "Unexpected error while loading animated texture: {}",
                    file,
                    exception
            );

            return null;

        } finally {

            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        NativeImage nativeImage = new NativeImage(
                bufferedImage.getWidth(),
                bufferedImage.getHeight(),
                false
        );

        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                int argb = bufferedImage.getRGB(x, y);

                int alpha = (argb >> 24) & 0xFF;
                int red = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int blue = argb & 0xFF;

                int abgr = alpha << 24
                        | blue << 16
                        | green << 8
                        | red;

                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }

        return nativeImage;
    }

    private int readFrameDurationMs(IIOMetadata metadata) {
        if (metadata == null) {
            return DEFAULT_FRAME_DURATION_MS;
        }

        try {
            Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            Node graphicControlExtension = findNode(root, "GraphicControlExtension");

            if (graphicControlExtension == null) {
                return DEFAULT_FRAME_DURATION_MS;
            }

            NamedNodeMap attributes = graphicControlExtension.getAttributes();

            if (attributes == null) {
                return DEFAULT_FRAME_DURATION_MS;
            }

            Node delayNode = attributes.getNamedItem("delayTime");

            if (delayNode == null) {
                return DEFAULT_FRAME_DURATION_MS;
            }

            int delayCentiseconds = Integer.parseInt(delayNode.getNodeValue());

            return Math.max(20, delayCentiseconds * 10);
        } catch (RuntimeException exception) {
            return DEFAULT_FRAME_DURATION_MS;
        }
    }

    private Node findNode(Node root, String nodeName) {
        if (root == null) {
            return null;
        }

        if (nodeName.equals(root.getNodeName())) {
            return root;
        }

        Node child = root.getFirstChild();

        while (child != null) {
            Node result = findNode(child, nodeName);

            if (result != null) {
                return result;
            }

            child = child.getNextSibling();
        }

        return null;
    }

    private String sanitizeTextureName(String value) {
        return value
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_");
    }

    private void releaseFrames(List<AnimatedTextureFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }

        var textureManager = Minecraft.getInstance().getTextureManager();

        for (AnimatedTextureFrame frame : frames) {
            if (frame == null || frame.texture() == null) {
                continue;
            }

            textureManager.release(frame.texture());
        }
    }
}