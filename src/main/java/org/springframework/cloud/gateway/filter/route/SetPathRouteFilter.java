package org.springframework.cloud.gateway.filter.route;

import java.net.URI;
import java.util.Map;

import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.UriTemplate;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getAttribute;
import static org.springframework.cloud.gateway.handler.predicate.UrlRoutePredicate.URL_PREDICATE_VARS_ATTR;

/**
 * @author Spencer Gibb
 */
public class SetPathRouteFilter implements RouteFilter {

	@Override
	@SuppressWarnings("unchecked")
	public WebFilter apply(String... args) {
		validate(1, args);
		UriTemplate uriTemplate = new UriTemplate(args[0]);

		return (exchange, chain) -> {
			Map<String, String> variables = getAttribute(exchange, URL_PREDICATE_VARS_ATTR, Map.class);
			ServerHttpRequest req = exchange.getRequest();
			URI uri = uriTemplate.expand(variables);
			String newPath = uri.getPath();

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
