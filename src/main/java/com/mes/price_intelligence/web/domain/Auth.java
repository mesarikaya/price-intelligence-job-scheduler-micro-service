package com.mes.price_intelligence.web.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * Created by mesar on 6/21/2020
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Auth implements Serializable {

    private static final long serialVersionUID = 5277051940247997003L;

    @JsonProperty("refresh")
    private String refresh;
    @JsonProperty("access")
    private String access;
}
