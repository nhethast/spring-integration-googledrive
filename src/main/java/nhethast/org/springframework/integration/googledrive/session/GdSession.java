package nhethast.org.springframework.integration.googledrive.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.util.Assert;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

public class GdSession implements Session<File> {
	
	private static final Logger logger = LoggerFactory.getLogger(GdSession.class);
	
	private DriveTemplate t;
	private boolean useTitleAndId;
	
	public GdSession(CredentialManager credentialManager, String email, long authTimeout, boolean useTitleAndId) {
		super();
		t = new DriveTemplate(credentialManager, email, authTimeout);
		this.useTitleAndId = useTitleAndId;
	}

	public boolean remove(final String path) throws IOException {
		// TODO have option to delete vs just trash
		// TODO check for existance of folder path - perhaps print warning when it doesn't exist
		Assert.hasText(path, "path must not be null");
		try {
			return t.execute(new DriveCallback<Boolean>() {
				@Override
				public Boolean doWithDrive(Drive drive) throws GoogleJsonResponseException, IOException {
					File file = pathToFile(drive, path);
					if (file != null) {
						if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
							throw new IllegalArgumentException("Will not delete path '"+ path + "' as it is a folder");
						}
						drive.files().trash(file.getId()).execute();
						return true;
					} else {
						return false;
					}
				}
			});
		} catch (AuthorizationTimeoutException e) {
			logger.warn("Timeout waiting for authorization for remove of '" + path + "' - returning false");
			return false;
		}
	}

	public File[] list(final String path) throws IOException {
		Assert.notNull(path, "path must not be null");
		try {
			return t.execute(new DriveCallback<File[]>() {
				@Override
				public File[] doWithDrive(Drive drive) throws GoogleJsonResponseException, IOException {
					File file = pathToFile(drive, path);
					if (file != null) {
						List<File> files = new ArrayList<File>();
						ChildList cl = drive.children().list(file.getId()).execute();
						for (ChildReference cr : cl.getItems()) {
							File childFile = drive.files().get(cr.getId()).execute();
							// it is possible that when executed on a shared directory that any trashed
							// files still appear as children so we filter them out here
							if (childFile.getExplicitlyTrashed() == null || !childFile.getExplicitlyTrashed()) {
								files.add(childFile);
							}
						}
						return files.toArray(new File[0]);
					} else {
						return new File[0];
					}
				}
			});
		} catch (AuthorizationTimeoutException e) {
			logger.warn("Timeout waiting for authorization for list of '" + path + "' - returning empty list");
			return new File[0];
		}
	}

	public void read(final String source, final OutputStream outputStream)
			throws IOException {
		Assert.hasText(source, "source must not be null");
		try {
			t.execute(new DriveCallbackWithoutResult() {
				@Override
				public void doWithDriveWithoutResult(Drive drive) throws GoogleJsonResponseException, IOException {
				    InputStream inputStream = null;
				    try {
						if (source.endsWith("/")) {
							throw new IllegalArgumentException("Invalid source file path '" + source + "' as it is a folder path (has / at the end)");
						}
						File file = pathToFile(drive, source);
						if (file == null) {
							throw new IllegalArgumentException("Invalid source file path '" + source + "' as files does not exist");
						}
						String downloadUrl = file.getDownloadUrl();
						if (downloadUrl == null) {
							// google file types cannot be downloaded directly and instead
							// must be exported.  We just assume we always export as plain text
							// but other options could be added in future
							downloadUrl = file.getExportLinks().get("text/plain");
						}
						if (downloadUrl == null) {
							throw new RuntimeException("Source file path '" + source + "' cannot be downloaded or exported as plain text");
						}
						HttpResponse resp = drive.getRequestFactory().buildGetRequest(new GenericUrl(downloadUrl)).execute();
				    	inputStream = resp.getContent();
				    	IOUtils.copy(inputStream, outputStream);
				    } catch (IOException e) {
				    	throw new IOException("Error downloading '" + source + "' from google drive", e);
				    } finally {
				    	IOUtils.closeQuietly(inputStream);
				    	IOUtils.closeQuietly(outputStream);
				    }
				}
			});
		} catch (AuthorizationTimeoutException e) {
			throw new IOException("Timeout waiting for authorization for read of '" + source + "'");
		}
	}

	public void write(final InputStream inputStream, final String destination)
			throws IOException {
		Assert.hasText(destination, "destination must not be null");
		try {
			t.execute(new DriveCallbackWithoutResult() {
				@Override
				public void doWithDriveWithoutResult(Drive drive) throws GoogleJsonResponseException, IOException {
					try {
						// split destination into folder and filename
						String folder = null;
						String filename = null;
						if (destination.contains("/")) {
							if (destination.endsWith("/")) {
								throw new IllegalArgumentException("Invalid destination file path '" + destination + "' as it is a folder path (has / at the end)");
							}
							folder = destination.substring(0, destination.lastIndexOf("/"));
							filename = destination.substring(destination.lastIndexOf("/"));
						} else {
							// in google drive there is no home directory for a user
							// so relative paths are just the root
							folder = "/";
							filename = destination;
						} 
					    // File's metadata.
					    File body = new File();
					    body.setTitle(filename);
					    File parent = pathToFile(drive, folder);
					    if (parent == null) {
							throw new IllegalArgumentException("Invalid destination file path '" + destination + "' as parent folder '" + folder + "' does not exist");
					    }
					    body.setParents(Arrays.asList(new ParentReference().setId(parent.getId())));
					    
					    // assume we use "application/vnd.google-apps.file" from https://developers.google.com/drive/web/mime-types
					    InputStreamContent mediaContent = new InputStreamContent("application/vnd.google-apps.file", inputStream);
					    drive.files().insert(body, mediaContent).execute();
				    } catch (IOException e) {
				    	throw new IOException("Error uploading '" + destination + "' to google drive", e);
					} finally {
				    	IOUtils.closeQuietly(inputStream);
					}
				}
			});
		} catch (AuthorizationTimeoutException e) {
			throw new IOException("Timeout waiting for authorization for write of '" + destination + "'");
		}
	}

	public boolean mkdir(final String directory) throws IOException {
		// TODO do we need to check if the folder already exists?
		Assert.hasText(directory, "directory must not be null");
		try {
			return t.execute(new DriveCallback<Boolean>() {
				@Override
				public Boolean doWithDrive(Drive drive) throws GoogleJsonResponseException, IOException {
					try {
						// split destination into parent and folder
						String parent = null;
						String folder = null;
						if (directory.contains("/")) {
							parent = directory.substring(0, directory.lastIndexOf("/"));
							folder = directory.substring(directory.lastIndexOf("/"));
						} else {
							// in google drive there is no home directory for a user
							// so relative paths are just the root
							parent = "/";
							folder = directory;
						} 
					    
					    File parentFolder = pathToFile(drive, parent);
						// TODO validate assumption that we don't have to be smart and create parent directories if they don't exist
					    if (parentFolder == null) {
					    	logger.warn("mkdir failed as parent folder '" + parentFolder + "' did not exist");
					    	return false;
					    }
					    
					    // File's metadata.
					    File body = new File();
					    body.setTitle(folder);
					    body.setMimeType("application/vnd.google-apps.folder");
					    body.setParents(Arrays.asList(new ParentReference().setId(parentFolder.getId())));
					    
					    drive.files().insert(body).execute();
					    return true;
				    } catch (GoogleJsonResponseException e) {
				    	logger.warn("mkdir failed due to '" + e + "'", e);
				    	return false;
					}
				}
			});
		} catch (AuthorizationTimeoutException e) {
			logger.warn("Timeout waiting for authorization for mkdir of '" + directory + "' - returning false");
			return false;
		}
	}

	public void rename(final String pathFrom, final String pathTo) throws IOException {
		// TODO do we need to check if the destination already exists?read
		Assert.hasText(pathFrom, "pathFrom must not be null");
		Assert.hasText(pathTo, "pathFrom must not be null");
		try {
			t.execute(new DriveCallbackWithoutResult() {
				@Override
				public void doWithDriveWithoutResult(Drive drive) throws GoogleJsonResponseException, IOException {
					// get the file first
					if (pathFrom.endsWith("/")) {
						throw new IllegalArgumentException("Invalid pathFrom file path '" + pathTo + "' as it is a folder path (has / at the end)");
					}
					File file = pathToFile(drive, pathFrom);
					if (file == null) {
						throw new IllegalArgumentException("Cannot rename file '" + pathFrom + "' as it does not exist");
					}
					if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
						throw new IllegalArgumentException("Will not rename file '" + pathFrom + "' as it is a folder");
					}
					// split toPath into folder and filename
					String toFolder = null;
					String toFilename = null;
					if (pathTo.contains("/")) {
						if (pathTo.endsWith("/")) {
							throw new IllegalArgumentException("Invalid pathTo file path '" + pathTo + "' as it is a folder path (has / at the end)");
						}
						toFolder = pathTo.substring(0, pathTo.lastIndexOf("/"));
						toFilename = pathTo.substring(pathTo.lastIndexOf("/"));
					} else {
						// in google drive there is no home directory for a user
						// so relative paths are just the root
						toFolder = "/";
						toFilename = pathTo;
					}
					
					// get and check the to parent
				    File toParent = pathToFile(drive, toFolder);
				    if (toParent == null) {
						throw new IllegalArgumentException("Invalid pathTo file path '" + pathTo + "' as parent folder '" + toFolder + "' does not exist");
				    }

				    // File's metadata.
				    File body = new File();
				    body.setTitle(toFilename);
				    body.setParents(Arrays.asList(new ParentReference().setId(toParent.getId())));
				    
				    drive.files().update(file.getId(), body).execute();
				}
			});
		} catch (AuthorizationTimeoutException e) {
			throw new IOException("Timeout waiting for authorization for rename of '" + pathFrom + "' to '" + pathTo + "'");
		}
	}

	public void close() {
		// nothing to do
		// there is no connection left open or server session that times out
	}

	public boolean isOpen() {
		// always true
		// there is no connection left open or server session that times out
		return true;
	}

	public boolean exists(final String path) throws IOException {
		Assert.hasText(path, "path must not be null");
		try {
			return t.execute(new DriveCallback<Boolean>() {
				@Override
				public Boolean doWithDrive(Drive drive) throws GoogleJsonResponseException, IOException {
					File file = pathToFile(drive, path);
					return file != null;
				}
			});
		} catch (AuthorizationTimeoutException e) {
			throw new IOException("Timeout waiting for authorization for checking existance of '" + path + "'");
		}
	}

	public String[] listNames(String path) throws IOException {
		// we don't use 
		Assert.notNull(path, "path must not be null");
		List<String> names = new ArrayList<String>();
		for (File file : list(path)) {
			if (useTitleAndId) {
				names.add(file.getTitle() + "-" + file.getId());
			} else {
				names.add(file.getTitle());
			}
		}
		return names.toArray(new String[0]);
	}

	public InputStream readRaw(final String source) throws IOException {
		Assert.hasText(source, "source must not be null");
		try {
			return t.execute(new DriveCallback<InputStream>() {
				@Override
				public InputStream doWithDrive(Drive drive) throws GoogleJsonResponseException, IOException {
					if (source.endsWith("/")) {
						throw new IllegalArgumentException("Invalid source file path '" + source + "' as it is a folder path (has / at the end)");
					}
					File file = pathToFile(drive, source);
					if (file == null) {
						throw new IllegalArgumentException("Invalid source file path '" + source + "' as files does not exist");
					}
					HttpResponse resp = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl())).execute();
			    	return resp.getContent();
				}
			});
		} catch (AuthorizationTimeoutException e) {
			throw new IOException("Timeout waiting for authorization for readRaw of '" + source + "'");
		}
	}

	public boolean finalizeRaw() throws IOException {
		// nothing to do
		// there isn't any server command we have to wait for
		return true;
	}
	
	private File pathToFile(Drive drive, String path) {
		// create a path list
		List<String> pathList = new ArrayList<String>();
		String[] folders = path.split("/");
		for (String folder : folders) {
			// ignore any empty folders
			// this has the effect of ignoring any leading / and any multiple /
			// all paths are relative to the root so this is desired
			// ignoring multiple / does no harm and helps in some cases
			if (!folder.isEmpty()) {
				pathList.add(folder);
			}
		}
		List<File> files = new ArrayList<File>();
		try {
			files.addAll(pathToFileRelativeToRoot(drive, pathList));
		} catch (IOException e) {
			throw new RuntimeException("Exception occurred getting files relative to root for path '" + path + "'");
		}
		try {
			files.addAll(pathToFileSharedWithMe(drive, pathList));
		} catch (IOException e) {
			throw new RuntimeException("Exception occurred getting files sharedWithMe for path '" + path + "'");
		}
		if (files.isEmpty()) {
			return null;
		} else if (files.size() == 1) {
			return files.get(0);
		} else {
			List<String> fileIds = new ArrayList<String>();
			for (File file : files) {
				fileIds.add(file.getId());
			}
			throw new RuntimeException("Path " + path + " matches multiple.  Ids of matching files are " + Arrays.toString(fileIds.toArray()));
		}
	}
	
	private List<File> pathToFileRelativeToRoot(Drive drive, List<String> path) throws IOException {
		File root = drive.files().get("root").execute();
		// if path is empty then this will just return the root file
		// there is no home directory in google drive so this is acceptable behavior
		return pathToRelativeFile(drive, root, path);
	}
	
	private List<File> pathToFileSharedWithMe(Drive drive, List<String> path) throws IOException {
		if (path.isEmpty()) {
			// if path is empty then the path should just resolve to the root directory
			// so it should not resolve to shared with me folder (or throw an error)
			return Collections.emptyList();
		} else {
			List<File> files = new ArrayList<File>();
			// get the first file from the path
			List<String> nextPath = path.subList(1, path.size());
			String nextPart = path.get(0);
			// according to https://developers.google.com/drive/web/search-parameters
			// you escape \ and ' in string types with \\ and \'
			String escapedPart = nextPart.replaceAll("\\\\", "\\\\\\\\").replaceAll("\'", "\\\\'");
			FileList sharedWithMe = drive.files().list().setQ("sharedWithMe and title=\'" + escapedPart + "\' and not trashed").execute();
			if (sharedWithMe.getItems().isEmpty() && useTitleAndId) {
				TitleAndIdResult result = GdFilenameUtils.extractTitleAndId(nextPart);
				if (result.isMatched()) {
					String escapedTitle = result.getTitle().replaceAll("\\\\", "\\\\\\\\").replaceAll("\'", "\\\\'");
					String escapedId = result.getId().replaceAll("\\\\", "\\\\\\\\").replaceAll("\'", "\\\\'");
					sharedWithMe = drive.files().list().setQ("sharedWithMe and title=\'" + escapedTitle + "\' and id=\'" + escapedId + "\' and not trashed").execute();
				}
			}
			for (File file : sharedWithMe.getItems()) {
				files.addAll(pathToRelativeFile(drive, file, nextPath));
			}
			return files;
		}
	}
	
	private List<File> pathToRelativeFile(Drive drive, File file, List<String> path) throws IOException {
		if (path.isEmpty()) {
			// go no further, just return the file
			return Collections.singletonList(file);
		} else {
			List<File> files = new ArrayList<File>();
			List<String> nextPath = path.subList(1, path.size());
			String nextPart = path.get(0);
			ChildList cl = drive.children().list(file.getId()).execute();
			for (ChildReference cr : cl.getItems()) {
				File childFile = drive.files().get(cr.getId()).execute();
				// it is possible that when executed on a shared directory that any trashed
				// files still appear as children so we filter them out here
				if ((childFile.getExplicitlyTrashed() == null || !childFile.getExplicitlyTrashed())) {
					boolean inPath = false;
					if (childFile.getTitle().equals(nextPart)) {
						inPath = true;
					// if the file doesn't match using title then try the next strategy if it is enabled
					} else if (useTitleAndId) {
						inPath = nextPart.equals(GdFilenameUtils.getFilenameWithTitleAndId(childFile));
					}
					if (inPath) {
						files.addAll(pathToRelativeFile(drive, childFile, nextPath));
					}
				}
			}
			return files;
		}
	}
	
}
