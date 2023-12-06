package com.example.demo;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
	
	@Bean
	public RouteLocator myRoutes(RouteLocatorBuilder builder) {
		return builder.routes()
        .route(p -> p
            .path("/get")
            .filters(f -> f.addRequestHeader("Hello", "World")
				.requestRateLimiter(r -> 
					r.setRateLimiter(redisRateLimiter()) // rate limiter settings
					.setStatusCode(HttpStatus.TOO_MANY_REQUESTS) // Rate limit exceeded, return 429 response code
					.setDenyEmptyKey(false) // No IP
					.setKeyResolver(new SimpleClientAddressResolver() // The IP address key resolver
				)
			))
            .uri("http://httpbin.org:80"))
		.route(p -> p
            .host("*.circuitbreaker.com")
            .filters(f -> f.circuitBreaker(config -> 
				config.setName("mycmd")
				.setFallbackUri("forward:/fallback")
			))
            .uri("http://httpbin.org:80"))
        .build();
	}

	// Identify each client by IP address
	@Component
	public class SimpleClientAddressResolver implements KeyResolver {
		@Override
		public Mono<String> resolve(ServerWebExchange exchange) {
			return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
				.map(InetSocketAddress::getAddress)
				.map(InetAddress::getHostAddress)
				.map(Mono::just)
				.orElse(Mono.empty());
		}
	}

	@Bean
	public RedisRateLimiter redisRateLimiter() {
		// replenish rate - requests per second
		// burst rate - stop handling requests until enough time has passes. If replenish rate is 500 and burst rate is
		//			    1000, wait until two seconds pass since start of the 1000 request burst
		return new RedisRateLimiter(1, 1, 1);
	}

	/**
	 * Our exception response handler
	 * @return
	 */
	@RequestMapping("/fallback")
	public Mono<String> fallback() {
		return Mono.just("Something went wrong.");
	}
	@RequestMapping("/rateExceeded")
	public Mono<String> rateExceeded() {
		return Mono.just("Rate limit exceeded.");
	}
}
