package io.snyk.languageserver.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snyk.eclipse.plugin.utils.FileDownloadResponseHandler;
import io.snyk.eclipse.plugin.utils.LsMetadataResponseHandler;
import io.snyk.languageserver.LsBaseTest;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.core.runtime.IProgressMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LsDownloaderTest extends LsBaseTest {
    CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        httpClient = mock(CloseableHttpClient.class);
        mockMetadata();
    }

    @Test
    void downloadShouldFailWhenShaWrongAndFileShouldNotBeOverwritten() throws IOException {
        byte[] expectedLsContent = Files.readAllBytes(utils.getLSFile().toPath());

        LsDownloader cut = new LsDownloader(utils, httpClient);
        File testFile = File.createTempFile("download-test", "tmp");
        testFile.deleteOnExit();
        // sha corresponds to file content (`echo "test 123" | sha256sum`)
        Files.write(testFile.toPath(), "test 123".getBytes(StandardCharsets.UTF_8));
        InputStream shaStream = new ByteArrayInputStream("wrong sha".getBytes());
        mockShaStream(shaStream);
        when(httpClient.execute(any(LsDownloadRequest.class), any(FileDownloadResponseHandler.class)))
                .thenReturn(testFile);

        assertThrows(ChecksumVerificationException.class, () -> cut.download(mock(IProgressMonitor.class)));

        File lsFile = utils.getLSFile();
        assertTrue(lsFile.exists());
        assertArrayEquals(Files.readAllBytes(lsFile.toPath()), expectedLsContent);
        verify(httpClient, times(1)).execute(any(LsVersionRequest.class), any(LsMetadataResponseHandler.class));
        verify(httpClient, times(1)).execute(any(LsShaRequest.class));
    }

    @Test
    void downloadShouldIssueDownloadRequestForShaAndBinary() throws IOException {
        LsDownloader cut = new LsDownloader(utils, httpClient);
        Path source = Paths.get("src/test/resources/ls-dummy-binary");
        var testFile = Files.createTempFile("download-test", "tmp").toFile();
        testFile.deleteOnExit();
        Files.copy(source, testFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        var fileContent = Files.readAllBytes(testFile.toPath());
        var checksums = "1a69d74e3b70cd2d386145bf6eb3882196525b7ca2b42e3fc87c67de873abf72  snyk-ls_testVersion_windows_386.exe\n"
                + "2b418a5d0573164b4f93188fc94de0332fc0968e7a8439b01f530a4cdde1dcf2  snyk-ls_testVersion_linux_amd64\n"
                + "5f53be6dac86284d58322dff0dd6b9e3a6002e593d3a16fc61e23d82b428915a  snyk-ls_testVersion_darwin_arm64\n";
        InputStream shaStream = new ByteArrayInputStream(checksums.getBytes());
        mockShaStream(shaStream);
        when(httpClient.execute(any(LsDownloadRequest.class), any(FileDownloadResponseHandler.class)))
                .thenReturn(testFile);

        cut.download(mock(IProgressMonitor.class));

        verify(httpClient, times(1)).execute(any(LsVersionRequest.class), any(LsMetadataResponseHandler.class));
        verify(httpClient, times(1)).execute(any(LsShaRequest.class));
        verify(httpClient, times(1)).execute(any(LsDownloadRequest.class), any(FileDownloadResponseHandler.class));
        File lsFile = utils.getLSFile();
        assertTrue(lsFile.exists());
        assertArrayEquals(Files.readAllBytes(lsFile.toPath()), fileContent);
    }

    @Test
    void getVersionShouldDownloadAndExtractTheLatestVersion() {
        LsDownloader cut = new LsDownloader(utils, httpClient);
        var metadataObject = mockMetadata();
        var actual = cut.getVersion();
        assertEquals(metadataObject.getVersion(), actual);
    }

    private LsMetadata mockMetadata() {
        String metadata = "{\"project_name\":\"snyk-ls\","
                + "\"tag\":\"v20220303.140906\","
                + "\"previous_tag\":\"v20220303.132432\","
                + "\"version\":\"testVersion\","
                + "\"commit\":\"419dbb08b663ec1d08a5bd416afcc0393703d4b7\","
                + "\"date\":\"2022-03-03T14:21:22.279851+01:00\","
                + "\"runtime\":{\"goos\":\"darwin\",\"goarch\":\"amd64\"}}";
        ObjectMapper mapper = new ObjectMapper();
        try {
            var metadataObject = mapper.readValue(metadata.getBytes(), LsMetadata.class);
            when(httpClient.execute(any(LsVersionRequest.class), any(LsMetadataResponseHandler.class)))
                    .thenReturn(metadataObject);
            return metadataObject;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mockShaStream(InputStream shaStream) throws IOException {
        CloseableHttpResponse shaHttpResponseMock = mock(CloseableHttpResponse.class);
        when(httpClient.execute(any(LsShaRequest.class))).thenReturn(shaHttpResponseMock);
        StatusLine statusLineMock = mock(StatusLine.class);
        when(shaHttpResponseMock.getStatusLine()).thenReturn(statusLineMock);
        when(statusLineMock.getStatusCode()).thenReturn(200);
        HttpEntity shaHttpEntityMock = mock(HttpEntity.class);
        when(shaHttpResponseMock.getEntity()).thenReturn(shaHttpEntityMock);
        when(shaHttpEntityMock.getContent()).thenReturn(shaStream);
    }

}
