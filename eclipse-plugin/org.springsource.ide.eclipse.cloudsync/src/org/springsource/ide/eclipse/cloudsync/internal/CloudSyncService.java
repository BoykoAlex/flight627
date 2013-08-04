package org.springsource.ide.eclipse.cloudsync.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class CloudSyncService {

	public ConnectedProject connect(IProject project) {
		ConnectedProject connectedProject = getProject(project);
		if (connectedProject != null) {
			triggerInitialSync(connectedProject);
		} else {
			connectedProject = createProject(project);
			triggerInitialUpload(connectedProject);
		}

		return connectedProject;
	}

	public void disconnect(IProject project) {
	}

	public void sendResourceUpdate(ConnectedProject project, IResourceDelta delta) {
		IResource resource = delta.getResource();

		if (resource != null && resource.isDerived(IResource.CHECK_ANCESTORS)) {
			return;
		}

		switch (delta.getKind()) {
		case IResourceDelta.ADDED:
			putResource(project, resource);
			break;
		case IResourceDelta.REMOVED:
			deleteResource(project, resource);
			break;
		case IResourceDelta.CHANGED:
			updateResource(project, resource);
			break;
		}
	}

	public void receivedResourceUpdate(ConnectedProject connectedProject, String resourcePath, int newVersion, String fingerprint) {
		IProject project = connectedProject.getProject();
		IResource resource = project.findMember(resourcePath);

		if (resource != null && resource instanceof IFile) {
			IFile file = (IFile) resource;

			String checksum = checksum(file);
			if (checksum != null && !checksum.equals(fingerprint)) {
				try {
					byte[] newResourceContent = getResource(project, resourcePath);
					file.setContents(new ByteArrayInputStream(newResourceContent), true, true, null);
					connectedProject.setVersion(resourcePath, newVersion);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private ConnectedProject getProject(IProject project) {
		try {
			URL url = new URL("http://localhost:3000/" + project.getName());
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod("GET");
			urlConn.setAllowUserInteraction(false); // no user interaction
			urlConn.setDoOutput(false);
			urlConn.setRequestProperty("accept", "application/json");

			int rspCode = urlConn.getResponseCode();
			if (rspCode == HttpURLConnection.HTTP_OK) {
				return ConnectedProject.readFromJSON(urlConn.getInputStream(), project);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private ConnectedProject createProject(IProject project) {
		try {
			URL url = new URL("http://localhost:3000/" + project.getName());
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod("PUT");
			urlConn.setAllowUserInteraction(false); // no user interaction
			urlConn.setDoOutput(false);
			urlConn.setRequestProperty("accept", "application/json");

			int rspCode = urlConn.getResponseCode();

			if (rspCode == HttpURLConnection.HTTP_OK) {
				return ConnectedProject.readFromJSON(urlConn.getInputStream(), project);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private void putResource(ConnectedProject project, IResource resource) {
		if (project == resource)
			return;

		try {
			URL url = new URL("http://localhost:3000/" + project.getName() + "/" + resource.getProjectRelativePath());
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod("PUT");
			urlConn.setAllowUserInteraction(false); // no user interaction
			urlConn.setDoOutput(true);

			if (resource instanceof IFile) {
				IFile file = (IFile) resource;

				OutputStream outputStream = urlConn.getOutputStream();
				pipe(file.getContents(), outputStream);
				outputStream.flush();
				outputStream.close();
			}

			int rspCode = urlConn.getResponseCode();
			if (rspCode != HttpURLConnection.HTTP_OK) {
				throw new Exception("error " + rspCode + " while putting resource: " + resource.getProjectRelativePath());
			}

			project.setVersion(resource.getProjectRelativePath().toString(), 0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void updateResource(ConnectedProject project, IResource resource) {
		if (project == resource)
			return;
		if (resource.getType() == IResource.FOLDER)
			return;
		if (resource.getType() == IResource.PROJECT)
			return;

		String resourcePath = resource.getProjectRelativePath().toString();

		try {
			URL url = new URL("http://localhost:3000/" + project.getName() + "/" + resourcePath);
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod("POST");
			urlConn.setAllowUserInteraction(false); // no user interaction
			urlConn.setDoOutput(true);

			if (resource instanceof IFile) {
				IFile file = (IFile) resource;

				OutputStream outputStream = urlConn.getOutputStream();
				pipe(file.getContents(), outputStream);
				outputStream.flush();
			}

			int rspCode = urlConn.getResponseCode();
			if (rspCode != HttpURLConnection.HTTP_OK) {
				throw new Exception("error " + rspCode + " while updating resource: " + resource.getProjectRelativePath());
			}

			JSONTokener tokener = new JSONTokener(new InputStreamReader(urlConn.getInputStream()));
			JSONObject returnJSONObject = new JSONObject(tokener);

			int newVersion = returnJSONObject.getInt("newversion");
			project.setVersion(resourcePath, newVersion);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private byte[] getResource(IProject project, String resourcePath) {
		try {
			URL url = new URL("http://localhost:3000/" + project.getName() + "/" + resourcePath);
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod("GET");
			urlConn.setAllowUserInteraction(false); // no user interaction
			urlConn.setDoOutput(false);
			urlConn.setDoInput(true);

			int rspCode = urlConn.getResponseCode();

			if (rspCode == HttpURLConnection.HTTP_OK) {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				pipe(urlConn.getInputStream(), bos);
				return bos.toByteArray();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private void deleteResource(ConnectedProject project, IResource resource) {
		if (project == resource)
			return;

		try {
			URL url = new URL("http://localhost:3000/" + project.getName() + "/" + resource.getProjectRelativePath());
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod("DELETE");
			urlConn.setAllowUserInteraction(false); // no user interaction
			urlConn.setDoOutput(false);
			urlConn.setDoInput(true);

			int rspCode = urlConn.getResponseCode();

			if (rspCode != HttpURLConnection.HTTP_OK) {
				throw new Exception("error " + rspCode + " while deleting resource: " + resource.getProjectRelativePath());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void pipe(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[1024];
		int bytesRead;
		while ((bytesRead = input.read(buffer)) != -1) {
			output.write(buffer, 0, bytesRead);
		}

		input.close();
	}

	private void triggerInitialUpload(final ConnectedProject connectedProject) {
		IProject project = connectedProject.getProject();

		try {
			project.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					System.out.println("upload resource: " + resource.getName());
					IProject project = resource.getProject();
					if (project != null) {
						putResource(connectedProject, resource);
					}
					return true;
				}
			}, IResource.DEPTH_INFINITE, IContainer.EXCLUDE_DERIVED);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void triggerInitialSync(ConnectedProject connectedProject) {
		// triggerInitialUpload(connectedProject);
	}

	public String checksum(IFile file) {
		try {
			InputStream fin = file.getContents(true);
			MessageDigest md5er = MessageDigest.getInstance("MD5");
			byte[] buffer = new byte[1024];
			int read;
			do {
				read = fin.read(buffer);
				if (read > 0)
					md5er.update(buffer, 0, read);
			} while (read != -1);
			fin.close();
			byte[] digest = md5er.digest();
			if (digest == null)
				return null;
			String strDigest = "";
			for (int i = 0; i < digest.length; i++) {
				strDigest += Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1);
			}
			return strDigest;
		} catch (Exception e) {
			return null;
		}
	}

}
