package nhethast.org.springframework.integration.googledrive.session;

import com.google.api.services.drive.model.File;

public interface GdFilenameStrategy {
	
	public String getFilename(File file);

	public boolean matches(File file, String filename);
	
}
