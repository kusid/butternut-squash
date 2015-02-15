package com.kusid.butternut.reactor;

import com.kusid.butternut.constants.CommonConstants;
import com.kusid.butternut.image.thumbnailer.ImageThumbnailer;
import com.kusid.butternut.rest.ImageThumbnailerRestApi;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.composable.Stream;
import reactor.core.spec.Reactors;
import reactor.net.NetServer;
import reactor.net.config.ServerSocketOptions;
import reactor.net.netty.NettyServerSocketOptions;
import reactor.net.netty.tcp.NettyTcpServer;
import reactor.net.tcp.spec.TcpServerSpec;
import reactor.spring.context.config.EnableReactor;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static reactor.event.selector.Selectors.$;

/**
 * Simple Spring Boot app to start a Reactor+Netty-based REST API server to make thumbnails of uploaded images.
 */

@EnableAutoConfiguration
@Configuration
@ComponentScan
@EnableReactor
public class ImageThumbnailerApp {

    @Bean
    public Reactor reactor(Environment env) {
        Reactor reactor = Reactors.reactor(env, Environment.THREAD_POOL);

        // Register instance on Reactor
        reactor.receive($("thumbnail"), new ImageThumbnailer(250));

        return reactor;
    }

    @Bean
    public ServerSocketOptions serverSocketOptions() {

        final int NETTY_OBJECT_AGGREGATOR = 16777216; //16 * 1024 * 1024

        return new NettyServerSocketOptions()
                .pipelineConfigurer(pipeline -> pipeline.addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(NETTY_OBJECT_AGGREGATOR)));
    }

    @Bean
    public NetServer<FullHttpRequest, FullHttpResponse> restApi(Environment env,
                                                                ServerSocketOptions opts,
                                                                Reactor reactor,
                                                                CountDownLatch closeLatch) throws InterruptedException {
        AtomicReference<Path> thumbnail = new AtomicReference<>();

        NetServer<FullHttpRequest, FullHttpResponse> server = new TcpServerSpec<FullHttpRequest, FullHttpResponse>(
                NettyTcpServer.class)
                .env(env).dispatcher("sync").options(opts)
                .consume(ch -> {
                    // filter requests by URI via the input Stream
                    Stream<FullHttpRequest> channelConsumer = ch.in();

                    // serve image thumbnail to browser
                    channelConsumer.filter((FullHttpRequest httpRequest) -> CommonConstants.IMG_THUMBNAIL_URI.equals(httpRequest.getUri()))
                            .when(Throwable.class, ImageThumbnailerRestApi.errorHandler(ch))
                            .consume(ImageThumbnailerRestApi.serveThumbnailImage(ch, thumbnail));

                    // take uploaded data and thumbnail it
                    channelConsumer.filter((FullHttpRequest httpRequest) -> CommonConstants.THUMBNAIL_REQ_URI.equals(httpRequest.getUri()))
                            .when(Throwable.class, ImageThumbnailerRestApi.errorHandler(ch))
                            .consume(ImageThumbnailerRestApi.thumbnailImage(ch, thumbnail, reactor));

                    // shutdown
                    channelConsumer.filter((FullHttpRequest httpRequest) -> "/shutdown".equals(httpRequest.getUri()))
                            .consume(req -> closeLatch.countDown());
                })
                .get();

        server.start().await();

        return server;
    }

    @Bean
    public CountDownLatch closeLatch() {
        return new CountDownLatch(1);
    }

    public static void main(String... args) throws InterruptedException {
        ApplicationContext ctx = SpringApplication.run(ImageThumbnailerApp.class, args);

        // Reactor's TCP servers are non-blocking so register a latch to keep the app from exiting.
        CountDownLatch closeLatch = ctx.getBean(CountDownLatch.class);
        closeLatch.await();
    }

}
