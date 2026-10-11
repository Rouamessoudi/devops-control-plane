package com.dcp.pipeline.api;

import com.dcp.pipeline.service.WebhookService;
import com.dcp.pipeline.service.WebhookService.Outcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(value = "/api/cicd/webhooks/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProblemDetail> github(@RequestBody byte[] body,
                                                 @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                                 @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery,
                                                 @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        Outcome outcome = webhooks.receive(body, signature, delivery, event);
        if (outcome == Outcome.SECRET_MISSING) {
            return unauthorized("Webhook secret is not configured");
        }
        if (outcome == Outcome.BAD_SIGNATURE) {
            return unauthorized("Webhook signature is invalid");
        }
        return ResponseEntity.accepted().build();
    }

    private static ResponseEntity<ProblemDetail> unauthorized(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }
}
