package nhethast.org.springframework.integration.googledrive.session;

public class VerificationResult {

	private String code;
	private String error;
	
	public VerificationResult(String code, String error) {
		super();
		if (code == null && error == null) {
			throw new IllegalArgumentException("error and code cannot both be null");
		}
		this.code = code;
		this.error = error;
	}

	public String getCode() {
		return code;
	}
	
	public String getError() {
		return error;
	}
	
}
