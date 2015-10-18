package io.linuxserver.davos.transfer.ftp.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import java.util.Vector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import io.linuxserver.davos.transfer.ftp.FTPFile;
import io.linuxserver.davos.transfer.ftp.exception.DownloadFailedException;
import io.linuxserver.davos.transfer.ftp.exception.FileListingException;
import io.linuxserver.davos.util.FileUtils;

public class SFTPConnectionTest {

    @InjectMocks
    private SFTPConnection sftpConnection;

    @Mock
    private FileUtils mockFileUtils;
    
    private ChannelSftp mockChannel;
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws SftpException {
        
        mockChannel = mock(ChannelSftp.class);

        Vector<LsEntry> lsEntries = createEntries();

        when(mockChannel.ls(anyString())).thenReturn(lsEntries);
        when(mockChannel.pwd()).thenReturn("a/directory");

        sftpConnection = new SFTPConnection(mockChannel);

        initMocks(this);
    }

    @Test
    public void listFilesMethodShouldCallOnChannelLsMethodForPresentDirectory() throws SftpException {

        sftpConnection.listFiles();

        verify(mockChannel).ls("a/directory/");
    }

    @Test
    public void whenListingFilesGivingRelativePathThenChannelLsMethodShouldUseGivenPath() throws SftpException {

        sftpConnection.listFiles("some/other/path");

        verify(mockChannel).ls("some/other/path/");
    }

    @Test
    public void ifUnderlyingChannelIsUnableToListFilesInPWDThenExceptionShouldBeCaughtAndRethrown() throws SftpException {

        expectedException.expect(FileListingException.class);
        expectedException.expectMessage(is(equalTo("Unable to list files in directory a/directory")));

        when(mockChannel.ls("a/directory/")).thenThrow(new SftpException(0, ""));

        sftpConnection.listFiles();
    }
    
    @Test
    public void lsEntriesReturnedFromChannelShouldBeParsedIntoFtpFileAndReturnedInList() {

        List<FTPFile> files = sftpConnection.listFiles();

        assertThat(files.get(0).getName()).isEqualTo("File 1");
        assertThat(files.get(0).getSize()).isEqualTo(123l);
        assertThat(files.get(0).getPath()).isEqualTo("a/directory/");
        assertThat(files.get(0).isDirectory()).isTrue();

        assertThat(files.get(1).getName()).isEqualTo("File 2");
        assertThat(files.get(1).getSize()).isEqualTo(456l);
        assertThat(files.get(1).getPath()).isEqualTo("a/directory/");
        assertThat(files.get(1).isDirectory()).isFalse();

        assertThat(files.get(2).getName()).isEqualTo("File 3");
        assertThat(files.get(2).getSize()).isEqualTo(789l);
        assertThat(files.get(2).getPath()).isEqualTo("a/directory/");
        assertThat(files.get(2).isDirectory()).isTrue();
    }

    @Test
    public void returnedFtpFilesShouldHaveCorrectModifiedDateTimesAgainstThem() {

        List<FTPFile> files = sftpConnection.listFiles();

        assertThat(files.get(0).getLastModified().toString("dd/MM/yyyy HH:mm:ss")).isEqualTo("11/03/2014 08:07:45");
        assertThat(files.get(1).getLastModified().toString("dd/MM/yyyy HH:mm:ss")).isEqualTo("12/03/2014 19:22:41");
        assertThat(files.get(2).getLastModified().toString("dd/MM/yyyy HH:mm:ss")).isEqualTo("08/02/2014 17:09:24");
    }
    
    @Test
    public void printingWorkingDirectoryShouldCallOnUnderlyingClientMethodToGetCurrentDirectory() throws SftpException {
        
        sftpConnection.currentDirectory();
        
        verify(mockChannel).pwd();
    }
    
    @Test
    public void printingWorkingDirectoryShouldReturnExactlyWhatTheUnderlyingClientReturns() {
        
        assertThat(sftpConnection.currentDirectory()).isEqualTo("a/directory");
    }
    
    @Test
    public void ifClientThrowsExceptionWhenTryingToGetWorkingDirectoryThenCatchExceptionAndRethrow() throws SftpException  {
        
        expectedException.expect(FileListingException.class);
        expectedException.expectMessage(is(equalTo("Unable to print the working directory")));
        
        when(mockChannel.pwd()).thenThrow(new SftpException(0, ""));
        
        sftpConnection.currentDirectory();
    }
    
    @Test
    public void downloadMethodShouldCallChannelGetMethodWithFtpFileNameAndDirectory() throws SftpException {

        FTPFile file = new FTPFile("name", 0, "path", 0, false);
        sftpConnection.download(file, "some/directory");

        verify(mockChannel).get("path/name", "some/directory/");
    }

    @Test
    public void downloadMethodShouldThrowDownloadFailedExceptionWhenChannelThrowsSftpConnection() throws SftpException {

        expectedException.expect(DownloadFailedException.class);
        expectedException.expectMessage(is(equalTo("Unable to download file path/to/file.txt")));

        doThrow(new SftpException(999, "")).when(mockChannel).get("path/to/file.txt", "some/directory/");

        sftpConnection.download(new FTPFile("file.txt", 0, "path/to", 0, false), "some/directory");
    }
    
    @Test
    public void downloadShouldRecursivelyCheckFileIfFolderThenLsThatAndGetOnlyFiles() throws SftpException {
        
        FTPFile directory = new FTPFile("folder", 0, "path/to", 0, true);
        sftpConnection.download(directory, "some/directory");
        
        verify(mockChannel).ls("path/to/folder/");
        verify(mockFileUtils).createLocalDirectory("some/directory/folder/");
        verify(mockChannel).get("path/to/folder/File 2", "some/directory/folder/");
    }
    
    private Vector<LsEntry> createEntries() {

        Vector<LsEntry> vector = new Vector<LsEntry>();

        vector.add(createSingleEntry("File 1", 123l, 1394525265, true));
        vector.add(createSingleEntry("File 2", 456l, 1394652161, false));
        vector.add(createSingleEntry("File 3", 789l, 1391879364, true));

        return vector;
    }

    private LsEntry createSingleEntry(String fileName, long size, int mTime, boolean directory) {

        SftpATTRS attributes = mock(SftpATTRS.class);
        when(attributes.getSize()).thenReturn(size);
        when(attributes.getMTime()).thenReturn(mTime);

        LsEntry entry = mock(LsEntry.class);
        when(entry.getAttrs()).thenReturn(attributes);
        when(entry.getFilename()).thenReturn(fileName);
        when(entry.getAttrs().isDir()).thenReturn(directory);

        return entry;
    }
}
