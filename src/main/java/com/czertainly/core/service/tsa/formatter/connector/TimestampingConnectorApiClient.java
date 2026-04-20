package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.clients.BaseApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.DataAttribute;
import com.czertainly.api.model.connector.signatures.formatter.FormatDtbsResponseDto;
import com.czertainly.api.model.connector.signatures.formatter.FormattedResponseDto;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatDtbsRequestDto;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatResponseRequestDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.net.ssl.TrustManager;
import java.util.List;

public class TimestampingConnectorApiClient extends BaseApiClient {

    private static final String ATTRIBUTES_PATH = "/v1/signatureProvider/formatting/attributes";
    private static final String DTBS_PATH = "/v1/signatureProvider/formatting/formatDtbs";
    private static final String RESPONSE_PATH = "/v1/signatureProvider/formatting/formatResponse";

    public TimestampingConnectorApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        super(webClient, defaultTrustManagers);
    }

    public List<DataAttribute> listFormatterAttributes(ConnectorDto connector) throws ConnectorException {
        var request = prepareRequest(HttpMethod.GET, connector, true);
        return processRequest(r -> r
                        .uri(connector.getUrl() + ATTRIBUTES_PATH)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<List<DataAttribute>>() {})
                        .block()
                        .getBody(),
                request, connector);
    }

    public FormatDtbsResponseDto formatDtbs(ConnectorDto connector, TimestampingFormatDtbsRequestDto requestDto) throws ConnectorException {
        var request = prepareRequest(HttpMethod.POST, connector, true);
        return processRequest(r -> r
                        .uri(connector.getUrl() + DTBS_PATH)
                        .body(Mono.just(requestDto), TimestampingFormatDtbsRequestDto.class)
                        .retrieve()
                        .toEntity(FormatDtbsResponseDto.class)
                        .block()
                        .getBody(),
                request, connector);
    }

    public FormattedResponseDto formatSigningResponse(ConnectorDto connector, TimestampingFormatResponseRequestDto requestDto) throws ConnectorException {
        var request = prepareRequest(HttpMethod.POST, connector, true);
        return processRequest(r -> r
                        .uri(connector.getUrl() + RESPONSE_PATH)
                        .body(Mono.just(requestDto), TimestampingFormatResponseRequestDto.class)
                        .retrieve()
                        .toEntity(FormattedResponseDto.class)
                        .block()
                        .getBody(),
                request, connector);
    }
}
