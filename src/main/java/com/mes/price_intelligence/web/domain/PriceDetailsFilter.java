package com.mes.price_intelligence.web.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

/**
 * Created by mesar on 6/22/2020
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class PriceDetailsFilter implements Serializable {

    private static final long serialVersionUID = -2788297858855403547L;

    @JsonProperty("is_allowed")
    private boolean isAllowed;

    @JsonProperty("batch_size")
    private int batchSize;
}
