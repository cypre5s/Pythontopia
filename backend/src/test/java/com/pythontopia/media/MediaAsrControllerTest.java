package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.media.dto.AsrResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class MediaAsrControllerTest {

  private final XfyunIatService iatService = mock(XfyunIatService.class);
  private final MediaAsrController controller = new MediaAsrController(iatService);

  @Test
  void disabledAsrReturnsUnavailableWithoutInspectingUploadedAudio() throws Exception {
    MultipartFile audio = mock(MultipartFile.class);
    CurrentUserPrincipal user = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    AsrResponse expected = AsrResponse.unavailable();
    when(iatService.isEnabled()).thenReturn(false);
    when(iatService.transcribe(eq(7L), isNull(), isNull())).thenReturn(expected);

    AsrResponse response = controller.transcribe(user, audio);

    assertThat(response.degraded()).isTrue();
    assertThat(response.text()).isEmpty();
    verify(iatService).isEnabled();
    verifyNoInteractions(audio);
  }

  @Test
  void rejectsAudioAboveEightMibBeforeReadingItsBytes() throws Exception {
    MultipartFile audio = mock(MultipartFile.class);
    when(iatService.isEnabled()).thenReturn(true);
    when(audio.isEmpty()).thenReturn(false);
    when(audio.getSize()).thenReturn(8L * 1024 * 1024 + 1);

    assertThatThrownBy(
            () ->
                controller.transcribe(
                    new CurrentUserPrincipal(7L, "student", Role.STUDENT), audio))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error -> {
              BusinessException businessException = (BusinessException) error;
              assertThat(businessException.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
              assertThat(businessException.code()).isEqualTo("ASR_AUDIO_TOO_LARGE");
            });

    verify(audio, never()).getBytes();
    verify(iatService, never()).transcribe(anyLong(), any(byte[].class), anyString());
  }

  @Test
  void transcribesAudioAtOrBelowEightMib() throws Exception {
    MultipartFile audio =
        new MockMultipartFile("audio", "question.webm", "audio/webm", new byte[] {1, 2, 3});
    CurrentUserPrincipal user = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    AsrResponse expected = AsrResponse.of("你好");
    when(iatService.isEnabled()).thenReturn(true);
    when(iatService.transcribe(7L, new byte[] {1, 2, 3}, "audio/webm")).thenReturn(expected);

    AsrResponse actual = controller.transcribe(user, audio);

    assertThat(actual).isEqualTo(expected);
    verify(iatService).transcribe(7L, new byte[] {1, 2, 3}, "audio/webm");
  }
}
