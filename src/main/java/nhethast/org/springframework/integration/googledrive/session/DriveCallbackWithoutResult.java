package nhethast.org.springframework.integration.googledrive.session;

import java.io.IOException;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.Drive;

public abstract class DriveCallbackWithoutResult implements DriveCallback<Object> {

	@Override
	public Object doWithDrive(Drive drive) throws GoogleJsonResponseException,
			IOException {
		doWithDriveWithoutResult(drive);
		return null;
	}

	public abstract void doWithDriveWithoutResult(Drive drive) throws GoogleJsonResponseException, IOException;

}
