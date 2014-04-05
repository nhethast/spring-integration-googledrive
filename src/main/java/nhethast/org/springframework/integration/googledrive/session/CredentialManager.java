package nhethast.org.springframework.integration.googledrive.session;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.Assert;

import nhethast.org.springframework.integration.googledrive.GdHeaders;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;

/**
 * The component responsible for getting a valid credential.
 */
public class CredentialManager {

	private String host;
	private int port;
	private String path;
	private MessageChannel authUrlChannel;
	private long authFlowTimeout;
	private Resource clientSecretsJson;
	private Resource dataStoreDirectory;
	
	private AuthorizationCodeFlow flow;
	private VerificationCodeReceiver receiver;
	private final Map<String, Lock> locks = Collections.synchronizedMap(new HashMap<String, Lock>());
	private final Map<String, Long> authorizationUrlSendTimes = Collections.synchronizedMap(new HashMap<String, Long>());
	
	public CredentialManager() {}
	
	/**
	 * <p>The resource containing the client secrets json.</p>
	 * 
	 * <p>To obtain this:</p>
	 * <ul>
	 * <li>login into google as the owner of the project</li>
	 * <li>go to https://code.google.com/apis/console, creating a project is necessary</li>
	 * <li>Select APIs & auth</li>
	 * <li>Select credentials</li>
	 * <li>Click on Download JSON of the Client ID for native application.  If none exist then click on Create New Client Id, select Installed application as application type and other as installed application type</li>
	 * <li>use it</li>
	 * </ul>
	 */
	public void setClientSecretsJson(Resource clientSecretsJson) {
		this.clientSecretsJson = clientSecretsJson;
	}

	public void setDataStoreDirectory(Resource dataStoreDirectory) {
		this.dataStoreDirectory = dataStoreDirectory;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setAuthUrlChannel(MessageChannel authUrlChannel) {
		this.authUrlChannel = authUrlChannel;
	}

	public void setAuthFlowTimeout(long authFlowTimeout) {
		this.authFlowTimeout = authFlowTimeout;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	@PostConstruct
	public void start() {
		JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
		HttpTransport httpTransport = null;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Failure to create trusted http transport", e);
		} catch (IOException e) {
			throw new RuntimeException("Failure to create trusted http transport", e);
		}
		try {
			// load client secrets
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jacksonFactory, new InputStreamReader(clientSecretsJson.getInputStream()));
			// set up authorization code flow
			// TODO do we have to set approval_prompt and access_type?
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jacksonFactory, clientSecrets, Arrays.asList(DriveScopes.DRIVE)).setDataStoreFactory(new FileDataStoreFactory(dataStoreDirectory.getFile())).build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		receiver = new VerificationCodeReceiver(host, port, path);
	}
	
	/**
	 * Returns the credential stored for the specified email address or triggers of a
	 * new authorization flow and waits for the specified timeout for the credential to be
	 * returned. 
	 * 
	 * @param email
	 * @param timeout
	 * 
	 * @throws AuthorizationTimeoutException if timeout occurs before the current authorization flow for the specified email can complete
	 */
	public Credential getCredential(String email, long timeout) throws AuthorizationTimeoutException {
		getLock(email).lock();
		try {
			Credential credential = flow.loadCredential(email);
			if (credential != null) {
				return credential;
			} else {
				Long sendTime = authorizationUrlSendTimes.get(email);
				// has a redirect url been sent?
				if (sendTime == null) {
					// if no then send one
					sendAuthorizationUrl(email);
				} else if (sendTime + authFlowTimeout < System.currentTimeMillis()) {
					// if yes, how long ago
					// if longer than auth flow timeout then send another one
					sendAuthorizationUrl(email);
				}
				// wait for the code or timeout if it took too long
				return waitForCode(email, timeout);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			getLock(email).unlock();
		}
	}
	
	private void sendAuthorizationUrl(String email) {
		String authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(receiver.getRedirectUri(email)).build();
		new MessagingTemplate().send(
				authUrlChannel,
				MessageBuilder.withPayload(authorizationUrl)
						.setHeader(GdHeaders.USER_ID, email).build());
		authorizationUrlSendTimes.put(email, System.currentTimeMillis());
	}
	
	private Credential waitForCode(String email, long timeout) throws AuthorizationTimeoutException, IOException {
		// receive authorization code and exchange it for an access token
		VerificationResult result = receiver.waitForCode(email, timeout);
		if (result == null) {
			throw new AuthorizationTimeoutException("Timeout after " + timeout + "ms waiting for verification code");
		}
		if (result.getError() != null) {
			throw new RuntimeException("Error " + result.getError());
		}
		TokenResponse response = flow.newTokenRequest(result.getCode()).setRedirectUri(receiver.getRedirectUri(email)).execute();
		// store credential and return it
		return flow.createAndStoreCredential(response, email);
	}

	/**
	 * <p>Discards the supplied invalid credential from the store if it exists before performing the same function as
	 * {@link #getCredential(String, long)}.</p>
	 * 
	 * <p>This exists as a separate method to enable the underlying implementation deal with thread synchronization issues.</p>
	 */
	public synchronized Credential discardAndGetCredential(Credential invalid, String email, long timeout) throws AuthorizationTimeoutException {
		Assert.notNull(invalid, "invalid cannot be null");
		getLock(email).lock();
		try {
			Credential credential = flow.loadCredential(email);
			if (credential != null && invalid.getRefreshToken().equals(credential.getRefreshToken())) {
				flow.getCredentialDataStore().delete(email);
				receiver.clearResult(email);
				authorizationUrlSendTimes.remove(email);
			}
			return getCredential(email, timeout);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			getLock(email).unlock();
		}
	}

	private Lock getLock(String email) {
		synchronized (locks) {
			Lock lock = locks.get(email);
			if (lock == null) {
				lock = new ReentrantLock();
				locks.put(email, lock);
			}
			return lock;
		}
	}
	
}
