package nhethast.org.springframework.integration.googledrive.session;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.DriveScopes;

public class CredentialManagerIT {

	@Test
	public void test() throws AuthorizationTimeoutException {
		Resource clientSecretsJson = new FileSystemResource("client_secrets.json");
		Resource dataStoreDirectory = new FileSystemResource(".");
		List<String> driveOAuth2Scopes = Arrays.asList(DriveScopes.DRIVE_READONLY);
		String host = "localhost";
		int port = 8080;
		MessageChannel authUrlChannel = new MessageChannel() {
			
			@Override
			public boolean send(Message<?> message, long timeout) {
				System.out.println(message.getPayload());
				return false;
			}
			
			@Override
			public boolean send(Message<?> message) {
				System.out.println(message.getPayload());
				return true;
			}
		};
		String email = "ticheryonschardogric@gmail.com";
		long authFlowTimeout = 5000;
		String path = "1234567890";
		CredentialManager manager = new CredentialManager(clientSecretsJson, dataStoreDirectory, driveOAuth2Scopes, host, port, authUrlChannel, email, authFlowTimeout, path);
		Credential c = manager.getCredential(email, 60000);
		try {
			manager.discardAndGetCredential(c, email, 2000);
		} catch (AuthorizationTimeoutException e) {
		}
		try {
			manager.discardAndGetCredential(c, email, 2000);
		} catch (AuthorizationTimeoutException e) {
		}
		try {
			manager.discardAndGetCredential(c, email, 2000);
		} catch (AuthorizationTimeoutException e) {
		}
		try {
			manager.discardAndGetCredential(c, email, 2000);
		} catch (AuthorizationTimeoutException e) {
		}
		try {
			manager.discardAndGetCredential(c, email, 2000);
		} catch (AuthorizationTimeoutException e) {
		}
	}
	
}
