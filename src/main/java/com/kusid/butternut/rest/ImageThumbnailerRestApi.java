package com.kusid.butternut.rest;

import io.netty.handler.codec.http.*;
import reactor.core.Reactor;
import reactor.event.Event;
import reactor.function.Consumer;
import reactor.net.NetChannel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contains the necessary Consumers for handling HTTP requests.
 */
public class ImageThumbnailerRestApi {

    /**
     * Accept an image upload via POST and notify a Reactor that the image needs to be thumbnailed. Asynchronously respond
     * to the client when the thumbnailing has completed.
     *
     * @param channel   the channel on which to send an HTTP response
     * @param thumbnail a reference to the shared thumbnail path
     * @param reactor   the Reactor on which to publish events
     * @return a consumer to handle HTTP requests
     */
    public static Consumer<FullHttpRequest> thumbnailImage(NetChannel<FullHttpRequest, FullHttpResponse> channel,
                                                           AtomicReference<Path> thumbnail,
                                                           Reactor reactor) {
        return req -> {

            ImageThumbnailersRestApiHelper restApiHelper = ImageThumbnailersRestApiHelper.getInstance();

            if (req.getMethod() != HttpMethod.POST) {
                channel.send(restApiHelper.badRequest(req.getMethod() + " not supported for this URI"));
                return;
            }

            // write to a temp file
            Path imgIn = null;
            try {
                imgIn = restApiHelper.readUpload(req.content());
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }

            // Asynchronously thumbnail the image to 250px on the long side
            reactor.sendAndReceive("thumbnail", Event.wrap(imgIn), ev -> {
                thumbnail.set(ev.getData());
                channel.send(restApiHelper.redirect());
            });
        };
    }

    /**
     * Respond to GET requests and serve the thumbnailed image, a reference to which is kept in the given {@literal
     * AtomicReference}.
     *
     * @param channel   the channel on which to send an HTTP response
     * @param thumbnail a reference to the shared thumbnail path
     * @return a consumer to handle HTTP requests
     */
    public static Consumer<FullHttpRequest> serveThumbnailImage(NetChannel<FullHttpRequest, FullHttpResponse> channel,
                                                                AtomicReference<Path> thumbnail) {
        return req -> {
            ImageThumbnailersRestApiHelper restApiHelper = ImageThumbnailersRestApiHelper.getInstance();

            if (req.getMethod() != HttpMethod.GET) {
                channel.send(restApiHelper.badRequest(req.getMethod() + " not supported for this URI"));
            } else {
                try {
                    channel.send(restApiHelper.serveImage(thumbnail.get()));
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        };
    }

    /**
     * Respond to errors occurring on a Reactor by redirecting them to the client via an HTTP 500 error response.
     *
     * @param channel the channel on which to send an HTTP response
     * @return a consumer to handle HTTP requests
     */
    public static Consumer<Throwable> errorHandler(NetChannel<FullHttpRequest, FullHttpResponse> channel) {
        return ev -> {
            DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
            resp.content().writeBytes(ev.getMessage().getBytes());
            resp.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain");
            resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, resp.content().readableBytes());
            channel.send(resp);
        };
    }

}
