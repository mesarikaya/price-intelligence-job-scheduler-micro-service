package com.mes.price_intelligence.web.controllers;

import com.mes.price_intelligence.services.WebProductScraperServices;
import com.mes.price_intelligence.web.domain.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Created by mesar on 6/21/2020
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs/productdetails/")
public class PriceIntelligenceController {

    @Value("${base_url}")
    private String baseUrl;

    @Value("#{${auth_urls}}")
    private Map<String, String> authUrls;

    @Value("#{${product_details_api_urls_map}}")
    private Map<String, String> productDetailsEndpointUrls;

    private final WebProductScraperServices webProductScraperServices;

    @GetMapping(value = "activate/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Mono<HttpResponse> activateJobById(@PathVariable(value = "id") String id){
        log.info("Called the method via controller");
        return webProductScraperServices.obtainAccessToken()
                .flatMap(auth -> webProductScraperServices.pullProductDetails(id, auth.getAccess()))
                .switchIfEmpty(Mono.defer(() -> Mono.empty()));
    }

    @GetMapping(value = "activate/all", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Mono<HttpResponse> activateAllJobs(){
        log.info("Called the method via controller");

        try {
            return webProductScraperServices.initiateTasks().thenReturn(new HttpResponse(HttpStatus.OK,
                    HttpResponse.ResponseType.SUCCESS,
                    "All endpoints have been successfully activated!"));
        } catch (RuntimeException webClientException) {
            log.info("Batch job activation has failed");
            return Mono.just(new HttpResponse(HttpStatus.BAD_REQUEST,
                    HttpResponse.ResponseType.FAILURE,
                    "All endpoints have been successfully activated!"));
        }
    }
}
