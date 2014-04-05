package nhethast.org.springframework.integration.googledrive.session;

public class TitleAndIdResult {

	private String id;
	private String title;
	private boolean matched;
	
	public TitleAndIdResult(String id, String title, boolean matched) {
		super();
		this.id = id;
		this.title = title;
		this.matched = matched;
	}

	public String getId() {
		return id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public boolean isMatched() {
		return matched;
	}
	
}
