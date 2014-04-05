package nhethast.org.springframework.integration.googledrive.session;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.services.drive.model.File;

public class GdFilenameUtils {
	
	private static final Pattern TITLE_AND_ID_NAME_PATTERN = Pattern.compile("^(.+)\\-(.+)$");

	public static String getFilenameWithTitleAndId(File file) {
		return file.getTitle() + "-" + file.getId();
	}

	public static TitleAndIdResult extractTitleAndId(String filenameWithTitleAndId) {
		Matcher matcher = TITLE_AND_ID_NAME_PATTERN.matcher(filenameWithTitleAndId);
		if (matcher.find()) {
			return new TitleAndIdResult(matcher.group(2), matcher.group(1), false);
		} else {
			return new TitleAndIdResult(null, null, false);
		}
	}
	
}
