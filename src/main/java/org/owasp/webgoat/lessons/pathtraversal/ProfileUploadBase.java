package org.owasp.webgoat.lessons.pathtraversal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.owasp.webgoat.container.assignments.AssignmentEndpoint;
import org.owasp.webgoat.container.assignments.AttackResult;
import org.owasp.webgoat.container.session.WebSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@Getter
public class ProfileUploadBase extends AssignmentEndpoint {

  private String webGoatHomeDirectory;
  private WebSession webSession;


    protected AttackResult execute(MultipartFile file, String fullName) {
      if (file.isEmpty()) {
          return failed(this).feedback("path-traversal-profile-empty-file").build();
      }

      if (!StringUtils.hasLength(fullName)) {
          return failed(this).feedback("path-traversal-profile-empty-name").build();
      }

      if (fullName.isBlank() || fullName.contains("..") || fullName.contains("/") || 
          fullName.contains("\\") ||
          fullName.indexOf('\0') != -1) {
          return failed(this).feedback("path-traversal-profile-invalid-name").build();
      }

      File uploadDirectory = cleanupAndCreateDirectoryForUser();

    try {
      File uploadedFile = new File(uploadDirectory, fullName);
      File canonicalUploadedFile = uploadedFile.getCanonicalFile();
      File canonicalUploadDir = uploadDirectory.getCanonicalFile();

      if (!canonicalUploadedFile.getPath().startsWith(canonicalUploadDir.getPath())) {
        return failed(this).feedback("path-traversal-profile-invalid-location").build();
      }

      FileCopyUtils.copy(file.getBytes(), canonicalUploadedFile);

      if (attemptWasMade(uploadDirectory, canonicalUploadedFile)) {
        return solvedIt(canonicalUploadedFile);
      }
      return informationMessage(this)
          .feedback("path-traversal-profile-updated")
          .feedbackArgs(canonicalUploadedFile.getAbsoluteFile())
          .build();

    } catch (IOException e) {
      return failed(this).output(e.getMessage()).build();
    }
  }

  @SneakyThrows
  protected File cleanupAndCreateDirectoryForUser() {
    var uploadDirectory =
        new File(this.webGoatHomeDirectory, "/PathTraversal/" + webSession.getUserName());
    if (uploadDirectory.exists()) {
      FileSystemUtils.deleteRecursively(uploadDirectory);
    }
    Files.createDirectories(uploadDirectory.toPath());
    return uploadDirectory;
  }

  private boolean attemptWasMade(File expectedUploadDirectory, File uploadedFile)
      throws IOException {
    return !expectedUploadDirectory
        .getCanonicalPath()
        .equals(uploadedFile.getParentFile().getCanonicalPath());
  }

  private AttackResult solvedIt(File uploadedFile) throws IOException {
    if (uploadedFile.getCanonicalFile().getParentFile().getName().endsWith("PathTraversal")) {
      return success(this).build();
    }
    return failed(this)
        .attemptWasMade()
        .feedback("path-traversal-profile-attempt")
        .feedbackArgs(uploadedFile.getCanonicalPath())
        .build();
  }

  public ResponseEntity<?> getProfilePicture() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MediaType.IMAGE_JPEG_VALUE))
        .body(getProfilePictureAsBase64());
  }

  protected byte[] getProfilePictureAsBase64() {
    var profilePictureDirectory =
        new File(this.webGoatHomeDirectory, "/PathTraversal/" + webSession.getUserName());
    var profileDirectoryFiles = profilePictureDirectory.listFiles();

    if (profileDirectoryFiles != null && profileDirectoryFiles.length > 0) {
      return Arrays.stream(profileDirectoryFiles)
          .filter(file -> FilenameUtils.isExtension(file.getName(), List.of("jpg", "png")))
          .findFirst()
          .map(
              file -> {
                try (var inputStream = new FileInputStream(profileDirectoryFiles[0])) {
                  return Base64.getEncoder().encode(FileCopyUtils.copyToByteArray(inputStream));
                } catch (IOException e) {
                  return defaultImage();
                }
              })
          .orElse(defaultImage());
    } else {
      return defaultImage();
    }
  }

  @SneakyThrows
  protected byte[] defaultImage() {
    var inputStream = getClass().getResourceAsStream("/images/account.png");
    return Base64.getEncoder().encode(FileCopyUtils.copyToByteArray(inputStream));
  }
}
