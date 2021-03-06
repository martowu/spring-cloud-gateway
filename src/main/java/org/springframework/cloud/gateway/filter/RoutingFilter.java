package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.http.client.HttpClient;

/**
 * @author Spencer Gibb
 */
public class RoutingFilter implements GlobalFilter, Ordered {

	private final HttpClient httpClient;

	public RoutingFilter(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public int getOrder() {
		return 2000000;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		Optional<URI> requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		ServerHttpRequest request = exchange.getRequest();

		final HttpMethod method = HttpMethod.valueOf(request.getMethod().toString());
		final String url = requestUrl.get().toString();

		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		request.getHeaders().forEach(httpHeaders::set);

		return this.httpClient.request(method, url, req ->
				req.options(NettyPipeline.SendOptions::flushOnEach)
						.headers(httpHeaders)
						.sendHeaders()
						.send(request.getBody()
								.map(DataBuffer::asByteBuffer)
								.map(Unpooled::wrappedBuffer)))
				.then(res -> {
					ServerHttpResponse response = exchange.getResponse();
					// put headers and status so filters can modify the response
					final HttpHeaders headers = new HttpHeaders();
					res.responseHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));

					response.getHeaders().putAll(headers);
					response.setStatusCode(HttpStatus.valueOf(res.status().code()));

					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write response later WriteResponseFilter
					exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
					return Mono.empty();
				}).then(chain.filter(exchange));
	}
}
