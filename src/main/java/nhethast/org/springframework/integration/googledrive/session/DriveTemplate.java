package nhethast.org.springframework.integration.googledrive.session;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;

public class DriveTemplate {

	private CredentialManager credManager;
	private String email;
	private long authTimeout;

	private JacksonFactory jacksonFactory;
	private NetHttpTransport httpTransport;

	public DriveTemplate(CredentialManager credManager, String email, long authTimeout) {
		this.credManager = credManager;
		this.authTimeout = authTimeout;
		this.email = email;
		
		jacksonFactory = JacksonFactory.getDefaultInstance();
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public <T> T execute(DriveCallback<T> action) throws IOException, AuthorizationTimeoutException {
		try {
			Credential credential = credManager.getCredential(email, authTimeout);
			try {
				Drive drive = new Drive.Builder(httpTransport, jacksonFactory, credential).build();
				return action.doWithDrive(drive);
			} catch (GoogleJsonResponseException e) {
				if (e.getDetails().getCode() == 401) {
					credential = credManager.discardAndGetCredential(credential, email, authTimeout);
					Drive drive = new Drive.Builder(httpTransport, jacksonFactory, credential).build();
					return action.doWithDrive(drive);
				} else {
					throw e;
				}
			}
		} catch (GoogleJsonResponseException e) {
			throw new RuntimeException(e);
		}
	}

}
