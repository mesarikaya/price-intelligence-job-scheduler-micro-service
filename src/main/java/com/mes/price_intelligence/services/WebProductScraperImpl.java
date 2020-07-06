package com.mes.price_intelligence.services;

import com.mes.price_intelligence.web.domain.Auth;
import com.mes.price_intelligence.web.domain.HttpResponse;
import com.mes.price_intelligence.web.domain.PriceDetailsFilter;
import com.mes.price_intelligence.web.domain.User;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;
import reactor.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Created by mesar on 6/21/2020
 */
@Slf4j
@Service
public class WebProductScraperImpl implements WebProductScraperServices{

    @Value("${spring.datasource.username}")
    private String userName;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${base_url}")
    private String baseUrl;

    @Value("#{${auth_urls}}")
    private Map<String, String> authUrls;

    @Value("#{${product_details_api_urls_map}}")
    private Map<String, String> productDetailsEndpointUrls;

    @Value("${wiretap.enabled}")
    private boolean wireTapEnabled;

    @Value("${tcp.connectionTimeOutMilis}")
    private int connectionTimeoutMilis;

    private WebClient createWebClient(){
        ConnectionProvider connectionProvider = ConnectionProvider.builder("fixed")
                .fifo()
                .maxConnections(1000)
                .maxIdleTime(Duration.ofSeconds(120))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .metrics(true)
                .build();

        TcpClient tcpClient = TcpClient.create(connectionProvider)
                .wiretap(wireTapEnabled)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMilis)
                .doOnConnected(
                        c -> c.addHandlerLast(new ReadTimeoutHandler(3000, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(3000, TimeUnit.MILLISECONDS))
                                .addHandler(new IdleStateHandler(3000, 5000,
                                        5000,TimeUnit.MILLISECONDS)));

        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient).compress(true)))
                .baseUrl(baseUrl).build();

        return client;
    }

    @Scheduled(cron = "${task_start_time_cron_expression}", zone="${zone}")
    @Override
    public Mono<Void> initiateTasks(){

        productDetailsEndpointUrls.forEach((key, value)->{
            log.info("Scheduled run for endpoint:{} ",  value);

            obtainAccessToken()
                    .flatMap(auth -> {
                        log.info("Access token url: " + baseUrl + authUrls.get("access"));
                        return  this.pullProductDetails(key, auth.getAccess());
                    })
                    .delayElement(Duration.ofMillis(500))
                    .doOnError((error -> {
                        log.info("Token request has failed ", error);
                    }))
                    .switchIfEmpty(Mono.defer(() -> Mono.empty()))
                    .subscribe();
        });

        return Mono.empty();
    }

    @Override
    public Mono<Auth> obtainAccessToken() {

        Mono<Auth> authTokens = null;
        try {
            User user = new User(userName, password);
            String accessTokenUrl = Optional.ofNullable(authUrls.
                    getOrDefault("access","INVALID_ENDPOINT")).orElse("INVALID_ENDPOINT");
            String SEND_URI = baseUrl + accessTokenUrl;
            log.info("Making access token call with endpoint: ", SEND_URI);

            if (accessTokenUrl != "INVALID_ENDPOINT"){
                WebClient client = this.createWebClient();
                authTokens = client
                        .post()
                        .uri(SEND_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(user))
                        .retrieve()
                        .bodyToMono(Auth.class)
                        .onErrorReturn(new Auth("Wrong credentials", "Wrong credentials"))
                        .doOnSuccess( message -> {
                            log.info("Success in requesting tokens {}", message);
                        })
                        .doOnError((error -> {
                            log.info("Token request has failed ", error);
                        }));
            }else{
                return Mono.just(new Auth("Wrong credentials", "Wrong credentials"));
            }
        } catch (WebClientException webClientException) {
            log.info("Unexpected error occurred in token retrieval: " + webClientException);
            return Mono.just(new Auth("", "Wrong credentials"));
        }

        return authTokens;
    }

    @Override
    public Mono<HttpResponse> pullProductDetails(String key, String token){

        log.info("INSIDE pull PRoduct Details");
        PriceDetailsFilter filter = PriceDetailsFilter.builder()
                .isAllowed(true)
                .batchSize(100)
                .build();

        String endpoint = Optional.ofNullable(productDetailsEndpointUrls.
                getOrDefault(key,"INVALID_ENDPOINT")).orElse("INVALID_ENDPOINT");

        Retry<?> fixedRetry = Retry.anyOf(RuntimeException.class)
                .randomBackoff(Duration.ofSeconds(30),Duration.ofSeconds(60))
                .retryMax(3)
                .doOnRetry((exception) -> {
                    log.info("Exception is {} .", exception);
                });

        final HttpResponse badHttpResponse = new HttpResponse(HttpStatus.BAD_REQUEST,
                HttpResponse.ResponseType.FAILURE,
                "Pull request failed for: " + endpoint);

        final HttpResponse goodHttpResponse = new HttpResponse(HttpStatus.OK,
                HttpResponse.ResponseType.SUCCESS,
                "Pull request is activated for: " + endpoint);

        if (endpoint != "INVALID_ENDPOINT"){
            log.info("Making GCL call with endpoint: "+ endpoint);
            return makeTaskStartCall(token, filter, endpoint, fixedRetry, badHttpResponse, goodHttpResponse);
        }else{
            log.info("NOT Making GCL call with endpoint INVALID REQUEST");
            return Mono.just(badHttpResponse);
        }
    }

    private Mono<HttpResponse> makeTaskStartCall(String token, PriceDetailsFilter filter,
                                                 String endpoint, Retry<?> fixedRetry,
                                                 HttpResponse badHttpResponse,
                                                 HttpResponse goodHttpResponse) {

        try {
            String SEND_URI = baseUrl + endpoint;
            log.info("Making GCL call with endpoint: "+ SEND_URI);
            WebClient client = this.createWebClient();
            return client
                    .post()
                    .uri(SEND_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(filter))
                    .exchange()
                    .flatMap(clientResponse -> {
                        System.out.println("Client response is {} " + clientResponse.statusCode());
                        if(clientResponse.statusCode().is2xxSuccessful()){
                            return Mono.just(goodHttpResponse);
                        }else{
                            log.info("Status code is: {}", clientResponse.statusCode());
                            return Mono.error(new RuntimeException());
                        }
                    }).retryWhen(fixedRetry)
                    .onErrorReturn(badHttpResponse);
        } catch (RuntimeException webClientException) {
            log.info("Unexpected error occurred in token retrieval: " + webClientException);
            return Mono.just(badHttpResponse);
        }
    }
}
