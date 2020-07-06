package com.mes.price_intelligence.web.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

/**
 * Created by mesar on 6/22/2020
 */
@Data
@AllArgsConstructor
@Getter
@ToString
public class HttpResponse {

    private final HttpStatus httpStatus;
    private final ResponseType type;
    private final String message;

    public enum ResponseType {
        SUCCESS, FAILURE;
    }
}
