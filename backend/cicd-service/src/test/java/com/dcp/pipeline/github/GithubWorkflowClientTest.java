package com.dcp.pipeline.github;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GithubWorkflowClientTest {

    @Test
    void nonGithubRepositoryIsRejectedBeforeHttp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubWorkflowClient client = new GithubWorkflowClient(builder.build());

        ApiException exception = assertThrows(ApiException.class, () -> client.dispatch(
                "token",
                "https://gitlab.com/acme/demo",
                "dcp-pipeline.yml",
                "main",
                UUID.randomUUID()));

        assertEquals(400, exception.status());
        server.verify();
    }

    @Test
    void dispatchesOnlyAfterTheRepositoryIsAccepted() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubWorkflowClient client = new GithubWorkflowClient(builder.build());
        UUID pipelineId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        server.expect(requestTo("https://api.github.com/repos/acme/demo/actions/workflows/dcp-pipeline.yml/dispatches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.dispatch("token", "https://github.com/acme/demo/", "dcp-pipeline.yml", "main", pipelineId);

        server.verify();
    }
}
