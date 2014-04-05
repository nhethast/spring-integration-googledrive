package nhethast.org.springframework.integration.googledrive.session;

import org.springframework.integration.file.remote.session.SessionFactory;

import com.google.api.services.drive.model.File;

public interface GdSessionFactory extends SessionFactory<File> {

	public abstract boolean getUseTitleAndId();

}