package com.yujiyamamoto64.java_spring_load_test.config;

import java.time.Duration;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.netty.channel.ChannelOption;
import reactor.netty.resources.LoopResources;

@Configuration
public class NettyTuningConfig {

	@Bean
	public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
		var factory = new NettyReactiveWebServerFactory();
		var cores = Runtime.getRuntime().availableProcessors();
		var loopResources = LoopResources.create("transfer-http", Math.max(4, cores * 2), true);

		factory.addServerCustomizers(httpServer -> httpServer
			.runOn(loopResources)
			.compress(true)
			.idleTimeout(Duration.ofSeconds(20))
			.httpRequestDecoder(spec -> spec
				.maxHeaderSize(16 * 1024)
				.maxChunkSize(16 * 1024))
			.tcpConfiguration(tcpServer -> tcpServer
				.option(ChannelOption.SO_BACKLOG, 65_535)
				.option(ChannelOption.SO_REUSEADDR, true)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.option(ChannelOption.SO_RCVBUF, 1_048_576)
				.option(ChannelOption.SO_SNDBUF, 1_048_576)));

		return factory;
	}
}
