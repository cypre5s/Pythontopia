package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.dto.TutorWorkflowCheckpointRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TutorWorkflowInternalCheckpointControllerTest {

  private final TutorWorkflowService service = mock(TutorWorkflowService.class);
  private final TutorWorkflowInternalCheckpointController controller =
      new TutorWorkflowInternalCheckpointController(
          service, new GraphServiceProperties("http://graph", "http://rag", "internal-token"));

  @Test
  void acceptsOnlyInternalUserSubjectAndDelegatesOwnerScope() throws Exception {
    UUID runId = UUID.randomUUID();
    String fingerprint = "a".repeat(64);
    var checkpoint =
        new ObjectMapper()
            .readTree(
                "{\"stage_index\":0,\"stage_name\":\"validate_transition\","
                    + "\"team_state\":{},\"workflow_state\":\"state\"}");

    var response =
        controller.save(
            "wf-7-476-abc",
            runId,
            new TutorWorkflowCheckpointRequest(fingerprint, checkpoint),
            "internal-token",
            "user:73");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(service)
        .persistAutoGenCheckpoint(73L, "wf-7-476-abc", runId, fingerprint, checkpoint);
  }

  @Test
  void rejectsWrongInternalTokenBeforeAccessingSession() throws Exception {
    var checkpoint = new ObjectMapper().readTree("{}");

    assertThatThrownBy(
            () ->
                controller.save(
                    "wf-7-476-abc",
                    UUID.randomUUID(),
                    new TutorWorkflowCheckpointRequest("a".repeat(64), checkpoint),
                    "wrong-token",
                    "user:73"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid internal service token");
  }

  @Test
  void rejectsMalformedInternalSubjectBeforeAccessingSession() throws Exception {
    var checkpoint = new ObjectMapper().readTree("{}");

    assertThatThrownBy(
            () ->
                controller.save(
                    "wf-7-476-abc",
                    UUID.randomUUID(),
                    new TutorWorkflowCheckpointRequest("a".repeat(64), checkpoint),
                    "internal-token",
                    "user:0"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid internal subject");
  }
}
