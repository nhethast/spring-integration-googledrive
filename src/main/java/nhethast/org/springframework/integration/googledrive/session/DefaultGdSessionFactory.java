package nhethast.org.springframework.integration.googledrive.session;

import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SharedSessionCapable;

import com.google.api.services.drive.model.File;

public class DefaultGdSessionFactory implements SharedSessionCapable, GdSessionFactory {
	
	private CredentialManager credentialManager;
	private String email;
	private long authTimeout;
	private boolean useTitleAndId;
	
	private GdSession session;

	public void setCredentialManager(CredentialManager credentialManager) {
		this.credentialManager = credentialManager;
	}

	public void setUseTitleAndId(boolean useTitleAndId) {
		this.useTitleAndId = useTitleAndId;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setAuthTimeout(long authTimeout) {
		this.authTimeout = authTimeout;
	}

	public synchronized Session<File> getSession() {
		if (session == null) {
			session = new GdSession(credentialManager, email, authTimeout, useTitleAndId); 
		}
		return session;
	}

	@Override
	public boolean isSharedSession() {
		return true;
	}

	@Override
	public synchronized void resetSharedSession() {
		this.session = null;
	}

	@Override
	public boolean getUseTitleAndId() {
		return useTitleAndId;
	}

}
