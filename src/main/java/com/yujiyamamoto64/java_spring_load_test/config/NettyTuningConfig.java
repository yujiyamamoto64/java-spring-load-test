package com.yujiyamamoto64.java_spring_load_test.config;

import java.time.Duration;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.netty.channel.ChannelOption;

@Configuration
public class NettyTuningConfig {

	@Bean
	public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
		var factory = new NettyReactiveWebServerFactory();

		factory.addServerCustomizers(httpServer -> httpServer
			.compress(true)
			.idleTimeout(Duration.ofSeconds(15))
			.httpRequestDecoder(spec -> spec
				.initialBufferSize(512)
				.maxHeaderSize(16 * 1024)
				.maxChunkSize(16 * 1024))
			.tcpConfiguration(tcpServer -> tcpServer
				.option(ChannelOption.SO_REUSEADDR, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.option(ChannelOption.SO_RCVBUF, 1_048_576)
				.option(ChannelOption.SO_SNDBUF, 1_048_576)));

		return factory;
	}
}
