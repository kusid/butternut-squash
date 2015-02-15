package com.kusid.butternut.rest;

import com.kusid.butternut.constants.CommonConstants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * DESC:
 */
public final class ImageThumbnailersRestApiHelper {

    private static ImageThumbnailersRestApiHelper restApiHelper = null;

    /**
     * prevent instantiation.
     */
    private ImageThumbnailersRestApiHelper() {

    }

    /**
     * @return Singleton instance of itself.
     */
    public static ImageThumbnailersRestApiHelper getInstance() {
        if (null == restApiHelper) {
            restApiHelper = new ImageThumbnailersRestApiHelper();
        }
        return restApiHelper;
    }

    /**
     * Read POST uploads and write them to a temp file, returning the Path to that file.
     *
     * @param content
     * @return
     * @throws IOException
     */
    public Path readUpload(ByteBuf content) throws IOException {
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        content.release();

        // write to a temp file
        Path imgIn = Files.createTempFile("upload", ".jpg");
        Files.write(imgIn, bytes);

        imgIn.toFile().deleteOnExit();

        return imgIn;
    }

    /**
     * Create an HTTP 400 bad request response.
     *
     * @param msg
     * @return
     */
    public FullHttpResponse badRequest(String msg) {
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST);
        resp.content().writeBytes(msg.getBytes());
        resp.headers().set(CONTENT_TYPE, "text/plain");
        resp.headers().set(CONTENT_LENGTH, resp.content().readableBytes());
        return resp;
    }

    /**
     * Create an HTTP 301 redirect response.
     *
     * @return
     */
    public FullHttpResponse redirect() {
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
        resp.headers().set(CONTENT_LENGTH, 0);
        resp.headers().set(LOCATION, CommonConstants.IMG_THUMBNAIL_URI);
        return resp;
    }

    /**
     * Create an HTTP 200 response that contains the data of the thumbnailed image.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public FullHttpResponse serveImage(Path path) throws IOException {
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK);

        RandomAccessFile f = new RandomAccessFile(path.toString(), "r");
        resp.headers().set(CONTENT_TYPE, "image/jpeg");
        resp.headers().set(CONTENT_LENGTH, f.length());

        byte[] bytes = Files.readAllBytes(path);
        resp.content().writeBytes(bytes);

        return resp;
    }

}
