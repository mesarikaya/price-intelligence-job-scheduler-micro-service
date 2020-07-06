package com.mes.price_intelligence.services;

import com.mes.price_intelligence.web.domain.Auth;
import com.mes.price_intelligence.web.domain.HttpResponse;
import reactor.core.publisher.Mono;

/**
 * Created by mesar on 6/21/2020
 */
public interface WebProductScraperServices {

    Mono<Void> initiateTasks();

    Mono<Auth> obtainAccessToken();

    Mono<HttpResponse> pullProductDetails(String endpoint, String token);
}
