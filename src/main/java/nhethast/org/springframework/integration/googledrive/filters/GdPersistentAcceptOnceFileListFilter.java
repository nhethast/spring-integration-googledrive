package nhethast.org.springframework.integration.googledrive.filters;

import nhethast.org.springframework.integration.googledrive.session.GdFilenameUtils;

import org.springframework.integration.file.filters.AbstractPersistentAcceptOnceFileListFilter;
import org.springframework.integration.metadata.MetadataStore;

import com.google.api.services.drive.model.File;

public class GdPersistentAcceptOnceFileListFilter extends AbstractPersistentAcceptOnceFileListFilter<File> {

	private boolean useTitleAndId;
	
	public GdPersistentAcceptOnceFileListFilter(MetadataStore store, String prefix, boolean useTitleAndId) {
		super(store, prefix);
	}

	@Override
	protected long modified(File file) {
		return file.getModifiedDate().getValue();
	}

	@Override
	protected String fileName(File file) {
		if (!useTitleAndId) {
			return GdFilenameUtils.getFilenameWithTitleAndId(file);
		} else {
			return file.getTitle();
		}
	}

}
