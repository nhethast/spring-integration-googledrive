package nhethast.org.springframework.integration.googledrive.inbound;

import java.util.Comparator;

import org.springframework.integration.file.remote.synchronizer.AbstractInboundFileSynchronizer;
import org.springframework.integration.file.remote.synchronizer.AbstractInboundFileSynchronizingMessageSource;

import com.google.api.services.drive.model.File;

public class GdInboundFileSynchronizingMessageSource extends AbstractInboundFileSynchronizingMessageSource<File> {

	public GdInboundFileSynchronizingMessageSource(
			AbstractInboundFileSynchronizer<File> synchronizer) {
		super(synchronizer);
	}
	
	public GdInboundFileSynchronizingMessageSource(
			AbstractInboundFileSynchronizer<File> synchronizer,
			Comparator<java.io.File> comparator) {
		super(synchronizer, comparator);
	}

	public String getComponentType() {
		return "gd:inbound-channel-adapter";
	}
}
