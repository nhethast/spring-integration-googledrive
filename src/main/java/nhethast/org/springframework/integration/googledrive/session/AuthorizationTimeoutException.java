package nhethast.org.springframework.integration.googledrive.session;

public class AuthorizationTimeoutException extends Exception {

	public AuthorizationTimeoutException() {
		super();
	}

	public AuthorizationTimeoutException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public AuthorizationTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

	public AuthorizationTimeoutException(String message) {
		super(message);
	}

	public AuthorizationTimeoutException(Throwable cause) {
		super(cause);
	}

}
