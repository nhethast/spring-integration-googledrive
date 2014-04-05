package nhethast.org.springframework.integration.googledrive.session;

import com.google.api.client.util.Throwables;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * OAuth 2.0 verification code receiver that runs a Jetty server on a free port,
 * waiting for a redirect with the verification code.
 * 
 * <p>
 * Implementation is thread-safe.
 * </p>
 * 
 * <p>
 * Derived from
 * {@link com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver}
 * </p>
 */
public final class VerificationCodeReceiver {

	/** Server or {@code null} before {@link #getRedirectUri()}. */
	private Server server;

	/** Verification result or {@code null} for none. */
	private final Map<String, VerificationResult> results = Collections.synchronizedMap(new HashMap<String, VerificationResult>());

	/** Lock on the code and error. */
	private final Map<String, Lock> locks = Collections.synchronizedMap(new HashMap<String, Lock>());

	/** Condition for receiving an authorization response. */
	private final Map<String, Condition> gotAuthorizationResponseMap =  Collections.synchronizedMap(new HashMap<String, Condition>());

	/**
	 * Port to use or {@code -1} to select an unused port in
	 * {@link #getRedirectUri()}.
	 */
	private int port;

	/** Host name to use. */
	private final String host;

	private final String path;
	
	public VerificationCodeReceiver(String host, int port, String path) {
		Assert.isTrue(!path.startsWith("/"), "path cannot start with '/'");
		this.host = host;
		this.port = port;
		this.path = path;
		server = new Server(port);
		for (Connector c : server.getConnectors()) {
			c.setHost(host);
		}
		server.addHandler(new CallbackHandler());
		try {
			server.start();
		} catch (Exception e) {
			Throwables.propagateIfPossible(e);
			throw new RuntimeException(e);
		}
	}

	public String getRedirectUri(String email) {
		return "http://" + host + ":" + port + "/" + path + "/" + email;
	}

	public VerificationResult waitForCode(String email, long timeout)
			throws IOException {
		getLock(email).lock();
		try {
			if (results.containsKey(email)) {
				return results.get(email);
			} else {
				getGotAuthorizationResponse(email).await(timeout,
						TimeUnit.MILLISECONDS);
			}
			return results.get(email);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			getLock(email).unlock();
		}
	}

	public void stop() throws IOException {
		if (server != null) {
			try {
				server.stop();
			} catch (Exception e) {
				Throwables.propagateIfPossible(e);
				throw new IOException(e);
			}
			server = null;
		}
	}

	public void clearResult(String email) {
		getLock(email).lock();
		try {
			results.remove(email);
		} finally {
			getLock(email).unlock();
		}
	}

	/**
	 * Jetty handler that takes the verifier token passed over from the OAuth
	 * provider and stashes it where {@link #waitForCode} will find it.
	 */
	class CallbackHandler extends AbstractHandler {

		@Override
		public void handle(String target, HttpServletRequest request,
				HttpServletResponse response, int dispatch) throws IOException {
			// a security measure to hackers from 
			// loading up stores with invalid data or causing errors
			// to learn about underlying platform
			if (!target.startsWith("/" + path)) {
				return;
			}
			String email = target.replaceAll("^/" + path + "/", "");
			writeLandingHtml(response);
			response.flushBuffer();
			((Request) request).setHandled(true);
			getLock(email).lock();
			try {
				results.put(email,
						new VerificationResult(request.getParameter("code"),
								request.getParameter("error")));
				getGotAuthorizationResponse(email).signal();
			} finally {
				getLock(email).unlock();
			}
		}

		private void writeLandingHtml(HttpServletResponse response)
				throws IOException {
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType("text/html");

			PrintWriter doc = response.getWriter();
			doc.println("<html>");
			doc.println("<head><title>OAuth 2.0 Authentication Token Recieved</title></head>");
			doc.println("<body>");
			doc.println("Received verification code. Closing...");
			doc.println("<script type='text/javascript'>");
			// We open "" in the same window to trigger JS ownership of it,
			// which lets
			// us then close it via JS, at least in Chrome.
			doc.println("window.setTimeout(function() {");
			doc.println("    window.open('', '_self', ''); window.close(); }, 1000);");
			doc.println("if (window.opener) { window.opener.checkToken(); }");
			doc.println("</script>");
			doc.println("</body>");
			doc.println("</HTML>");
			doc.flush();
		}
	}

	private Lock getLock(String email) {
		synchronized (locks) {
			Lock lock = locks.get(email);
			if (lock == null) {
				lock = new ReentrantLock();
				locks.put(email, lock);
			}
			return lock;
		}
	}

	private Condition getGotAuthorizationResponse(String email) {
		synchronized (locks) {
			Condition gotAuthorizationResponse = gotAuthorizationResponseMap.get(email);
			if (gotAuthorizationResponse == null) {
				gotAuthorizationResponse = getLock(email).newCondition();
				gotAuthorizationResponseMap.put(email, gotAuthorizationResponse);
			}
			return gotAuthorizationResponse;
		}
	}

}
