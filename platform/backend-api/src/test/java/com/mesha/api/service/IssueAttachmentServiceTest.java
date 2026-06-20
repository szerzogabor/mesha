package com.mesha.api.service;

import com.mesha.api.model.Issue;
import com.mesha.api.model.IssueAttachment;
import com.mesha.api.model.User;
import com.mesha.api.repository.IssueAttachmentRepository;
import com.mesha.api.repository.IssueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssueAttachmentServiceTest {

    @Mock private IssueAttachmentRepository attachmentRepository;
    @Mock private IssueRepository issueRepository;

    private IssueAttachmentService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new IssueAttachmentService(attachmentRepository, issueRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ---- upload ----

    @Test
    void upload_savesAttachmentWithCorrectFields() {
        UUID issueId = UUID.randomUUID();
        Issue issue = new Issue();
        User uploader = new User();

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", new byte[1024]);
        IssueAttachment saved = new IssueAttachment();
        when(attachmentRepository.save(any())).thenReturn(saved);

        IssueAttachment result = service.upload(issueId, file, uploader);

        assertThat(result).isNotNull();
        ArgumentCaptor<IssueAttachment> captor = ArgumentCaptor.forClass(IssueAttachment.class);
        verify(attachmentRepository).save(captor.capture());
        IssueAttachment toSave = captor.getValue();
        assertThat(toSave.getFileName()).isEqualTo("diagram.png");
        assertThat(toSave.getContentType()).isEqualTo("image/png");
        assertThat(toSave.getFileSize()).isEqualTo(1024);
        assertThat(toSave.getIssue()).isSameAs(issue);
        assertThat(toSave.getUploadedBy()).isSameAs(uploader);
    }

    @Test
    void upload_throwsWhenIssueNotFound() {
        UUID issueId = UUID.randomUUID();
        when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "f.txt", "text/plain", new byte[10]);
        assertThatThrownBy(() -> service.upload(issueId, file, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void upload_throwsWhenFileIsEmpty() {
        UUID issueId = UUID.randomUUID();
        Issue issue = new Issue();
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.upload(issueId, empty, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_throwsWhenFileTooLarge() {
        UUID issueId = UUID.randomUUID();
        Issue issue = new Issue();
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        byte[] big = new byte[11 * 1024 * 1024]; // 11 MB
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        assertThatThrownBy(() -> service.upload(issueId, file, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void upload_sanitizesFileNamePath() {
        UUID issueId = UUID.randomUUID();
        Issue issue = new Issue();
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(attachmentRepository.save(any())).thenReturn(new IssueAttachment());

        MockMultipartFile file = new MockMultipartFile("file", "C:\\Users\\alice\\docs\\plan.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[100]);
        service.upload(issueId, file, new User());

        ArgumentCaptor<IssueAttachment> captor = ArgumentCaptor.forClass(IssueAttachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("plan.docx");
    }

    @Test
    void upload_fallsBackToOctetStreamWhenNoContentType() {
        UUID issueId = UUID.randomUUID();
        Issue issue = new Issue();
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(attachmentRepository.save(any())).thenReturn(new IssueAttachment());

        MockMultipartFile file = new MockMultipartFile("file", "data.bin", null, new byte[50]);
        service.upload(issueId, file, new User());

        ArgumentCaptor<IssueAttachment> captor = ArgumentCaptor.forClass(IssueAttachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("application/octet-stream");
    }

    // ---- list ----

    @Test
    void list_delegatesToRepository() {
        UUID issueId = UUID.randomUUID();
        List<IssueAttachment> expected = List.of(new IssueAttachment());
        when(attachmentRepository.findAllByIssueIdOrderByCreatedAtAsc(issueId)).thenReturn(expected);

        assertThat(service.list(issueId)).isSameAs(expected);
    }

    // ---- delete ----

    @Test
    void delete_removesAttachmentWhenIssueMatches() {
        UUID issueId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        Issue issue = new Issue();
        setId(issue, issueId);

        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssue(issue);

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.delete(attachmentId, issueId);

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void delete_throwsNotFoundWhenAttachmentBelongsToDifferentIssue() {
        UUID issueId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        Issue otherIssue = new Issue();
        setId(otherIssue, otherId);

        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssue(otherIssue);

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.delete(attachmentId, issueId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void delete_throwsNotFoundWhenAttachmentDoesNotExist() {
        UUID attachmentId = UUID.randomUUID();
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(attachmentId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** Reflectively sets the UUID id field since there is no public setter. */
    private static void setId(Issue issue, UUID id) {
        try {
            var field = Issue.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(issue, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
