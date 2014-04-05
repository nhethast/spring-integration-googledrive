package nhethast.org.springframework.integration.googledrive.session;

import java.io.IOException;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.Drive;

public interface DriveCallback<T> {
	
	T doWithDrive(Drive drive) throws GoogleJsonResponseException, IOException;
	
}
